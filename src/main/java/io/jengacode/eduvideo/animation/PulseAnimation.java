/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

import io.jengacode.eduvideo.util.Checks;

/**
 * Discrete highlight pulse; off outside its interval.
 *
 * <p>Uses a half-open [start,end) interval. It is suitable for temporary highlights because it
 * returns zero outside that interval instead of holding its final value.
 *
 * @param property property controlled by this track
 * @param start nonnegative start time in scene-local seconds
 * @param end exclusive end time in seconds
 */
public record PulseAnimation(Property property, double start, double end) implements Animation {
  /**
   * Creates a configured PulseAnimation instance.
   *
   * @param property property controlled by this track
   * @param start nonnegative start time in scene-local seconds
   * @param end finite exclusive end time, greater than start
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public PulseAnimation {
    Checks.nonnegative(start, "pulse start");
    if (!Double.isFinite(end) || end <= start)
      throw new IllegalArgumentException("Pulse duration must be positive");
  }

  /**
   * Returns one inside [start,end) and zero outside.
   *
   * @param t sample time in scene-local seconds
   * @return one inside [start,end) and zero outside
   */
  public double valueAt(double t) {
    return t >= start && t < end ? 1 : 0;
  }
}
