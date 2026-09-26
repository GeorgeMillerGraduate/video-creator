/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

import java.util.function.DoubleUnaryOperator;

/**
 * Named easing curves; implement DoubleUnaryOperator for custom curves.
 *
 * <p>Normalizes incoming progress to the closed interval [0,1] before evaluating a curve. Elastic
 * easing can overshoot the output interval; callers clamp when required by the property.
 */
public enum Easing implements DoubleUnaryOperator {
  /** Constant-rate interpolation. */
  LINEAR(t -> t),
  /** Quadratic acceleration from rest. */
  EASE_IN(t -> t * t),
  /** Quadratic deceleration into the destination. */
  EASE_OUT(t -> 1 - (1 - t) * (1 - t)),
  /** Symmetric quadratic acceleration and deceleration. */
  EASE_IN_OUT(t -> t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2),
  /** Cubic interpolation with zero endpoint slopes. */
  SMOOTH_STEP(t -> t * t * (3 - 2 * t)),
  /** Piecewise quadratic bounce into the final value. */
  BOUNCE(
      t -> {
        double n = 7.5625, d = 2.75;
        if (t < 1 / d) return n * t * t;
        if (t < 2 / d) {
          t -= 1.5 / d;
          return n * t * t + .75;
        }
        if (t < 2.5 / d) {
          t -= 2.25 / d;
          return n * t * t + .9375;
        }
        t -= 2.625 / d;
        return n * t * t + .984375;
      }),
  /** Damped oscillation that may overshoot its destination. */
  ELASTIC(
      t ->
          t == 0 || t == 1
              ? t
              : Math.pow(2, -10 * t) * Math.sin((t * 10 - .75) * 2 * Math.PI / 3) + 1);

  private final DoubleUnaryOperator fn;

  /**
   * Creates a configured Easing instance.
   *
   * @param fn normalized-progress easing function
   */
  Easing(DoubleUnaryOperator fn) {
    this.fn = fn;
  }

  /**
   * Evaluates the easing curve after clamping input progress.
   *
   * @param t normalized interpolation progress
   * @return evaluates the easing curve after clamping input progress
   */
  public double applyAsDouble(double t) {
    return fn.applyAsDouble(Math.max(0, Math.min(1, t)));
  }

  /**
   * Resolves an exact supported XML easing name.
   *
   * @param s source notation to parse
   * @return the resolved or newly constructed value
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static Easing parse(String s) {
    return switch (s) {
      case "linear" -> LINEAR;
      case "easeIn" -> EASE_IN;
      case "easeOut" -> EASE_OUT;
      case "easeInOut" -> EASE_IN_OUT;
      case "smoothStep" -> SMOOTH_STEP;
      case "bounce" -> BOUNCE;
      case "elastic" -> ELASTIC;
      default -> throw new IllegalArgumentException("Unknown easing: " + s);
    };
  }
}
