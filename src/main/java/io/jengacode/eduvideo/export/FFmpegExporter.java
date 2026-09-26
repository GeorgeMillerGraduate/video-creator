/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.export;

import io.jengacode.eduvideo.video.VideoSettings;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Optional silent H.264 export. FFmpeg runs directly, without a shell.
 *
 * <p>Creates a ProcessBuilder argument list directly, preserving paths with spaces. It pads odd
 * dimensions, requests H.264/yuv420p and fast-start metadata, refuses output overwrites and retains
 * PNGs on failure.
 */
public final class FFmpegExporter {

  private final String executable;

  /** Creates a FFmpegExporter instance with its default configuration. */
  public FFmpegExporter() {
    this("ffmpeg");
  }

  /**
   * Creates a configured FFmpegExporter instance.
   *
   * @param executable FFmpeg executable name or path
   */
  public FFmpegExporter(String executable) {
    this.executable = Objects.requireNonNull(executable);
  }

  /**
   * Builds a shell-free FFmpeg argument list for silent H.264/yuv420p output.
   *
   * @param frames directory containing frame_000000.png and subsequent frames
   * @param output destination MP4 path; existing files are refused
   * @param settings validated video output dimensions and frame rate
   * @param count number of input frames to encode
   * @return builds a shell-free FFmpeg argument list for silent H.264/yuv420p output
   */
  public List<String> command(Path frames, Path output, VideoSettings settings, int count) {
    return List.of(
        executable,
        "-nostdin",
        "-xerror",
        "-n",
        "-framerate",
        Double.toString(settings.fps()),
        "-start_number",
        "0",
        "-i",
        frames.toAbsolutePath().resolve("frame_%06d.png").toString(),
        "-frames:v",
        Integer.toString(count),
        "-vf",
        "pad=ceil(iw/2)*2:ceil(ih/2)*2",
        "-c:v",
        "libx264",
        "-pix_fmt",
        "yuv420p",
        "-movflags",
        "+faststart",
        output.toAbsolutePath().toString());
  }

  /**
   * Runs FFmpeg and checks its exit status; PNG input is preserved on failure.
   *
   * @param frames directory containing frame_000000.png and subsequent frames
   * @param output destination MP4 path; existing files are refused
   * @param settings validated video output dimensions and frame rate
   * @param count number of input frames to encode
   * @throws IOException if file access or encoding fails
   * @throws InterruptedException if the encoder wait is interrupted
   */
  public void export(Path frames, Path output, VideoSettings settings, int count)
      throws IOException, InterruptedException {
    if (Files.exists(output))
      throw new IOException("Refusing to overwrite existing video: " + output);
    Files.createDirectories(output.toAbsolutePath().getParent());
    Process process;
    try {
      process = new ProcessBuilder(command(frames, output, settings, count)).inheritIO().start();
    } catch (IOException ex) {
      throw new IOException(
          "Could not start FFmpeg. Install it and add it to PATH; PNG frames are retained in "
              + frames,
          ex);
    }
    try {
      int code = process.waitFor();
      if (code != 0)
        throw new IOException(
            "FFmpeg exited with code " + code + "; PNG frames retained at " + frames);
      if (!Files.isRegularFile(output) || Files.size(output) == 0)
        throw new IOException("FFmpeg produced no usable output: " + output);
    } catch (InterruptedException ex) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw ex;
    }
  }
}
