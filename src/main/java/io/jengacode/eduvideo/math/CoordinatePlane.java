/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.scene.GroupObject;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;

/**
 * Cartesian coordinates in a bounded pixel rectangle; child plots share this transform.
 *
 * <p>Maps mathematical x/y coordinates to a bounded pixel rectangle, reversing y for screen space.
 * Functions, areas and plot points must be direct children; other children retain ordinary pixel
 * coordinates.
 */
public final class CoordinatePlane extends GroupObject {
  /**
   * Returns the container viewport rather than the union of overflowing child content.
   *
   * @param time scene-local sample time
   * @return configured outer rectangle in local coordinates
   */
  @Override
  public java.awt.geom.Rectangle2D bounds(double time) {
    return new java.awt.geom.Rectangle2D.Double(
        0,
        0,
        value(io.jengacode.eduvideo.animation.Property.WIDTH, time),
        value(io.jengacode.eduvideo.animation.Property.HEIGHT, time));
  }

  /**
   * * Resolves math:x:y coordinates through the animated plane dimensions.
   *
   * @param name local semantic anchor name supported by this object
   * @param time sample time in scene-local seconds
   * @return semantic point in this object's local coordinates
   */
  @Override
  public java.awt.geom.Point2D anchor(String name, double time) {
    String[] p = name.split(":");
    if (p.length == 3 && p[0].equals("math"))
      return new java.awt.geom.Point2D.Double(
          screenX(Double.parseDouble(p[1]), time), screenY(Double.parseDouble(p[2]), time));
    return super.anchor(name, time);
  }

  private final double xmin, xmax, ymin, ymax;

  private final boolean grid;

  private final double step;

  private double gridOpacity = 1, tickSize = 15;

  private Color axisColor;

  /**
   * Configures grid opacity, tick typography and an optional axis colour override.
   *
   * @param opacity alpha multiplier in [0,1]
   * @param tickSize positive tick-label font size in pixels
   * @param axisColor axis colour override; null uses the active theme
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public CoordinatePlane appearance(double opacity, double tickSize, Color axisColor) {
    this.gridOpacity = Checks.clamp(Checks.finite(opacity, "grid opacity"));
    this.tickSize = Checks.positive(tickSize, "tick size");
    this.axisColor = axisColor;
    return this;
  }

  private String xlabel = "x", ylabel = "y";

  /**
   * Creates a configured CoordinatePlane instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param xmin finite lower mathematical x bound
   * @param xmax finite upper mathematical x bound
   * @param ymin finite lower mathematical y bound
   * @param ymax finite upper mathematical y bound
   * @param grid whether grid lines are enabled
   * @param step positive major tick spacing in mathematical units
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public CoordinatePlane(
      String id, double xmin, double xmax, double ymin, double ymax, boolean grid, double step) {
    super(id);
    Checks.finite(xmin, "xmin");
    Checks.finite(xmax, "xmax");
    Checks.finite(ymin, "ymin");
    Checks.finite(ymax, "ymax");
    if (xmin >= xmax || ymin >= ymax)
      throw new IllegalArgumentException("Axis minima must be less than maxima");
    this.xmin = xmin;
    this.xmax = xmax;
    this.ymin = ymin;
    this.ymax = ymax;
    this.grid = grid;
    this.step = Checks.positive(step, "grid step");
    if ((xmax - xmin + ymax - ymin) / step > 2000)
      throw new IllegalArgumentException("Too many grid lines");
  }

  /**
   * Sets the horizontal and vertical axis labels.
   *
   * @param x mathematical x-coordinate, or horizontal label in labels()
   * @param y mathematical y-coordinate, or vertical label in labels()
   * @return this instance for fluent configuration
   */
  public CoordinatePlane labels(String x, String y) {
    xlabel = x;
    ylabel = y;
    return this;
  }

  /**
   * Returns xmin for this configured component.
   *
   * @return xmin for this configured component
   */
  public double xmin() {
    return xmin;
  }

  /**
   * Returns xmax for this configured component.
   *
   * @return xmax for this configured component
   */
  public double xmax() {
    return xmax;
  }

  /**
   * Returns ymin for this configured component.
   *
   * @return ymin for this configured component
   */
  public double ymin() {
    return ymin;
  }

  /**
   * Returns ymax for this configured component.
   *
   * @return ymax for this configured component
   */
  public double ymax() {
    return ymax;
  }

  /**
   * Maps a mathematical x-coordinate to local pixel space.
   *
   * @param x mathematical x-coordinate, or horizontal label in labels()
   * @param t sample time in scene-local seconds
   * @return maps a mathematical x-coordinate to local pixel space
   */
  public double screenX(double x, double t) {
    return (x - xmin) / (xmax - xmin) * value(Property.WIDTH, t);
  }

  /**
   * Maps a mathematical y-coordinate to local pixels, reversing the vertical direction.
   *
   * @param y mathematical y-coordinate, or vertical label in labels()
   * @param t sample time in scene-local seconds
   * @return maps a mathematical y-coordinate to local pixels, reversing the vertical direction
   */
  public double screenY(double y, double t) {
    return (ymax - y) / (ymax - ymin) * value(Property.HEIGHT, t);
  }

  /**
   * Inverts the horizontal mathematical-to-pixel mapping.
   *
   * @param px horizontal local pixel coordinate
   * @param t sample time in scene-local seconds
   * @return inverts the horizontal mathematical-to-pixel mapping
   */
  public double mathX(double px, double t) {
    return xmin + px / value(Property.WIDTH, t) * (xmax - xmin);
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    double t = c.time(), w = value(Property.WIDTH, t), h = value(Property.HEIGHT, t);
    if (w <= 0 || h <= 0) return;
    Graphics2D gridGraphics = (Graphics2D) g.create();
    try {
      gridGraphics.clip(new Rectangle2D.Double(0, 0, w, h));
      if (grid) {
        for (int minor = 1; minor >= 0; minor--) {
          double s = minor == 1 ? step / 5 : step;
          gridGraphics.setColor(
              io.jengacode.eduvideo.render.GlowRenderer.alpha(
                  c.theme().grid(minor == 1), gridOpacity));
          gridGraphics.setStroke(new BasicStroke(1));
          for (double x = Math.ceil(xmin / s) * s; x <= xmax; x += s)
            gridGraphics.draw(new Line2D.Double(screenX(x, t), 0, screenX(x, t), h));
          for (double y = Math.ceil(ymin / s) * s; y <= ymax; y += s)
            gridGraphics.draw(new Line2D.Double(0, screenY(y, t), w, screenY(y, t)));
        }
      }
      gridGraphics.setColor(axisColor == null ? c.theme().axis() : axisColor);
      gridGraphics.setStroke(new BasicStroke(1.8f));
      if (xmin <= 0 && xmax >= 0)
        gridGraphics.draw(new Line2D.Double(screenX(0, t), 0, screenX(0, t), h));
      if (ymin <= 0 && ymax >= 0)
        gridGraphics.draw(new Line2D.Double(0, screenY(0, t), w, screenY(0, t)));
    } finally {
      gridGraphics.dispose();
    }
    g.setColor(c.theme().foreground());
    g.setFont(FontManager.get(c.theme().font(), Font.PLAIN, (float) tickSize));
    double axisY = Math.max(0, Math.min(h, screenY(0, t))),
        axisX = Math.max(0, Math.min(w, screenX(0, t)));
    for (double x = Math.ceil(xmin / step) * step; x <= xmax; x += step) {
      double px = screenX(x, t);
      g.draw(new Line2D.Double(px, axisY - 4, px, axisY + 4));
      if (Math.abs(x) > 1e-8) g.drawString(label(x), (float) px + 4, (float) axisY + 19);
    }
    for (double y = Math.ceil(ymin / step) * step; y <= ymax; y += step) {
      double py = screenY(y, t);
      g.draw(new Line2D.Double(axisX - 4, py, axisX + 4, py));
      if (Math.abs(y) > 1e-8) g.drawString(label(y), (float) axisX + 9, (float) py - 4);
    }
    g.drawString("0", (float) axisX + 6, (float) axisY + 18);
    g.drawString(xlabel, (float) w - 14, (float) axisY - 12);
    g.drawString(ylabel, (float) axisX - 24, -12);
    super.draw(g, c);
  }

  /**
   * Configures label for this component.
   *
   * @param n numeric value or union-find key, according to the overload
   * @return configures label for this component
   */
  private static String label(double n) {
    return Math.abs(n - Math.rint(n)) < 1e-8
        ? Long.toString(Math.round(n))
        : String.format(java.util.Locale.ROOT, "%.1f", n);
  }
}
