/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

import io.jengacode.eduvideo.render.TextRenderer;
import io.jengacode.eduvideo.scene.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.Point2D;

/**
 * Transient labelled copy travelling between live anchors. Source objects remain intact. The actor
 * exists only during its interval and can be sampled out of chronological order.
 */
public final class ValueTransfer extends SceneObject {
  private final Anchor from, to;
  private final String label;
  private final double start, duration, bend, size;

  /**
   * Creates an automatically retired copy with a curved flight measured in scene pixels.
   *
   * @param id stable nonblank identity, unique in its owning namespace
   * @param label visible text copied during the transfer
   * @param from source position or start of the requested mathematical/render interval
   * @param to destination position or end of the requested mathematical/render interval
   * @param start nonnegative interval start in scene-local seconds (global seconds for audio cues)
   * @param duration positive interval length in seconds
   * @param bend signed vertical offset for the Bezier control points
   * @param size positive font size in logical pixels
   */
  public ValueTransfer(
      String id,
      String label,
      Anchor from,
      Anchor to,
      double start,
      double duration,
      double bend,
      double size) {
    super(id);
    this.label = java.util.Objects.requireNonNull(label);
    this.from = from;
    this.to = to;
    if (!Double.isFinite(start + duration + bend + size) || start < 0 || duration <= 0 || size <= 0)
      throw new IllegalArgumentException("Invalid transfer");
    this.start = start;
    this.duration = duration;
    this.bend = bend;
    this.size = size;
    z(1000);
  }

  /**
   * Returns the exclusive retirement time for preflight validation.
   *
   * @return exclusive end time in scene-local seconds
   */
  public double end() {
    return start + duration;
  }

  /**
   * * Samples a cubic trajectory between current source and destination locations.
   *
   * @param time sample time in scene-local seconds
   * @return scene-coordinate position of the travelling value
   */
  public Point2D position(double time) {
    var a = from.at(time);
    var b = to.at(time);
    double u = Easing.SMOOTH_STEP.applyAsDouble((time - start) / duration);
    return MotionPath.bezier(
            a,
            new Point2D.Double(a.getX(), a.getY() + bend),
            new Point2D.Double(b.getX(), b.getY() + bend),
            b)
        .point(u);
  }

  /**
   * Draws the flying label only while its lifecycle is active.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param c scene-local clock, active theme and render settings
   */
  @Override
  protected void draw(Graphics2D g, RenderContext c) {
    if (c.time() < start || c.time() >= end()) return;
    var p = position(c.time());
    g.setFont(new Font(c.theme().font(), Font.BOLD, (int) size));
    TextRenderer.draw(g, label, (float) p.getX(), (float) (p.getY() + size * .33), "center");
  }
}
