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
import io.jengacode.eduvideo.util.ExpressionParser;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;
import java.util.Locale;

/**
 * Live calculus construction inside a coordinate plane. PARAM_A controls secant displacement or
 * Riemann rectangle count. Function evaluation and labels use the same mathematical samples. The
 * derivative is a central numerical difference, explicitly distinct from symbolic algebra.
 */
public final class CalculusObject extends SceneObject {
  private final ExpressionParser.Expression f;
  private final String mode;
  private final double point, from, to;

  /**
   * Creates a secant/tangent or midpoint-Riemann demonstration for a finite expression.
   *
   * @param id stable nonblank identity, unique in its owning namespace
   * @param expression safe arithmetic expression in x
   * @param mode supported construction or plotting mode
   * @param point fixed mathematical x coordinate of the first secant point
   * @param from source position or start of the requested mathematical/render interval
   * @param to destination position or end of the requested mathematical/render interval
   */
  public CalculusObject(
      String id, String expression, String mode, double point, double from, double to) {
    super(id);
    f = ExpressionParser.compile(expression);
    if (!mode.equals("secant") && !mode.equals("riemann"))
      throw new IllegalArgumentException("Expected secant or riemann");
    if (!Double.isFinite(point + from + to) || to <= from)
      throw new IllegalArgumentException("Invalid calculus interval");
    this.mode = mode;
    this.point = point;
    this.from = from;
    this.to = to;
    set(Property.PARAM_A, mode.equals("secant") ? 2 : 4);
  }

  /**
   * * Allows the explanation parameter to share ordinary property tracks.
   *
   * @param p normalised progress or validated component, according to this signature
   * @return true if the object accepts the requested property
   */
  @Override
  public boolean supports(Property p) {
    return p == Property.PARAM_A || super.supports(p);
  }

  /**
   * * Evaluates the authored function using default a=1, b=c=0.
   *
   * @param x horizontal coordinate in the units documented by this operation
   * @return Evaluates the authored function using default a=1, b=c=0
   */
  private double value(double x) {
    return f.evaluate(x, 1, 0, 0);
  }

  /**
   * * Returns the current secant slope, switching to a central difference at zero displacement.
   *
   * @param time sample time in scene-local seconds
   * @return sampled secant slope, or a central-difference tangent at zero displacement
   */
  public double slope(double time) {
    double h = value(Property.PARAM_A, time);
    return Math.abs(h) < 1e-6
        ? (value(point + 1e-5) - value(point - 1e-5)) / 2e-5
        : (value(point + h) - value(point)) / h;
  }

  /**
   * Draws a line in mathematical coordinates.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param p normalised progress or validated component, according to this signature
   * @param x horizontal coordinate in the units documented by this operation
   * @param y vertical coordinate in the units documented by this operation
   * @param xx mathematical horizontal endpoint
   * @param yy mathematical vertical endpoint
   * @param t scene-local sample time
   */
  private void line(
      Graphics2D g, CoordinatePlane p, double x, double y, double xx, double yy, double t) {
    g.draw(new Line2D.Double(p.screenX(x, t), p.screenY(y, t), p.screenX(xx, t), p.screenY(yy, t)));
  }

  /**
   * Paints moving sample points, construction lines and labels from a single clock sample.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param c scene-local clock, active theme and render settings
   */
  @Override
  protected void draw(Graphics2D g, RenderContext c) {
    if (!(parent() instanceof CoordinatePlane p))
      throw new IllegalArgumentException("Calculus needs axes parent");
    double t = c.time();
    g.clip(new Rectangle2D.Double(0, 0, p.value(Property.WIDTH, t), p.value(Property.HEIGHT, t)));
    g.setFont(new Font(c.theme().font(), Font.PLAIN, 23));
    g.setStroke(new BasicStroke(2.5f));
    if (mode.equals("riemann")) {
      int n = (int) Math.max(1, Math.min(1024, Math.round(value(Property.PARAM_A, t))));
      double dx = (to - from) / n, sum = 0;
      for (int i = 0; i < n; i++) {
        double x = from + i * dx, y = value(x + dx / 2);
        sum += y * dx;
        double sx = p.screenX(x, t),
            ex = p.screenX(x + dx, t),
            sy = p.screenY(y, t),
            zero = p.screenY(0, t);
        g.setColor(new Color(50, 230, 170, 80));
        g.fill(
            new Rectangle2D.Double(
                Math.min(sx, ex), Math.min(sy, zero), Math.abs(ex - sx), Math.abs(zero - sy)));
        g.setColor(new Color(76, 240, 179, 150));
        g.draw(
            new Rectangle2D.Double(
                Math.min(sx, ex), Math.min(sy, zero), Math.abs(ex - sx), Math.abs(zero - sy)));
      }
      g.setColor(c.theme().foreground());
      TextRenderer.draw(
          g, String.format(Locale.ROOT, "n = %d     midpoint sum = %.4f", n, sum), 25, 35, "left");
      return;
    }
    double h = value(Property.PARAM_A, t),
        y = value(point),
        q = point + h,
        qy = value(q),
        m = slope(t);
    g.setColor(c.theme().success());
    line(g, p, from, y + m * (from - point), to, y + m * (to - point), t);
    g.setColor(c.theme().primary());
    line(g, p, point, y, q, y, t);
    g.setColor(new Color(192, 157, 255));
    line(g, p, q, y, q, qy, t);
    for (double x : new double[] {point, q}) {
      double sx = p.screenX(x, t), sy = p.screenY(value(x), t);
      g.setColor(c.theme().background());
      g.fill(new Ellipse2D.Double(sx - 9, sy - 9, 18, 18));
      g.setColor(c.theme().primary());
      g.fill(new Ellipse2D.Double(sx - 6, sy - 6, 12, 12));
    }
    g.setColor(new Color(6, 22, 34, 235));
    g.fill(new RoundRectangle2D.Double(12, 8, 650, 44, 12, 12));
    g.setColor(c.theme().foreground());
    TextRenderer.draw(
        g,
        String.format(Locale.ROOT, "Δx = %.3f     Δy = %.3f     slope = %.3f", h, qy - y, m),
        25,
        35,
        "left");
  }
}
