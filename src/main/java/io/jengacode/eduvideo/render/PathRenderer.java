/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.render;

import java.awt.Shape;
import java.awt.geom.*;
import java.util.*;

/**
 * Arc-length reveal, preserving subpath boundaries and discontinuities.
 *
 * <p>Flattens curves with a local-space tolerance and accumulates segment lengths. Partial paths
 * keep separate moves at segment boundaries, preventing accidental links between discontinuous
 * subpaths.
 */
public final class PathRenderer {
  /** Prevents instantiation of this static utility. */
  private PathRenderer() {}

  /**
   * Returns the initial fraction of a flattened path measured by arc length.
   *
   * @param shape local geometry to render or copy
   * @param progress visible path fraction; values outside [0,1] are clamped by endpoint handling
   * @return visible prefix of the supplied shape
   */
  public static Shape partial(Shape shape, double progress) {
    if (progress >= 1) return shape;
    var out = new Path2D.Double();
    if (progress <= 0) return out;
    var segments = new ArrayList<double[]>();
    var it = shape.getPathIterator(null, .35);
    double[] c = new double[6];
    double x = 0, y = 0, sx = 0, sy = 0, total = 0;
    while (!it.isDone()) {
      int type = it.currentSegment(c);
      if (type == PathIterator.SEG_MOVETO) {
        x = sx = c[0];
        y = sy = c[1];
      } else {
        double nx = type == PathIterator.SEG_CLOSE ? sx : c[0],
            ny = type == PathIterator.SEG_CLOSE ? sy : c[1];
        double len = Math.hypot(nx - x, ny - y);
        segments.add(new double[] {x, y, nx, ny, len});
        total += len;
        x = nx;
        y = ny;
      }
      it.next();
    }
    double remaining = total * progress;
    for (var s : segments) {
      if (remaining <= 0) break;
      double fraction = s[4] == 0 ? 1 : Math.min(1, remaining / s[4]);
      out.moveTo(s[0], s[1]);
      out.lineTo(s[0] + (s[2] - s[0]) * fraction, s[1] + (s[3] - s[1]) * fraction);
      remaining -= s[4];
    }
    return out;
  }
}
