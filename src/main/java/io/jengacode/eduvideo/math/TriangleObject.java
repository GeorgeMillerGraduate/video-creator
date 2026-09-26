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
 * Labelled triangle with geometrically computed angle arcs; independent side/angle emphasis uses
 * bit masks.
 *
 * <p>Copies three non-collinear vertices and computes interior angles from their vectors. Side
 * labels follow consecutive edges, while masks select static emphasis. highlight and angleHighlight
 * use 1-based selections (zero means none).
 */
public final class TriangleObject extends SceneObject {

  private final double[][] points;

  /**
   * * * Returns vertex:index or vertex:label coordinates for geometry transfers and construction
   * marks.
   *
   * @param name local semantic anchor name supported by this object
   * @param time sample time in scene-local seconds
   * @return semantic point in this object's local coordinates
   */
  @Override
  public Point2D anchor(String name, double time) {
    if (!name.startsWith("vertex:")) return super.anchor(name, time);
    String id = name.substring(7);
    int index = -1;
    for (int i = 0; i < vertices.length; i++) if (vertices[i].equals(id)) index = i;
    if (index < 0)
      try {
        index = Integer.parseInt(id);
      } catch (NumberFormatException ignored) {
        throw new IllegalArgumentException("Unknown vertex " + id);
      }
    if (index < 0 || index >= points.length)
      throw new IllegalArgumentException("Unknown vertex " + id);
    return new Point2D.Double(points[index][0], points[index][1]);
  }

  /**
   * * Computes the actual triangle's axis-aligned local bounds.
   *
   * @param time sample time in scene-local seconds
   * @return axis-aligned local bounds at the requested time
   */
  @Override
  public Rectangle2D bounds(double time) {
    var path = new Path2D.Double();
    path.moveTo(points[0][0], points[0][1]);
    for (int i = 1; i < 3; i++) path.lineTo(points[i][0], points[i][1]);
    return path.getBounds2D();
  }

  private final String[] vertices, sides;
  private int sideMask, angleMask;

  /**
   * Creates a configured TriangleObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param points finite coordinate pairs, defensively copied where retained
   * @param vertices three vertex labels in point order
   * @param sides three consecutive-edge labels, or edge-emphasis mask where numeric
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public TriangleObject(String id, double[][] points, String[] vertices, String[] sides) {
    super(id);
    if (points.length != 3 || vertices.length != 3 || sides.length != 3)
      throw new IllegalArgumentException("Triangle needs three points and labels");
    this.points = java.util.Arrays.stream(points).map(double[]::clone).toArray(double[][]::new);
    this.vertices = vertices.clone();
    this.sides = sides.clone();
    for (var p : points) {
      if (p.length != 2) throw new IllegalArgumentException("Point needs x,y");
      Checks.finite(p[0], "x");
      Checks.finite(p[1], "y");
    }
    double area =
        (points[1][0] - points[0][0]) * (points[2][1] - points[0][1])
            - (points[1][1] - points[0][1]) * (points[2][0] - points[0][0]);
    if (Math.abs(area) < 1e-8) throw new IllegalArgumentException("Triangle is degenerate");
  }

  /**
   * Selects independently emphasized edges and angles using three-bit masks.
   *
   * @param sides three consecutive-edge labels, or edge-emphasis mask where numeric
   * @param angles three-bit mask selecting emphasized angles
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public TriangleObject emphasis(int sides, int angles) {
    if (sides < 0 || sides > 7 || angles < 0 || angles > 7)
      throw new IllegalArgumentException("Emphasis masks must be 0..7");
    sideMask = sides;
    angleMask = angles;
    return this;
  }

  /**
   * Computes an interior angle in degrees from the two incident vertex vectors.
   *
   * @param vertex zero-based triangle vertex index (0\u20132)
   * @return computes an interior angle in degrees from the two incident vertex vectors
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public double angle(int vertex) {
    if (vertex < 0 || vertex > 2) throw new IllegalArgumentException("Vertex 0..2 required");
    double[] a = points[vertex], b = points[(vertex + 1) % 3], c = points[(vertex + 2) % 3];
    double ux = b[0] - a[0], uy = b[1] - a[1], vx = c[0] - a[0], vy = c[1] - a[1];
    return Math.toDegrees(
        Math.acos(
            Math.max(
                -1, Math.min(1, (ux * vx + uy * vy) / (Math.hypot(ux, uy) * Math.hypot(vx, vy))))));
  }

  /**
   * Reports whether this object accepts the requested animated property.
   *
   * @param p property to query or configure
   * @return true when the documented condition holds; false otherwise
   */
  public boolean supports(Property p) {
    return super.supports(p)
        || p == Property.HIGHLIGHT
        || p == Property.REVEAL
        || p == Property.ANGLE_HIGHLIGHT;
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    double t = c.time(),
        cx = (points[0][0] + points[1][0] + points[2][0]) / 3,
        cy = (points[0][1] + points[1][1] + points[2][1]) / 3;
    g.setColor(GlowRenderer.alpha(c.theme().primary(), .08));
    g.fill(GeometryObject.polygon(points, true));
    int activeSide = (int) Math.round(value(Property.HIGHLIGHT, t));
    for (int i = 0; i < 3; i++) {
      double[] a = points[i], b = points[(i + 1) % 3], other = points[(i + 2) % 3];
      Color accent =
          (sideMask & (1 << i)) != 0 || activeSide == i + 1
              ? c.theme().warning()
              : c.theme().primary();
      GlowRenderer.draw(
          g,
          PathRenderer.partial(
              new Line2D.Double(a[0], a[1], b[0], b[1]), value(Property.DRAW_PROGRESS, t)),
          accent,
          3,
          .65);
      double mx = (a[0] + b[0]) / 2,
          my = (a[1] + b[1]) / 2,
          n = Math.max(1, Math.hypot(mx - cx, my - cy));
      g.setFont(FontManager.get("Serif", Font.ITALIC, 26));
      g.setColor(accent);
      TextRenderer.draw(
          g,
          sides[i],
          (float) (mx + (mx - cx) / n * 32),
          (float) (my + (my - cy) / n * 32),
          "center");
      g.setColor(c.theme().foreground());
      g.fill(new Ellipse2D.Double(a[0] - 4, a[1] - 4, 8, 8));
      n = Math.max(1, Math.hypot(a[0] - cx, a[1] - cy));
      TextRenderer.draw(
          g,
          vertices[i],
          (float) (a[0] + (a[0] - cx) / n * 30),
          (float) (a[1] + (a[1] - cy) / n * 30 + 7),
          "center");
      if (value(Property.REVEAL, t) > 0) {
        double start = Math.toDegrees(Math.atan2(-(b[1] - a[1]), b[0] - a[0])),
            end = Math.toDegrees(Math.atan2(-(other[1] - a[1]), other[0] - a[0])),
            extent = ((end - start + 540) % 360) - 180;
        Color ac =
            ((angleMask & (1 << i)) != 0
                    || (int) Math.round(value(Property.ANGLE_HIGHLIGHT, t)) == i + 1)
                ? c.theme().success()
                : c.theme().secondary();
        GlowRenderer.draw(
            g,
            new Arc2D.Double(a[0] - 40, a[1] - 40, 80, 80, start, extent, Arc2D.OPEN),
            ac,
            2,
            .3);
        double mid = Math.toRadians(start + extent / 2);
        g.setColor(ac);
        g.setFont(FontManager.get("SansSerif", Font.PLAIN, 22));
        TextRenderer.draw(
            g,
            String.format(java.util.Locale.ROOT, "%.0f°", angle(i)),
            (float) (a[0] + 68 * Math.cos(mid)),
            (float) (a[1] - 68 * Math.sin(mid) + 7),
            "center");
      }
    }
  }
}
