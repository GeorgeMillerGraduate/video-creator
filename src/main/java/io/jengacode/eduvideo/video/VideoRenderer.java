/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.video;

import io.jengacode.eduvideo.export.FrameExporter;
import io.jengacode.eduvideo.scene.Scene;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Deterministic frame renderer. No UI or real-time clock dependency.
 *
 * <p>Converts a global frame index to a scene-local clock and renders through Java2D. Incoming
 * transitions combine independently rendered frames; no previous-frame simulation state is
 * retained. A renderer owns bounded scene-backdrop caches and should be used by one rendering
 * thread.
 */
public final class VideoRenderer {

  private final VideoProject project;
  private final VideoSettings output;
  private final java.util.Map<Scene, BufferedImage> backgrounds =
      new java.util.LinkedHashMap<>(8, .75f, true) {
        /** Retains only nearby scene backdrops, bounding memory for long multi-scene lessons. */
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Scene, BufferedImage> entry) {
          return size() > 4;
        }
      };

  /**
   * Creates a configured VideoRenderer instance.
   *
   * @param project fully configured project to validate and render
   */
  public VideoRenderer(VideoProject project) {
    this(project, project.settings());
  }

  /**
   * Renders the existing logical canvas at a selected output resolution and frame rate.
   *
   * @param project validated lesson model, kept unchanged during rendering
   * @param output delivery dimensions and frame rate, independent of the logical canvas
   */
  public VideoRenderer(VideoProject project, VideoSettings output) {
    project.validate();
    this.project = project;
    this.output = output;
  }

  /**
   * Returns delivery settings, which can differ from the logical authoring canvas.
   *
   * @return delivery dimensions and frame rate
   */
  public VideoSettings outputSettings() {
    return output;
  }

  /**
   * Returns the number of frames at the selected delivery rate.
   *
   * @return number of frames covering the full project at the delivery rate
   */
  public int frameCount() {
    return output.frameCount(project.duration());
  }

  /**
   * Returns project for this configured component.
   *
   * @return project for this configured component
   */
  public VideoProject project() {
    return project;
  }

  /**
   * Renders one in-range frame independently of all previously requested frames.
   *
   * <p>Renders one in-range global frame independently of prior samples.
   *
   * @param frame zero-based global output frame index
   * @return newly rendered ARGB image
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public BufferedImage renderFrame(int frame) {
    if (frame < 0 || frame >= frameCount())
      throw new IllegalArgumentException(
          "Frame must be between 0 and " + (project.frameCount() - 1));
    return renderTime(output.timeAt(frame));
  }

  /** Renders an exact local time; multipart boundaries need not fall on the source frame grid. */
  public BufferedImage renderTime(double time) {
    if (!Double.isFinite(time) || time < 0 || time >= project.duration())
      throw new IllegalArgumentException("Time outside lesson");
    int frame = (int) Math.floor(time * output.fps());
    var scenes = project.scenes();
    int index = 0;
    while (index < scenes.size() - 1 && time >= scenes.get(index).duration()) {
      time -= scenes.get(index++).duration();
    }
    var scene = scenes.get(index);
    var next = renderScene(scene, time, frame);
    double d = scene.transitionDuration();
    if (index == 0 || d == 0 || time >= d) return next;
    double p = time / d;
    var previous = scenes.get(index - 1);
    var old =
        renderScene(
            previous, Math.max(0, previous.duration() - 1 / project.settings().fps()), frame);
    var out = canvas();
    var g = out.createGraphics();
    try {
      g.setColor(project.theme().background());
      g.fillRect(0, 0, out.getWidth(), out.getHeight());
      switch (scene.transition()) {
        case CUT -> g.drawImage(next, 0, 0, null);
        case CROSSFADE -> {
          g.drawImage(old, 0, 0, null);
          g.setComposite(AlphaComposite.SrcOver.derive((float) p));
          g.drawImage(next, 0, 0, null);
        }
        case FADE -> {
          g.setComposite(AlphaComposite.SrcOver.derive((float) (p < .5 ? 1 - 2 * p : 2 * p - 1)));
          g.drawImage(p < .5 ? old : next, 0, 0, null);
        }
        case SLIDE -> {
          g.drawImage(old, (int) (-p * out.getWidth()), 0, null);
          g.drawImage(next, (int) ((1 - p) * out.getWidth()), 0, null);
        }
      }
    } finally {
      g.dispose();
    }
    return out;
  }

  /**
   * Allocates an ARGB raster with the configured output dimensions.
   *
   * @return newly rendered ARGB image
   */
  private BufferedImage canvas() {
    return new BufferedImage(output.width(), output.height(), BufferedImage.TYPE_INT_ARGB);
  }

  /**
   * Renders a scene backdrop and camera-transformed object tree at a local time.
   *
   * @param scene owning scene with its object namespace
   * @param time sample or event time in scene-local seconds
   * @param frame zero-based global output frame index
   * @return newly rendered ARGB image
   */
  private BufferedImage renderScene(Scene scene, double time, int frame) {
    var image = canvas();
    var g = image.createGraphics();
    try {
      quality(g);
      g.setColor(
          scene.background() == null
              ? (scene.theme() == null ? project.theme() : scene.theme()).background()
              : scene.background());
      g.fillRect(0, 0, image.getWidth(), image.getHeight());
      Theme activeTheme = scene.theme() == null ? project.theme() : scene.theme();
      BufferedImage backdrop = backgrounds.get(scene);
      if (backdrop == null) {
        backdrop = canvas();
        var bg = backdrop.createGraphics();
        try {
          quality(bg);
          bg.setColor(scene.background() == null ? activeTheme.background() : scene.background());
          bg.fillRect(0, 0, backdrop.getWidth(), backdrop.getHeight());
          if (scene.backdrop() != null)
            scene.backdrop().draw(bg, backdrop.getWidth(), backdrop.getHeight(), activeTheme);
        } finally {
          bg.dispose();
        }
        backgrounds.put(scene, backdrop);
      }
      g.drawImage(backdrop, 0, 0, null);
      g.scale(
          output.width() / (double) project.settings().width(),
          output.height() / (double) project.settings().height());
      var transform = scene.camera().transform(time, project.settings());
      g.transform(transform);
      scene
          .root()
          .render(
              g, new RenderContext(frame, time, project.settings(), activeTheme, scene, transform));
    } finally {
      g.dispose();
    }
    return image;
  }

  /**
   * Applies consistent antialiasing, fractional metrics and high-quality image rendering hints.
   *
   * @param g graphics context positioned in local coordinates
   */
  public static void quality(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    g.setRenderingHint(
        RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
  }

  /**
   * Streams numbered PNG files into a directory without an existing frame sequence.
   *
   * <p>Streams the complete numbered PNG sequence to a protected output directory.
   *
   * @param directory destination directory for numbered PNG frames
   * @throws IOException if file access or encoding fails
   */
  public void renderAllFrames(Path directory) throws IOException {
    new FrameExporter().export(this, directory);
  }
}
