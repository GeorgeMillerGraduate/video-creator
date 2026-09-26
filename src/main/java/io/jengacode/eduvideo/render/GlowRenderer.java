/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.render;

import io.jengacode.eduvideo.util.Checks;
import java.awt.*;

/**
 * Reusable multi-pass outline lighting in local coordinates, preserving inherited opacity.
 *
 * <p>Draws broad low-alpha strokes followed by a crisp core. It preserves caller graphics state and
 * inherited composite alpha; all distances are local coordinates and therefore follow camera scale.
 */
public final class GlowRenderer {
  /** Prevents instantiation of this static utility. */
  private GlowRenderer() {}

  /**
   * Returns a colour with its alpha multiplied by a clamped opacity factor.
   *
   * @param color display or highlight colour
   * @param opacity alpha multiplier in [0,1]
   * @return a colour with its alpha multiplied by a clamped opacity factor
   */
  public static Color alpha(Color color, double opacity) {
    return new Color(
        color.getRed(),
        color.getGreen(),
        color.getBlue(),
        (int) Math.round(color.getAlpha() * Checks.clamp(opacity)));
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param graphics caller graphics context, preserved where a child context is created
   * @param shape local geometry to render or copy
   * @param color display or highlight colour
   * @param width local pixel width
   * @param intensity effect strength, clamped to [0,1]
   */
  public static void draw(
      Graphics2D graphics, Shape shape, Color color, double width, double intensity) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      // Local-space strokes scale with the camera; alpha remains inherited from the object.
      double amount = Checks.clamp(intensity);
      for (int i = 4; i >= 1; i--) {
        g.setColor(alpha(color, amount * .055));
        g.setStroke(
            new BasicStroke(
                (float) Math.max(.01, width + i * 4),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.draw(shape);
      }
      g.setColor(color);
      g.setStroke(
          new BasicStroke(
              (float) Math.max(.01, width), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.draw(shape);
    } finally {
      g.dispose();
    }
  }
}
