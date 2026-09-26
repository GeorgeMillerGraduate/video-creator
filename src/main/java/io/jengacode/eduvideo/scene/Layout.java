/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.scene;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.util.Checks;
import java.util.List;

/**
 * Optional configure-time layout helpers; explicit object positions remain the default.
 *
 * <p>Computes positions once during scene construction using configured width and height. It does
 * not continuously reflow animated objects; text baseline positioning remains explicit.
 */
public final class Layout {
  /** Prevents instantiation of this static utility. */
  private Layout() {}

  /**
   * Places objects consecutively using initial widths and a nonnegative gap.
   *
   * @param objects object parser or objects to position, according to the overload
   * @param x local pixel x coordinate (mathematical units for plot inputs)
   * @param y local pixel y coordinate (mathematical units for plot inputs)
   * @param gap nonnegative spacing between objects in pixels
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static void horizontal(
      List<? extends SceneObject> objects, double x, double y, double gap) {
    Checks.nonnegative(gap, "gap");
    for (var o : objects) {
      o.at(x, y);
      x += o.value(Property.WIDTH, 0) + gap;
    }
  }

  /**
   * Places objects consecutively using initial heights and a nonnegative gap.
   *
   * @param objects object parser or objects to position, according to the overload
   * @param x local pixel x coordinate (mathematical units for plot inputs)
   * @param y local pixel y coordinate (mathematical units for plot inputs)
   * @param gap nonnegative spacing between objects in pixels
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static void vertical(List<? extends SceneObject> objects, double x, double y, double gap) {
    Checks.nonnegative(gap, "gap");
    for (var o : objects) {
      o.at(x, y);
      y += o.value(Property.HEIGHT, 0) + gap;
    }
  }

  /**
   * Centers an object using its configured initial dimensions.
   *
   * @param object visual object to attach or position
   * @param width local pixel width
   * @param height local pixel height
   */
  public static void center(SceneObject object, double width, double height) {
    object.at(
        (width - object.value(Property.WIDTH, 0)) / 2,
        (height - object.value(Property.HEIGHT, 0)) / 2);
  }
}
