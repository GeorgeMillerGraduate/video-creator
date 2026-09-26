/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.video;

import io.jengacode.eduvideo.util.Checks;

/**
 * Output dimensions and fixed sampling rate.
 *
 * <p>Maps each nonnegative frame index to frame/fps seconds. The frame count rounds duration upward
 * with a small floating-point tolerance and rejects counts exceeding integer range.
 *
 * @param width local pixel width
 * @param height local pixel height
 * @param fps positive frames per second
 */
public record VideoSettings(int width, int height, double fps) {
  /**
   * Creates a configured VideoSettings instance.
   *
   * @param width local pixel width
   * @param height local pixel height
   * @param fps positive frames per second
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public VideoSettings {
    if (width < 1 || height < 1)
      throw new IllegalArgumentException("Canvas dimensions must be positive");
    Checks.positive(fps, "fps");
  }

  /** Creates a VideoSettings instance with its default configuration. */
  public VideoSettings() {
    this(1920, 1080, 60);
  }

  /**
   * Converts a nonnegative frame index to seconds at the configured frame rate.
   *
   * @param frame zero-based global output frame index
   * @return converts a nonnegative frame index to seconds at the configured frame rate
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public double timeAt(int frame) {
    if (frame < 0) throw new IllegalArgumentException("Negative frame");
    return frame / fps;
  }

  /**
   * Computes the number of frames required to cover the positive duration.
   *
   * @param duration positive interval length in seconds
   * @return computes the number of frames required to cover the positive duration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public int frameCount(double duration) {
    Checks.positive(duration, "duration");
    double count = Math.ceil(duration * fps - 1e-9);
    if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("Too many frames");
    return Math.max(1, (int) count);
  }
}
