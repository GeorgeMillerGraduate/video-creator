/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.render.*;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;

/**
 * Labelled mathematical point in a coordinate plane. Use for roots, intersections and extrema
 * supplied by a lesson.
 *
 * <p>Stores coordinates in mathematical units and resolves them through its axes parent at each
 * sample. Labels use pixel offsets, and optional dashed guides project onto x=0 and y=0. Root
 * finding is performed by the lesson author.
 */
public final class PlotPoint extends SceneObject {

  private final double mx, my;

  private final String label;

  private final boolean guides;

  private double dx = 0, dy = -22;

  /**
   * Creates a configured PlotPoint instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param x mathematical x-coordinate at construction; pixel label offset in labelOffset()
   * @param y mathematical y-coordinate at construction; pixel label offset in labelOffset()
   * @param label display label
   * @param guides whether to draw projections onto the axes
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public PlotPoint(String id, double x, double y, String label, boolean guides) {
    super(id);
    mx = Checks.finite(x, "point x");
    my = Checks.finite(y, "point y");
    this.label = java.util.Objects.requireNonNull(label);
    this.guides = guides;
  }

  /**
   * Positions the point label relative to its marker in pixels.
   *
   * @param x mathematical x-coordinate at construction; pixel label offset in labelOffset()
   * @param y mathematical y-coordinate at construction; pixel label offset in labelOffset()
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public PlotPoint labelOffset(double x, double y) {
    dx = Checks.finite(x, "label x");
    dy = Checks.finite(y, "label y");
    return this;
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
      throw new IllegalArgumentException("Plot point requires axes parent");
    double x = p.screenX(mx, c.time()), y = p.screenY(my, c.time());
    Color color = g.getColor();
    if (guides) {
      g.setColor(GlowRenderer.alpha(color, .5));
      g.setStroke(
          new BasicStroke(
              1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[] {6, 6}, 0));
      g.draw(new Line2D.Double(x, y, x, p.screenY(0, c.time())));
      g.draw(new Line2D.Double(x, y, p.screenX(0, c.time()), y));
    }
    var dot = new Ellipse2D.Double(x - 5, y - 5, 10, 10);
    g.setColor(color);
    g.fill(dot);
    GlowRenderer.draw(g, dot, color, 1.5, .6);
    g.setFont(FontManager.get(c.theme().font(), Font.PLAIN, 18));
    TextRenderer.draw(g, label, (float) (x + dx), (float) (y + dy), "center");
  }
}
