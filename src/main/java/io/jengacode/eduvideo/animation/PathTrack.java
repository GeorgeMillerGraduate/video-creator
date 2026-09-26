/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

import io.jengacode.eduvideo.scene.SceneObject;
import java.util.Objects;

/**
 * One property sampled from a pure path. Three coordinated tracks provide position and optional
 * tangent orientation; scale, opacity and glow remain independent timeline properties.
 */
public final class PathTrack implements Animation {
  private final MotionPath path;
  private final Property property;
  private final double start, end;
  private final Easing easing;

  /**
   * Binds X, Y or ROTATION to a path over a finite positive interval.
   *
   * @param path pure path sampler or file path, as specified by this operation
   * @param property property driven by this track
   * @param start nonnegative interval start in scene-local seconds (global seconds for audio cues)
   * @param duration positive interval length in seconds
   * @param easing normalised interpolation curve
   */
  public PathTrack(
      MotionPath path, Property property, double start, double duration, Easing easing) {
    this.path = Objects.requireNonNull(path);
    this.property = property;
    this.start = start;
    this.end = start + duration;
    this.easing = Objects.requireNonNull(easing);
    if (!Double.isFinite(end)
        || start < 0
        || duration <= 0
        || !(property == Property.X || property == Property.Y || property == Property.ROTATION))
      throw new IllegalArgumentException("Invalid path track");
  }

  /**
   * Returns the controlled position or rotation property.
   *
   * @return the property controlled by this animation
   */
  @Override
  public Property property() {
    return property;
  }

  /**
   * Returns the scene-local start.
   *
   * @return start time in scene-local seconds
   */
  @Override
  public double start() {
    return start;
  }

  /**
   * Returns the scene-local end.
   *
   * @return exclusive end time in scene-local seconds
   */
  @Override
  public double end() {
    return end;
  }

  /**
   * * * Samples position or tangent orientation in degrees, holding path endpoints outside the
   * interval.
   *
   * @param time sample time in scene-local seconds
   * @return the requested property value at the clamped sample time
   */
  @Override
  public double valueAt(double time) {
    double u = easing.applyAsDouble((time - start) / (end - start));
    var p = path.point(u);
    return property == Property.X
        ? p.getX()
        : property == Property.Y ? p.getY() : Math.toDegrees(path.angle(u));
  }

  /**
   * Attaches coordinated position and optional rotation tracks, rejecting property conflicts.
   *
   * @param target object or zero-based row receiving the operation
   * @param path pure path sampler or file path, as specified by this operation
   * @param start nonnegative interval start in scene-local seconds (global seconds for audio cues)
   * @param duration positive interval length in seconds
   * @param orient whether to rotate the object to follow the path tangent
   */
  public static void follow(
      SceneObject target, MotionPath path, double start, double duration, boolean orient) {
    target.animate(new PathTrack(path, Property.X, start, duration, Easing.SMOOTH_STEP));
    target.animate(new PathTrack(path, Property.Y, start, duration, Easing.SMOOTH_STEP));
    if (orient)
      target.animate(new PathTrack(path, Property.ROTATION, start, duration, Easing.SMOOTH_STEP));
  }
}
