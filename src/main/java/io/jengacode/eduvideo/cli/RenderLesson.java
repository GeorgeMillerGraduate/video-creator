/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.cli;

import io.jengacode.eduvideo.export.FFmpegExporter;
import io.jengacode.eduvideo.video.VideoRenderer;
import io.jengacode.eduvideo.xml.ProjectParser;
import java.nio.file.*;

/**
 * Minimal XML-to-PNG-to-MP4 entry point with independent input, frames and output paths.
 *
 * <p>Loads one lesson, exports its numbered PNG sequence, then invokes FFmpeg. With no arguments it
 * uses the bundled matrix showcase; three arguments select XML, frame directory and MP4 paths.
 */
public final class RenderLesson {
  /** Prevents instantiation of this static utility. */
  private RenderLesson() {}

  /**
   * Renders XML to PNG frames and then encodes MP4 using optional explicit paths.
   *
   * @param args command-line arguments as documented by the entry point
   * @throws Exception if parsing, rendering or output processing fails
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 0 && args.length != 3)
      throw new IllegalArgumentException("RenderLesson [lesson.xml frames-directory output.mp4]");
    Path xml = Path.of(args.length == 0 ? "examples/matrix-multiplication.xml" : args[0]);
    Path frames = Path.of(args.length == 0 ? "output/matrix-frames" : args[1]);
    Path video = Path.of(args.length == 0 ? "output/matrix-multiplication.mp4" : args[2]);
    if (Files.exists(video)) throw new IllegalArgumentException("Output exists: " + video);
    var project = new ProjectParser().parse(xml);
    new VideoRenderer(project).renderAllFrames(frames);
    new FFmpegExporter().export(frames, video, project.settings(), project.frameCount());
  }
}
