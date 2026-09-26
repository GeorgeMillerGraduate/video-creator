/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.objects;

import io.jengacode.eduvideo.animation.Property;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;

/**
 * Circle or ellipse centered on its local origin.
 *
 * <p>Treats width and height as diameters around the local origin. Equal values produce a circle;
 * independent dimensions produce an ellipse.
 */
public final class CircleObject extends ShapeObject {
  /**
   * * Returns centred local shape bounds, consistent with the drawing origin.
   *
   * @param time sample time in scene-local seconds
   * @return axis-aligned local bounds at the requested time
   */
  @Override
  public java.awt.geom.Rectangle2D bounds(double time) {
    double w = value(io.jengacode.eduvideo.animation.Property.WIDTH, time),
        h = value(io.jengacode.eduvideo.animation.Property.HEIGHT, time);
    return new java.awt.geom.Rectangle2D.Double(-w / 2, -h / 2, w, h);
  }

  /**
   * Creates a configured CircleObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   */
  public CircleObject(String id) {
    super(id, true);
  }

  /**
   * Builds the local geometry at the requested scene time.
   *
   * @param t sample time in scene-local seconds
   * @return builds the local geometry at the requested scene time
   */
  protected Shape shape(double t) {
    double w = Math.max(0, value(Property.WIDTH, t)), h = Math.max(0, value(Property.HEIGHT, t));
    return new Ellipse2D.Double(-w / 2, -h / 2, w, h);
  }
}
