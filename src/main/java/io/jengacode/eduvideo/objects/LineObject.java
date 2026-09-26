/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.objects;

import io.jengacode.eduvideo.animation.Property;
import java.awt.Shape;
import java.awt.geom.Line2D;

/**
 * Segment from origin to a signed local width,height endpoint.
 *
 * <p>Allows negative width and height because they represent the local endpoint rather than bounds.
 * ShapeObject supplies partial drawing, dashed outlines and glow.
 */
public class LineObject extends ShapeObject {
  /**
   * Creates a configured LineObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   */
  public LineObject(String id) {
    super(id, false);
  }

  /**
   * Reports whether width and height represent signed endpoints instead of nonnegative bounds.
   *
   * @return true when the documented condition holds; false otherwise
   */
  @Override
  protected boolean signedDimensions() {
    return true;
  }

  /**
   * Builds the local geometry at the requested scene time.
   *
   * @param t sample time in scene-local seconds
   * @return builds the local geometry at the requested scene time
   */
  @Override
  protected Shape shape(double t) {
    return new Line2D.Double(0, 0, value(Property.WIDTH, t), value(Property.HEIGHT, t));
  }
}
