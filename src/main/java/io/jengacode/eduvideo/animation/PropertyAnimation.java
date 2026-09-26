/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

import io.jengacode.eduvideo.util.ColorParser;
import java.util.*;

/**
 * Immutable keyframe track with endpoint holding.
 *
 * <p>Defensively copies keyframes and interpolates numeric values or individual ARGB channels.
 * Values before the first and after the final keyframe hold at their endpoints.
 */
public final class PropertyAnimation implements Animation {

  private final Property property;

  private final List<Keyframe> keys;

  /**
   * Creates a configured PropertyAnimation instance.
   *
   * @param property property controlled by this track
   * @param keys at least two strictly time-ordered keyframes
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public PropertyAnimation(Property property, List<Keyframe> keys) {
    this.property = Objects.requireNonNull(property);
    if (keys.size() < 2) throw new IllegalArgumentException("Need at least two keyframes");
    this.keys = List.copyOf(keys);
    for (int i = 1; i < keys.size(); i++)
      if (keys.get(i).time() <= keys.get(i - 1).time())
        throw new IllegalArgumentException("Keyframe times must strictly increase");
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
    return keys.get(0).time();
  }

  /**
   * Returns end for this configured component.
   *
   * @return time or duration in seconds
   */
  public double end() {
    return keys.get(keys.size() - 1).time();
  }

  /**
   * Interpolates the enclosing keyframe pair, holding endpoint values outside the track.
   *
   * @param t sample time in scene-local seconds
   * @return interpolates the enclosing keyframe pair, holding endpoint values outside the track
   */
  public double valueAt(double t) {
    if (t <= start()) return keys.get(0).value();
    for (int i = 1; i < keys.size(); i++) {
      var b = keys.get(i);
      var a = keys.get(i - 1);
      if (t <= b.time()) {
        double u = b.easing().applyAsDouble((t - a.time()) / (b.time() - a.time()));
        return property == Property.COLOR
            ? ColorParser.mix(a.value(), b.value(), u)
            : a.value() + (b.value() - a.value()) * u;
      }
    }
    return keys.get(keys.size() - 1).value();
  }
}
