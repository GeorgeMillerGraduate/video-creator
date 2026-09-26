/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.production;

import io.jengacode.eduvideo.audio.*;
import io.jengacode.eduvideo.video.*;
import java.nio.file.*;
import java.util.*;

/**
 * Validated ordered lesson snapshots. Source XML clocks and scene identities are never rewritten.
 */
public record ProductionProject(
    Path source,
    String title,
    VideoSettings settings,
    Path output,
    List<Part> parts,
    List<AudioTimeline.Track> music) {
  /** An independently renderable lesson and its cumulative start on the production clock. */
  public record Part(Path source, String title, VideoProject project, double offset) {
    /** Converts a local timestamp to production time without modifying the lesson. */
    public double global(double local) {
      return offset + local;
    }
  }

  /** Defensively copies the ordered collections and validates dimensions, offsets and audio. */
  public ProductionProject {
    source = source.toAbsolutePath().normalize();
    parts = List.copyOf(parts);
    music = List.copyOf(music);
    if (parts.isEmpty())
      throw new IllegalArgumentException("Production needs at least one XML part");
    double offset = 0;
    for (int i = 0; i < parts.size(); i++) {
      var p = parts.get(i);
      String label = "Part " + (i + 1) + ": " + p.source().getFileName() + ": ";
      p.project().validate();
      if (p.project().settings().width() != settings.width()
          || p.project().settings().height() != settings.height())
        throw new IllegalArgumentException(
            label
                + "resolution does not match manifest "
                + settings.width()
                + "×"
                + settings.height());
      if (Math.abs(p.project().settings().fps() - settings.fps()) > 1e-9)
        throw new IllegalArgumentException(label + "FPS does not match manifest " + settings.fps());
      if (Math.abs(p.offset() - offset) > 1e-8)
        throw new IllegalArgumentException(label + "invalid production offset");
      offset += p.project().duration();
    }
    settings.frameCount(offset);
    for (var track : music)
      if (track.end() > offset + 1e-8)
        throw new IllegalArgumentException("Global music exceeds production");
  }

  /** Total exact source duration, without per-part frame rounding. */
  public double duration() {
    return parts.stream().mapToDouble(p -> p.project().duration()).sum();
  }

  /**
   * Finds the chapter containing a production timestamp; a boundary belongs to the next chapter.
   */
  public int partIndex(double time) {
    if (!Double.isFinite(time) || time < 0 || time > duration())
      throw new IllegalArgumentException("Time outside production");
    for (int i = parts.size() - 1; i >= 0; i--) if (time >= parts.get(i).offset()) return i;
    return 0;
  }

  /** Builds globally timed narration/effects and continuous compatible music regions. */
  public AudioTimeline audioTimeline() {
    return ProductionAudio.assemble(this);
  }

  /** Creates an optional human-readable chapter list with millisecond precision. */
  public String chapters() {
    return chapters(0, duration());
  }

  /** Returns delivery-relative chapter starts for a requested source range. */
  public String chapters(double from, double to) {
    var result = new StringBuilder();
    for (var part : parts) {
      if (part.offset() >= to || part.offset() + part.project().duration() <= from) continue;
      long ms = Math.round(Math.max(0, part.offset() - from) * 1000);
      result.append(
          String.format(
              Locale.ROOT,
              "%02d:%02d:%02d.%03d %s%n",
              ms / 3600000,
              ms / 60000 % 60,
              ms / 1000 % 60,
              ms % 1000,
              part.title()));
    }
    return result.toString();
  }
}
