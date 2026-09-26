/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.objects.ShapeObject;
import java.awt.Shape;
import java.awt.geom.*;

/**
 * Arbitrary immutable copied Java2D geometry; polygons, arcs, angles and paths.
 *
 * <p>Copies the supplied shape as a Path2D. Filled and outlined geometry share ShapeObject
 * animation, glow and dashed-stroke behaviour, so camera transforms remain consistent.
 */
public final class GeometryObject extends ShapeObject {

  private final Path2D path;

  /**
   * Creates a configured GeometryObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param shape local geometry to render or copy
   * @param fill fill colour or enabled fill mode
   */
  public GeometryObject(String id, Shape shape, boolean fill) {
    super(id, fill);
    path = new Path2D.Double(shape);
  }

  /**
   * Builds the local geometry at the requested scene time.
   *
   * @param t sample time in scene-local seconds
   * @return builds the local geometry at the requested scene time
   */
  protected Shape shape(double t) {
    return path;
  }

  /**
   * Configures polygon for this component.
   *
   * @param points finite coordinate pairs, defensively copied where retained
   * @param close whether to close the polygon path
   * @return configures polygon for this component
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static Path2D polygon(double[][] points, boolean close) {
    if (points.length < 2) throw new IllegalArgumentException("At least two points required");
    var p = new Path2D.Double();
    p.moveTo(points[0][0], points[0][1]);
    for (int i = 1; i < points.length; i++) p.lineTo(points[i][0], points[i][1]);
    if (close) p.closePath();
    return p;
  }
}
