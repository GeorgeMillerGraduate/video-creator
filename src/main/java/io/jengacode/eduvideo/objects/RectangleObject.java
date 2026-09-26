/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.objects;

import io.jengacode.eduvideo.animation.Property;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

/**
 * Rectangle with top-left local origin.
 *
 * <p>Uses a top-left origin and nonnegative animated dimensions. Fill, progressive outlines, dashed
 * strokes and glow are inherited from ShapeObject.
 */
public final class RectangleObject extends ShapeObject {
  /**
   * Creates a configured RectangleObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   */
  public RectangleObject(String id) {
    super(id, true);
  }

  /**
   * Builds the local geometry at the requested scene time.
   *
   * @param t sample time in scene-local seconds
   * @return builds the local geometry at the requested scene time
   */
  protected Shape shape(double t) {
    return new Rectangle2D.Double(
        0, 0, Math.max(0, value(Property.WIDTH, t)), Math.max(0, value(Property.HEIGHT, t)));
  }
}
