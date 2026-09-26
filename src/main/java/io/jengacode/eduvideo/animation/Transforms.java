/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

import io.jengacode.eduvideo.scene.*;
import java.awt.geom.*;

/**
 * Reusable transform builders compiled into ordinary, conflict-checked property tracks. Matching
 * equations use TokenEquation; replacement transforms crossfade object identities.
 */
public final class Transforms {
  /** Prevents utility construction. */
  private Transforms() {}

  /**
   * * Moves an object's selected anchor onto a destination, converting scene space to parent space.
   *
   * @param object scene object whose geometry is sampled
   * @param anchor validated semantic anchor or local anchor name
   * @param destination destination object or semantic anchor
   * @param start nonnegative interval start in scene-local seconds (global seconds for audio cues)
   * @param duration positive interval length in seconds
   */
  public static void moveTo(
      SceneObject object, String anchor, Anchor destination, double start, double duration) {
    var from = object.worldAnchor(anchor, start);
    var to = destination.at(start);
    var parent = object.parent();
    var transform = parent == null ? new AffineTransform() : parent.worldTransform(start);
    try {
      var inverse = transform.createInverse();
      var a = inverse.transform(from, null);
      var b = inverse.transform(to, null);
      object.animate(
          Property.X,
          object.value(Property.X, start),
          object.value(Property.X, start) + b.getX() - a.getX(),
          start,
          duration,
          Easing.SMOOTH_STEP);
      object.animate(
          Property.Y,
          object.value(Property.Y, start),
          object.value(Property.Y, start) + b.getY() - a.getY(),
          start,
          duration,
          Easing.SMOOTH_STEP);
    } catch (NoninvertibleTransformException e) {
      throw new IllegalArgumentException("Cannot move inside a zero-scale parent", e);
    }
  }

  /**
   * * Aligns and crossfades source into destination. Source retirement and destination arrival
   * persist.
   *
   * @param source source object, matrix row or existing audio file, as described above
   * @param destination destination object or semantic anchor
   * @param start nonnegative interval start in scene-local seconds (global seconds for audio cues)
   * @param duration positive interval length in seconds
   */
  public static void replace(
      SceneObject source, SceneObject destination, double start, double duration) {
    if (source == destination)
      throw new IllegalArgumentException("Replacement needs different objects");
    moveTo(source, "center", new Anchor(destination, "center"), start, duration);
    source.animate(
        Property.OPACITY,
        source.value(Property.OPACITY, start),
        0,
        start,
        duration,
        Easing.SMOOTH_STEP);
    destination.animate(Property.OPACITY, 0, 1, start, duration, Easing.SMOOTH_STEP);
  }
}
