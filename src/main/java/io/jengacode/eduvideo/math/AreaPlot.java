/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.GlowRenderer;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;

/**
 * Shaded finite area to y=0 or midpoint Riemann rectangles; discontinuous sampled strips are
 * omitted.
 *
 * <p>Samples a fixed expression between ordered finite bounds. Zero rectangles requests a shaded
 * area; positive counts use midpoint rectangles. drawProgress reveals the interval left to right.
 * This is a visualization, not numerical integration.
 */
public final class AreaPlot extends SceneObject {

  private final ExpressionParser.Expression expression;

  private final double from, to;

  private final int rectangles;

  /**
   * Creates a configured AreaPlot instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param expression safe arithmetic expression using x and optional a/b/c
   * @param from mathematical integration bound
   * @param to mathematical integration bound
   * @param rectangles midpoint rectangle count, or zero for continuous area shading
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public AreaPlot(String id, String expression, double from, double to, int rectangles) {
    super(id);
    this.expression = ExpressionParser.compile(expression);
    this.from = Checks.finite(from, "area from");
    this.to = Checks.finite(to, "area to");
    if (from >= to || rectangles < 0 || rectangles > 2000)
      throw new IllegalArgumentException("Invalid area interval or rectangle count");
    this.rectangles = rectangles;
    z(-1);
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  protected void draw(Graphics2D g, RenderContext c) {
    if (!(parent() instanceof CoordinatePlane p))
      throw new IllegalArgumentException("Area requires axes parent");
    double t = c.time(),
        w = p.value(Property.WIDTH, t),
        h = p.value(Property.HEIGHT, t),
        end = from + (to - from) * Checks.clamp(value(Property.DRAW_PROGRESS, t));
    g.clip(new Rectangle2D.Double(0, 0, w, h));
    Color color = g.getColor();
    int n = rectangles > 0 ? rectangles : Math.min(20000, Math.max(32, (int) (w * 2)));
    double step = (to - from) / n;
    for (int i = 0; i < n; i++) {
      double a = from + i * step, b = Math.min(a + step, end);
      if (a >= end) break;
      double ya = expression.evaluate(rectangles > 0 ? (a + b) / 2 : a),
          yb = expression.evaluate(rectangles > 0 ? (a + b) / 2 : b);
      double sy1 = p.screenY(ya, t), sy2 = p.screenY(yb, t), base = p.screenY(0, t);
      // Never bridge a pole or an out-of-range discontinuity with a giant filled polygon.
      if (!Double.isFinite(sy1)
          || !Double.isFinite(sy2)
          || Math.abs(sy1 - sy2) > h * .5
          || Math.abs(sy1) > h * 4
          || Math.abs(sy2) > h * 4) continue;
      var shape = new Path2D.Double();
      shape.moveTo(p.screenX(a, t), base);
      shape.lineTo(p.screenX(a, t), sy1);
      shape.lineTo(p.screenX(b, t), sy2);
      shape.lineTo(p.screenX(b, t), base);
      shape.closePath();
      g.setPaint(
          new GradientPaint(
              0,
              0,
              GlowRenderer.alpha(color, .5),
              0,
              (float) Math.max(1, h),
              GlowRenderer.alpha(color, .08)));
      g.fill(shape);
      if (rectangles > 0) {
        g.setColor(GlowRenderer.alpha(color, .7));
        g.setStroke(new BasicStroke(1));
        g.draw(shape);
      }
    }
  }
}
