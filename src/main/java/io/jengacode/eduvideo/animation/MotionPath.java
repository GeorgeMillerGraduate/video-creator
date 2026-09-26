/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

import java.awt.geom.Point2D;
import java.util.function.DoubleUnaryOperator;

/** Pure normalised path sampler. Paths compose with property tracks without simulation state. */
@FunctionalInterface
public interface MotionPath {
  /**
   * * Samples a position; callers normally supply progress in [0,1].
   *
   * @param progress normalised construction progress in [0,1]
   * @return position on the normalised path
   */
  Point2D point(double progress);

  /**
   * * Returns a tangent angle in radians for optional orientation along the path.
   *
   * @param p normalised path progress in [0,1]
   * @return path tangent angle in radians
   */
  default double angle(double p) {
    var a = point(Math.max(0, p - .0001));
    var b = point(Math.min(1, p + .0001));
    return Math.atan2(b.getY() - a.getY(), b.getX() - a.getX());
  }

  /**
   * * Builds a straight path between immutable endpoint copies.
   *
   * @param a first path control point, copied on construction
   * @param b second path control point, copied on construction
   * @return pure straight-line sampler with copied endpoints
   */
  static MotionPath line(Point2D a, Point2D b) {
    return bezier(a, a, b, b);
  }

  /**
   * * Builds a cubic Bezier from four copied control points.
   *
   * @param a first path control point, copied on construction
   * @param b second path control point, copied on construction
   * @param c third path control point, copied on construction
   * @param d fourth path control point, copied on construction
   * @return pure cubic Bezier sampler with copied control points
   */
  static MotionPath bezier(Point2D a, Point2D b, Point2D c, Point2D d) {
    double[] x = {a.getX(), b.getX(), c.getX(), d.getX()},
        y = {a.getY(), b.getY(), c.getY(), d.getY()};
    return p -> {
      double q = 1 - p;
      return new Point2D.Double(
          q * q * q * x[0] + 3 * q * q * p * x[1] + 3 * q * p * p * x[2] + p * p * p * x[3],
          q * q * q * y[0] + 3 * q * q * p * y[1] + 3 * q * p * p * y[2] + p * p * p * y[3]);
    };
  }

  /**
   * * Builds a circular arc; angular arguments use radians.
   *
   * @param x horizontal coordinate in the units documented by this operation
   * @param y vertical coordinate in the units documented by this operation
   * @param radius nonnegative arc radius
   * @param start initial polar angle in radians
   * @param sweep signed angular sweep in radians
   * @return pure circular-arc sampler
   */
  static MotionPath arc(double x, double y, double radius, double start, double sweep) {
    if (radius < 0 || !Double.isFinite(x + y + radius + start + sweep))
      throw new IllegalArgumentException("Invalid arc");
    return p ->
        new Point2D.Double(
            x + radius * Math.cos(start + p * sweep), y + radius * Math.sin(start + p * sweep));
  }

  /**
   * * Adapts user-supplied pure parametric functions.
   *
   * @param x horizontal coordinate in the units documented by this operation
   * @param y vertical coordinate in the units documented by this operation
   * @return pure function-backed path sampler
   */
  static MotionPath parametric(DoubleUnaryOperator x, DoubleUnaryOperator y) {
    return p -> new Point2D.Double(x.applyAsDouble(p), y.applyAsDouble(p));
  }
}
