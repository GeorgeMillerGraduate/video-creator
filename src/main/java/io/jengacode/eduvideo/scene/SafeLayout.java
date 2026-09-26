/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.scene;

import io.jengacode.eduvideo.animation.Property;
import java.awt.geom.Rectangle2D;
import java.util.List;

/** Resolution-independent authoring layout with a 5% safe inset and explicit content regions. */
public final class SafeLayout {
  private final double width, height;

  /**
   * Selects logical canvas dimensions; output resolution is applied by the renderer.
   *
   * @param width positive logical canvas width
   * @param height positive logical canvas height
   */
  public SafeLayout(double width, double height) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid canvas");
    this.width = width;
    this.height = height;
  }

  /**
   * Returns the title-safe rectangle.
   *
   * @return title-safe layout rectangle
   */
  public Rectangle2D title() {
    return new Rectangle2D.Double(width * .05, height * .05, width * .9, height * .15);
  }

  /**
   * Returns the main mathematical working region.
   *
   * @return main working-region rectangle
   */
  public Rectangle2D work() {
    return new Rectangle2D.Double(width * .05, height * .24, width * .9, height * .52);
  }

  /**
   * Returns the lower conclusion region above the safe bottom margin.
   *
   * @return computed result or reserved conclusion rectangle, as described above
   */
  public Rectangle2D result() {
    return new Rectangle2D.Double(width * .05, height * .79, width * .9, height * .15);
  }

  /**
   * Places objects into evenly distributed cells; gaps are in logical pixels.
   *
   * @param objects ordered objects to arrange
   * @param region available logical-coordinate rectangle
   * @param columns positive number of grid columns
   * @param gap nonnegative separation between cells or audio intervals, in the units described
   *     above
   */
  public static void grid(
      List<? extends SceneObject> objects, Rectangle2D region, int columns, double gap) {
    if (columns < 1 || gap < 0) throw new IllegalArgumentException("Invalid grid");
    int rows = (objects.size() + columns - 1) / columns;
    if (rows == 0) return;
    double w = (region.getWidth() - (columns - 1) * gap) / columns,
        h = (region.getHeight() - (rows - 1) * gap) / rows;
    if (w <= 0 || h <= 0) throw new IllegalArgumentException("Grid does not fit");
    for (int i = 0; i < objects.size(); i++)
      objects
          .get(i)
          .at(region.getX() + (i % columns) * (w + gap), region.getY() + (i / columns) * (h + gap))
          .set(Property.WIDTH, w)
          .set(Property.HEIGHT, h);
  }
}
