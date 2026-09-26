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
 * Arc-length movement along a polyline; pair X and Y tracks on the same path.
 *
 * <p>Copies input points and precomputes cumulative segment lengths. Sampling maps eased progress
 * to distance rather than point count, so unevenly spaced vertices do not change speed
 * unexpectedly.
 */
public final class PathAnimation implements Animation {

  private final Property property;

  private final double[][] points;

  private final double[] distances;

  private final double start, end;

  private final DoubleUnaryOperator easing;

  /**
   * Creates a configured PathAnimation instance.
   *
   * @param property X or Y axis controlled by this path track
   * @param points at least two finite x,y pairs with nonzero total path length
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @param easing curve applied to normalized segment progress
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public PathAnimation(
      Property property,
      double[][] points,
      double start,
      double duration,
      DoubleUnaryOperator easing) {
    if (property != Property.X && property != Property.Y)
      throw new IllegalArgumentException("Path animation needs x or y");
    if (points.length < 2) throw new IllegalArgumentException("Path needs at least two points");
    this.property = property;
    this.points = java.util.Arrays.stream(points).map(double[]::clone).toArray(double[][]::new);
    this.start = Checks.nonnegative(start, "start");
    this.end = start + Checks.positive(duration, "duration");
    this.easing = java.util.Objects.requireNonNull(easing);
    distances = new double[points.length];
    for (int i = 0; i < points.length; i++) {
      if (points[i].length != 2) throw new IllegalArgumentException("Path point needs x,y");
      Checks.finite(points[i][0], "path x");
      Checks.finite(points[i][1], "path y");
      if (i > 0)
        distances[i] =
            distances[i - 1]
                + Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]);
    }
    if (distances[points.length - 1] == 0)
      throw new IllegalArgumentException("Path has zero length");
  }

  /**
   * Returns property for this configured component.
   *
   * @return property for this configured component
   */
  public Property property() {
    return property;
  }

  /**
   * Returns start for this configured component.
   *
   * @return time or duration in seconds
   */
  public double start() {
    return start;
  }

  /**
   * Returns end for this configured component.
   *
   * @return time or duration in seconds
   */
  public double end() {
    return end;
  }

  /**
   * Maps eased elapsed time to cumulative path distance and interpolates its segment.
   *
   * @param t sample time in scene-local seconds
   * @return maps eased elapsed time to cumulative path distance and interpolates its segment
   */
  public double valueAt(double t) {
    double d =
        Checks.clamp(easing.applyAsDouble(Checks.clamp((t - start) / (end - start))))
            * distances[distances.length - 1];
    int axis = property == Property.X ? 0 : 1;
    for (int i = 1; i < points.length; i++)
      if (d <= distances[i]) {
        double span = distances[i] - distances[i - 1],
            u = span == 0 ? 0 : (d - distances[i - 1]) / span;
        return points[i - 1][axis] + (points[i][axis] - points[i - 1][axis]) * u;
      }
    return points[points.length - 1][axis];
  }
}
