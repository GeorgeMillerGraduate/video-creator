/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.scene;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.video.*;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

/**
 * Camera x/y are pan offsets; zoom and rotation pivot at canvas center.
 *
 * <p>The renderer applies this transform to scene objects after drawing the screen-space
 * background. Pan is measured in scene pixels, rotation in degrees, and zoom must stay positive.
 */
public final class Camera extends SceneObject {
  /** Creates a Camera instance with its default configuration. */
  public Camera() {
    super("camera");
  }

  /**
   * Reports whether this object accepts the requested animated property.
   *
   * @param p property to query or configure
   * @return true when the documented condition holds; false otherwise
   */
  public boolean supports(Property p) {
    return p == Property.X || p == Property.Y || p == Property.ZOOM || p == Property.ROTATION;
  }

  /**
   * Configures transform for this component.
   *
   * @param t sample time in scene-local seconds
   * @param s output dimensions defining the transform pivot
   * @return configures transform for this component
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public AffineTransform transform(double t, VideoSettings s) {
    double zoom = value(Property.ZOOM, t);
    if (zoom <= 0) throw new IllegalArgumentException("Camera zoom must remain positive");
    var a = new AffineTransform();
    a.translate(s.width() / 2.0, s.height() / 2.0);
    a.scale(zoom, zoom);
    a.rotate(-Math.toRadians(value(Property.ROTATION, t)));
    a.translate(-s.width() / 2.0 - value(Property.X, t), -s.height() / 2.0 - value(Property.Y, t));
    return a;
  }

  /**
   * Performs no painting; a camera contributes only its transform to the scene.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {}
}
