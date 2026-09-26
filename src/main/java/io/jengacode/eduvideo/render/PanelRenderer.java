/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.render;

import java.awt.*;
import java.awt.geom.*;

/**
 * Shared card surface renderer. Paints shadow, gradient, border and optional restrained glow.
 *
 * <p>Centralizes rounded gradient fills, restrained offset shadows and outline glow. It uses child
 * graphics state and does not reposition scene children; PanelObject supplies content padding.
 */
public final class PanelRenderer {
  /** Prevents instantiation of this static utility. */
  private PanelRenderer() {}

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param graphics caller graphics context, preserved where a child context is created
   * @param x local pixel x coordinate (mathematical units for plot inputs)
   * @param y local pixel y coordinate (mathematical units for plot inputs)
   * @param w canvas or shape width in pixels
   * @param h canvas or shape height in pixels
   * @param radius nonnegative rounded-corner diameter in pixels
   * @param fill fill colour or enabled fill mode
   * @param bottom lower gradient colour
   * @param border outline colour
   * @param borderWidth nonnegative outline thickness in local pixels
   * @param glow glow intensity, clamped to [0,1] when drawn
   * @param shadow whether to render the restrained card shadow
   */
  public static void draw(
      Graphics2D graphics,
      double x,
      double y,
      double w,
      double h,
      double radius,
      Color fill,
      Color bottom,
      Color border,
      double borderWidth,
      double glow,
      boolean shadow) {
    if (w <= 0 || h <= 0) return;
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      if (shadow)
        for (int i = 4; i >= 1; i--) {
          g.setColor(new Color(0, 0, 0, 12));
          g.fill(
              new RoundRectangle2D.Double(x - i, y + 3 - i, w + 2 * i, h + 2 * i, radius, radius));
        }
      var shape = new RoundRectangle2D.Double(x, y, w, h, radius, radius);
      g.setPaint(new GradientPaint((float) x, (float) y, fill, (float) x, (float) (y + h), bottom));
      g.fill(shape);
      if (borderWidth > 0) GlowRenderer.draw(g, shape, border, borderWidth, glow);
    } finally {
      g.dispose();
    }
  }
}
