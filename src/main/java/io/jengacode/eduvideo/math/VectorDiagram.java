/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.TextRenderer;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;
import java.util.Locale;

/**
 * Animated vector addition, projection, complex multiplication and linear grid transformation.
 * Coordinates use mathematical units in a local centred viewport. PARAM_A in [0,1] controls
 * construction progress. Linear maps interpolate from identity; complex maps interpolate polar
 * rotation and modulus so multiplication visibly rotates and scales.
 */
public final class VectorDiagram extends SceneObject {
  private final String mode;
  private final double ax, ay, bx, by, m00, m01, m10, m11, unit;

  /**
   * Constructs a diagram with two vectors and an optional 2x2 target matrix.
   *
   * @param id stable nonblank identity, unique in its owning namespace
   * @param mode supported construction or plotting mode
   * @param ax first vector horizontal component
   * @param ay first vector vertical component
   * @param bx second vector horizontal component
   * @param by second vector vertical component
   * @param m00 target matrix entry at row 0, column 0
   * @param m01 target matrix entry at row 0, column 1
   * @param m10 target matrix entry at row 1, column 0
   * @param m11 target matrix entry at row 1, column 1
   * @param unit positive pixels per mathematical unit
   */
  public VectorDiagram(
      String id,
      String mode,
      double ax,
      double ay,
      double bx,
      double by,
      double m00,
      double m01,
      double m10,
      double m11,
      double unit) {
    super(id);
    if (!java.util.Set.of("addition", "projection", "linear", "complex").contains(mode)
        || unit <= 0
        || !Double.isFinite(ax + ay + bx + by + m00 + m01 + m10 + m11 + unit)
        || (mode.equals("projection") && bx * bx + by * by < 1e-12))
      throw new IllegalArgumentException("Invalid vector diagram");
    this.mode = mode;
    this.ax = ax;
    this.ay = ay;
    this.bx = bx;
    this.by = by;
    this.m00 = m00;
    this.m01 = m01;
    this.m10 = m10;
    this.m11 = m11;
    this.unit = unit;
    set(Property.WIDTH, 900);
    set(Property.HEIGHT, 600);
  }

  /**
   * * Enables the single construction-progress parameter.
   *
   * @param p normalised progress or validated component, according to this signature
   * @return true if the object accepts the requested property
   */
  @Override
  public boolean supports(Property p) {
    return p == Property.PARAM_A || super.supports(p);
  }

  /**
   * * Interpolates a target linear map from identity.
   *
   * @param x horizontal coordinate in the units documented by this operation
   * @param y vertical coordinate in the units documented by this operation
   * @param progress normalised construction progress in [0,1]
   * @return the interpolated mathematical image of the input point
   */
  public Point2D mapped(double x, double y, double progress) {
    return new Point2D.Double(
        x + progress * ((m00 * x + m01 * y) - x), y + progress * ((m10 * x + m11 * y) - y));
  }

  /**
   * Draws a mathematical arrow, including a fixed-size endpoint head.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param x horizontal coordinate in the units documented by this operation
   * @param y vertical coordinate in the units documented by this operation
   * @param xx mathematical horizontal endpoint
   * @param yy mathematical vertical endpoint
   * @param color paint colour for the travelling value
   */
  private void arrow(Graphics2D g, double x, double y, double xx, double yy, Color color) {
    g.setColor(color);
    g.setStroke(new BasicStroke(3));
    g.draw(new Line2D.Double(x * unit, -y * unit, xx * unit, -yy * unit));
    double angle = Math.atan2(-(yy - y), xx - x);
    var head = new Path2D.Double();
    head.moveTo(xx * unit, -yy * unit);
    for (double a : new double[] {angle + 2.65, angle - 2.65})
      head.lineTo(xx * unit + 15 * Math.cos(a), -yy * unit + 15 * Math.sin(a));
    head.closePath();
    g.fill(head);
  }

  /**
   * Draws mathematical grid lines and the selected vector construction.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param c scene-local clock, active theme and render settings
   */
  @Override
  protected void draw(Graphics2D g, RenderContext c) {
    double w = value(Property.WIDTH, c.time()),
        h = value(Property.HEIGHT, c.time()),
        u = Math.max(0, Math.min(1, value(Property.PARAM_A, c.time())));
    g.clip(new Rectangle2D.Double(0, 0, w, h));
    g.translate(w / 2, h / 2);
    g.setStroke(new BasicStroke(1));
    g.setColor(new Color(80, 140, 180, 55));
    for (int i = -10; i <= 10; i++) {
      var a = mode.equals("linear") ? mapped(i, -10, u) : new Point2D.Double(i, -10);
      var b = mode.equals("linear") ? mapped(i, 10, u) : new Point2D.Double(i, 10);
      g.draw(
          new Line2D.Double(a.getX() * unit, -a.getY() * unit, b.getX() * unit, -b.getY() * unit));
      a = mode.equals("linear") ? mapped(-10, i, u) : new Point2D.Double(-10, i);
      b = mode.equals("linear") ? mapped(10, i, u) : new Point2D.Double(10, i);
      g.draw(
          new Line2D.Double(a.getX() * unit, -a.getY() * unit, b.getX() * unit, -b.getY() * unit));
    }
    String caption = "";
    switch (mode) {
      case "addition" -> {
        arrow(g, 0, 0, ax, ay, c.theme().primary());
        arrow(g, ax * u, ay * u, ax * u + bx, ay * u + by, new Color(192, 157, 255));
        arrow(g, 0, 0, (ax + bx) * u, (ay + by) * u, c.theme().success());
        caption = "u + v = (" + (ax + bx) + ", " + (ay + by) + ")";
      }
      case "projection" -> {
        double dot = ax * bx + ay * by, factor = dot / (bx * bx + by * by);
        arrow(g, 0, 0, ax, ay, c.theme().primary());
        arrow(g, 0, 0, bx, by, new Color(192, 157, 255));
        arrow(g, 0, 0, bx * factor * u, by * factor * u, c.theme().success());
        g.setColor(c.theme().secondary());
        g.setStroke(new BasicStroke(1, 0, 0, 10, new float[] {5, 5}, 0));
        g.draw(
            new Line2D.Double(
                ax * unit,
                -ay * unit,
                (ax * (1 - u) + bx * factor * u) * unit,
                -(ay * (1 - u) + by * factor * u) * unit));
        caption =
            String.format(
                Locale.ROOT, "u · v = %.2f    |    projection factor = %.3f", dot, factor);
      }
      case "linear" -> {
        var a = mapped(1, 0, u);
        var b = mapped(0, 1, u);
        arrow(g, 0, 0, a.getX(), a.getY(), c.theme().primary());
        arrow(g, 0, 0, b.getX(), b.getY(), c.theme().success());
        caption =
            String.format(
                Locale.ROOT,
                "det A = %.2f    |    basis vectors define the map",
                m00 * m11 - m01 * m10);
      }
      case "complex" -> {
        double radius = Math.hypot(bx, by),
            angle = Math.atan2(by, bx) * u,
            scale = 1 + (radius - 1) * u;
        arrow(g, 0, 0, ax, ay, new Color(80, 140, 180));
        arrow(
            g,
            0,
            0,
            scale * (ax * Math.cos(angle) - ay * Math.sin(angle)),
            scale * (ax * Math.sin(angle) + ay * Math.cos(angle)),
            c.theme().success());
        g.setColor(c.theme().primary());
        g.draw(
            new Arc2D.Double(
                -unit, -unit, 2 * unit, 2 * unit, 0, Math.toDegrees(angle), Arc2D.OPEN));
        caption =
            String.format(
                Locale.ROOT,
                "multiply by %.2f + %.2fi: scale %.2f, rotate %.1f°",
                bx,
                by,
                radius,
                Math.toDegrees(Math.atan2(by, bx)));
      }
    }
    g.setFont(new Font(c.theme().font(), Font.PLAIN, 22));
    g.setColor(c.theme().foreground());
    TextRenderer.draw(g, caption, 0, (float) (h / 2 - 30), "center");
  }
}
