/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.PathRenderer;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.ExpressionParser;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;

/**
 * Pixel-density sampling with midpoint checks and gaps at poles/non-finite regions.
 *
 * <p>Compiles a safe arithmetic expression once and samples approximately twice per output pixel.
 * Non-finite values, steep jumps and midpoint anomalies break the path rather than joining across
 * poles. Parameters a, b and c can be animated.
 */
public final class FunctionPlot extends SceneObject {

  private final ExpressionParser.Expression expression;

  /**
   * Creates a configured FunctionPlot instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param expression safe arithmetic expression using x and optional a/b/c
   */
  public FunctionPlot(String id, String expression) {
    super(id);
    this.expression = ExpressionParser.compile(expression);
  }

  /**
   * Reports whether this object accepts the requested animated property.
   *
   * @param p property to query or configure
   * @return true when the documented condition holds; false otherwise
   */
  public boolean supports(Property p) {
    return super.supports(p)
        || p == Property.PARAM_A
        || p == Property.PARAM_B
        || p == Property.PARAM_C;
  }

  /**
   * Samples the expression into disconnected path segments, breaking across invalid regions.
   *
   * @param plane plane used by this operation
   * @param t sample time in scene-local seconds
   * @return samples the expression into disconnected path segments, breaking across invalid regions
   */
  public Path2D path(CoordinatePlane plane, double t) {
    double w = plane.value(Property.WIDTH, t), h = plane.value(Property.HEIGHT, t);
    int count = Math.max(32, Math.min(20000, (int) Math.ceil(w * 2)));
    var path = new Path2D.Double();
    double a = value(Property.PARAM_A, t),
        b = value(Property.PARAM_B, t),
        c = value(Property.PARAM_C, t),
        lastX = 0,
        lastY = 0;
    boolean connected = false;
    for (int i = 0; i <= count; i++) {
      double x = plane.xmin() + (plane.xmax() - plane.xmin()) * i / count;
      double y = plane.screenY(expression.evaluate(x, a, b, c), t), sx = plane.screenX(x, t);
      boolean valid = Double.isFinite(y) && y >= -h && y <= 2 * h;
      if (valid && connected) {
        double mid = plane.screenY(expression.evaluate((lastX + x) / 2, a, b, c), t);
        if (!Double.isFinite(mid)
            || Math.abs(y - lastY) > h * .5
            || Math.abs(mid - (lastY + y) / 2) > Math.max(8, h * .05)) connected = false;
      }
      if (valid) {
        if (connected) path.lineTo(sx, y);
        else path.moveTo(sx, y);
      }
      connected = valid;
      lastX = x;
      lastY = y;
    }
    return path;
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  protected void draw(Graphics2D g, RenderContext c) {
    if (!(parent() instanceof CoordinatePlane plane))
      throw new IllegalArgumentException("FunctionPlot must be a direct child of CoordinatePlane");
    g.clip(
        new Rectangle2D.Double(
            0, 0, plane.value(Property.WIDTH, c.time()), plane.value(Property.HEIGHT, c.time())));
    io.jengacode.eduvideo.render.GlowRenderer.draw(
        g,
        PathRenderer.partial(path(plane, c.time()), value(Property.DRAW_PROGRESS, c.time())),
        g.getColor(),
        value(Property.STROKE_WIDTH, c.time()),
        value(Property.GLOW, c.time()));
  }
}
