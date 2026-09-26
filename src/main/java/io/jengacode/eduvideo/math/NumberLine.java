/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.*;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;

/**
 * Horizontal labeled real-number axis.
 *
 * <p>Draws a horizontal axis with evenly spaced numerical ticks. The mathematical range is fixed at
 * construction while pixel width can be animated; excessive tick counts are rejected.
 */
public final class NumberLine extends SceneObject {

  private final double min, max, step;

  /**
   * Creates a configured NumberLine instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param min finite lower mathematical bound
   * @param max finite upper mathematical bound
   * @param step positive major tick spacing in mathematical units
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public NumberLine(String id, double min, double max, double step) {
    super(id);
    Checks.finite(min, "min");
    Checks.finite(max, "max");
    Checks.positive(step, "step");
    if (min >= max || (max - min) / step > 2000)
      throw new IllegalArgumentException("Invalid number line range");
    this.min = min;
    this.max = max;
    this.step = step;
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    double w = value(Property.WIDTH, c.time());
    g.draw(
        PathRenderer.partial(
            new Line2D.Double(0, 0, w, 0), value(Property.DRAW_PROGRESS, c.time())));
    g.setFont(FontManager.get(c.theme().font(), Font.PLAIN, 18));
    for (double n = Math.ceil(min / step) * step; n <= max; n += step) {
      double x = (n - min) / (max - min) * w;
      g.draw(new Line2D.Double(x, -5, x, 5));
      TextRenderer.draw(
          g,
          java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString(),
          (float) x,
          28,
          "center");
    }
  }
}
