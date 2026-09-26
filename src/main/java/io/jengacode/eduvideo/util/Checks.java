/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.util;

/**
 * Shared finite-number and range validation.
 *
 * <p>Provides finite, positive and nonnegative checks with contextual error labels. Validation
 * methods return the checked value for assignment; clamp only bounds a value to [0,1].
 */
public final class Checks {
  /** Prevents instantiation of this static utility. */
  private Checks() {}

  /**
   * Rejects non-finite numbers and returns the checked value.
   *
   * @param n numeric value or union-find key, according to the overload
   * @param name semantic name or diagnostic label
   * @return rejects non-finite numbers and returns the checked value
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static double finite(double n, String name) {
    if (!Double.isFinite(n)) throw new IllegalArgumentException(name + " must be finite");
    return n;
  }

  /**
   * Rejects non-finite or nonpositive numbers and returns the checked value.
   *
   * @param n numeric value or union-find key, according to the overload
   * @param name semantic name or diagnostic label
   * @return rejects non-finite or nonpositive numbers and returns the checked value
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static double positive(double n, String name) {
    finite(n, name);
    if (n <= 0) throw new IllegalArgumentException(name + " must be positive");
    return n;
  }

  /**
   * Rejects non-finite or negative numbers and returns the checked value.
   *
   * @param n numeric value or union-find key, according to the overload
   * @param name semantic name or diagnostic label
   * @return rejects non-finite or negative numbers and returns the checked value
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static double nonnegative(double n, String name) {
    finite(n, name);
    if (n < 0) throw new IllegalArgumentException(name + " must be nonnegative");
    return n;
  }

  /**
   * Bounds a numeric value to the closed interval [0,1].
   *
   * @param x local pixel x coordinate (mathematical units for plot inputs)
   * @return bounds a numeric value to the closed interval [0,1]
   */
  public static double clamp(double x) {
    return Math.max(0, Math.min(1, x));
  }
}
