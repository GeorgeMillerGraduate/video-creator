/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.objects;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.*;
import io.jengacode.eduvideo.util.Checks;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;

/**
 * Quadratic callout arrow with a tangent-aligned head and deterministic draw progress.
 *
 * <p>Uses one quadratic control point and a signed endpoint. The arrowhead follows the tangent of
 * the flattened visible path, so progressive drawing remains aligned with the curve.
 */
public final class CurvedArrowObject extends LineObject {

  private final double cx, cy;

  private final boolean open;

  /**
   * Creates a configured CurvedArrowObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param cx quadratic control-point x offset
   * @param cy quadratic control-point y offset
   * @param open whether the curved arrowhead is outlined
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public CurvedArrowObject(String id, double cx, double cy, boolean open) {
    super(id);
    this.cx = Checks.finite(cx, "control x");
    this.cy = Checks.finite(cy, "control y");
    this.open = open;
  }

  /**
   * Builds the local geometry at the requested scene time.
   *
   * @param t sample time in scene-local seconds
   * @return builds the local geometry at the requested scene time
   */
  protected Shape shape(double t) {
    return new QuadCurve2D.Double(
        0, 0, cx, cy, value(Property.WIDTH, t), value(Property.HEIGHT, t));
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    super.draw(g, c);
    double p = Checks.clamp(value(Property.DRAW_PROGRESS, c.time()));
    if (p <= 0) return;
    // Match the flattened arc-length reveal, rather than using Bezier parameter as distance.
    var path = PathRenderer.partial(shape(c.time()), p).getPathIterator(null, .35);
    double[] v = new double[6];
    double x = 0, y = 0, px = 0, py = 0;
    while (!path.isDone()) {
      int type = path.currentSegment(v);
      if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
        px = x;
        py = y;
        x = v[0];
        y = v[1];
      }
      path.next();
    }
    double angle = Math.atan2(y - py, x - px);
    var head = new Path2D.Double();
    head.moveTo(x - 13 * Math.cos(angle - .5), y - 13 * Math.sin(angle - .5));
    head.lineTo(x, y);
    head.lineTo(x - 13 * Math.cos(angle + .5), y - 13 * Math.sin(angle + .5));
    if (open) g.draw(head);
    else {
      head.closePath();
      g.fill(head);
    }
  }
}
