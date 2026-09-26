/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.production;

import io.jengacode.eduvideo.audio.AudioTimeline;
import io.jengacode.eduvideo.video.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Shared single/multipart render input. A worker retains only the current chapter's
 * renderer/backdrops.
 */
public final class FrameSequence {
  private final VideoProject single;
  private final ProductionProject production;
  private final VideoSettings output;
  private VideoRenderer renderer;
  private int active = -1;

  /** Adapts an existing lesson without changing its timing or delivery policy. */
  public FrameSequence(VideoProject project, VideoSettings output) {
    single = project;
    production = null;
    this.output = output;
  }

  /** Adapts a validated production to one continuous frame clock. */
  public FrameSequence(ProductionProject project, VideoSettings output) {
    single = null;
    production = project;
    this.output = output;
  }

  /** Creates a separate worker-owned renderer/cache for another output raster. */
  public FrameSequence atOutput(VideoSettings value) {
    return single != null ? new FrameSequence(single, value) : new FrameSequence(production, value);
  }

  /** Returns source dimensions, independent of the chosen delivery profile. */
  public VideoSettings settings() {
    return single != null ? single.settings() : production.settings();
  }

  /** Returns exact source duration. */
  public double duration() {
    return single != null ? single.duration() : production.duration();
  }

  /** Returns the shared globally timed audio timeline. */
  public AudioTimeline audioTimeline() {
    return single != null ? single.audioTimeline() : production.audioTimeline();
  }

  /** Validates all immutable lesson snapshots. */
  public void validate() {
    lessons().forEach(VideoProject::validate);
    audioTimeline().validate(duration());
  }

  /** Returns lesson models for reporting without flattening their scene namespaces. */
  public List<VideoProject> lessons() {
    return single != null
        ? List.of(single)
        : production.parts().stream().map(ProductionProject.Part::project).toList();
  }

  /** Number of globally sampled output frames; chapters are not rounded individually. */
  public int frameCount() {
    return output.frameCount(duration());
  }

  /** Returns a chapter index for a global time. */
  public int partIndex(double time) {
    return production == null ? 0 : production.partIndex(time);
  }

  /** Emits chapter progress using actual sampled frame ranges, including partial renders. */
  public PartProgress progress(int frame, int first, int last) {
    int i = partIndex(frame / output.fps());
    double start = production == null ? 0 : production.parts().get(i).offset();
    double end =
        production == null ? duration() : start + production.parts().get(i).project().duration();
    int a = Math.max(first, (int) Math.ceil(start * output.fps() - 1e-9));
    int b = Math.min(last, (int) Math.ceil(end * output.fps() - 1e-9));
    return new PartProgress(
        i + 1,
        production == null ? 1 : production.parts().size(),
        production == null ? "Lesson" : production.parts().get(i).title(),
        Math.max(0, frame - a + 1),
        Math.max(0, b - a),
        frame / output.fps() - start,
        frame / output.fps());
  }

  /**
   * Renders one actual global sample through the unchanged graphics engine at chapter-local time.
   */
  public BufferedImage renderFrame(int frame) {
    if (frame < 0 || frame >= frameCount())
      throw new IllegalArgumentException("Frame outside production");
    double time = frame / output.fps();
    int part = partIndex(time);
    if (part != active) {
      renderer =
          new VideoRenderer(
              single != null ? single : production.parts().get(part).project(), output);
      active = part;
    }
    return renderer.renderTime(
        time - (production == null ? 0 : production.parts().get(part).offset()));
  }
}
