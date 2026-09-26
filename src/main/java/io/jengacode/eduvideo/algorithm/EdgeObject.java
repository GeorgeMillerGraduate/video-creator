/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.algorithm;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.*;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;

/**
 * Directed/weighted graph edge, following endpoint animations in its parent graph.
 *
 * <p>Resolves node positions at each scene sample and shortens the line by endpoint radii. Directed
 * heads, labels, draw progress and emphasis follow the same parent coordinate system.
 */
public final class EdgeObject extends SceneObject {

  private final String from, to, label;

  private final boolean directed;

  /**
   * Creates a configured EdgeObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param from endpoint node identity
   * @param to endpoint node identity
   * @param label display label
   * @param directed directed used by this operation
   */
  public EdgeObject(String id, String from, String to, String label, boolean directed) {
    super(id);
    this.from = from;
    this.to = to;
    this.label = label;
    this.directed = directed;
    z(-1);
  }

  /**
   * Reports whether this object accepts the requested animated property.
   *
   * @param p property to query or configure
   * @return true when the documented condition holds; false otherwise
   */
  public boolean supports(Property p) {
    return super.supports(p) || p == Property.HIGHLIGHT;
  }

  /**
   * Returns from for this configured component.
   *
   * @return from for this configured component
   */
  public String from() {
    return from;
  }

  /**
   * Returns to for this configured component.
   *
   * @return to for this configured component
   */
  public String to() {
    return to;
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  protected void draw(Graphics2D g, RenderContext c) {
    SceneObject a = c.scene().find(from), b = c.scene().find(to);
    if (a.parent() != parent() || b.parent() != parent())
      throw new IllegalArgumentException("Edge endpoints must share its graph parent");
    double t = c.time(),
        ax = a.value(Property.X, t),
        ay = a.value(Property.Y, t),
        bx = b.value(Property.X, t),
        by = b.value(Property.Y, t),
        angle = Math.atan2(by - ay, bx - ax);
    double ar = a.value(Property.WIDTH, t) / 2, br = b.value(Property.WIDTH, t) / 2;
    double x1 = ax + ar * Math.cos(angle),
        y1 = ay + ar * Math.sin(angle),
        x2 = bx - br * Math.cos(angle),
        y2 = by - br * Math.sin(angle),
        p = Checks.clamp(value(Property.DRAW_PROGRESS, t));
    x2 = x1 + (x2 - x1) * p;
    y2 = y1 + (y2 - y1) * p;
    if (value(Property.HIGHLIGHT, t) > .5) g.setColor(c.theme().warning());
    GlowRenderer.draw(
        g,
        new Line2D.Double(x1, y1, x2, y2),
        g.getColor(),
        value(Property.STROKE_WIDTH, t),
        value(Property.GLOW, t));
    if (directed && p > 0) {
      var path = new Path2D.Double();
      path.moveTo(x2, y2);
      path.lineTo(x2 - 13 * Math.cos(angle - .5), y2 - 13 * Math.sin(angle - .5));
      path.lineTo(x2 - 13 * Math.cos(angle + .5), y2 - 13 * Math.sin(angle + .5));
      path.closePath();
      g.fill(path);
    }
    g.setFont(FontManager.get(c.theme().font(), Font.BOLD, 20));
    TextRenderer.draw(
        g, label, (float) ((x1 + x2) / 2 + 12), (float) ((y1 + y2) / 2 - 12), "center");
  }
}
