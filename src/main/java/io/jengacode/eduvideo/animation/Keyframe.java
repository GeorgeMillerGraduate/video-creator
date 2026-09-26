/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

import io.jengacode.eduvideo.util.Checks;
import java.util.function.DoubleUnaryOperator;

/**
 * Easing controls the segment arriving at this keyframe.
 *
 * <p>A destination keyframe defines the easing of the segment arriving at it. Times must be finite
 * and nonnegative; enclosing tracks require strictly increasing times.
 *
 * @param time sample or event time in scene-local seconds
 * @param value initial or assigned value
 * @param easing curve applied to normalized segment progress
 */
public record Keyframe(double time, double value, DoubleUnaryOperator easing) {
  /**
   * Creates a configured Keyframe instance.
   *
   * @param time sample or event time in scene-local seconds
   * @param value finite property value at this keyframe
   * @param easing curve applied to normalized segment progress
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public Keyframe {
    Checks.nonnegative(time, "keyframe time");
    Checks.finite(value, "keyframe value");
    java.util.Objects.requireNonNull(easing);
  }

  /**
   * Creates a configured Keyframe instance.
   *
   * @param time sample or event time in scene-local seconds
   * @param value finite property value at this keyframe
   */
  public Keyframe(double time, double value) {
    this(time, value, Easing.LINEAR);
  }
}
