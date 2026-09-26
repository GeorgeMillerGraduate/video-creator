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

/**
 * Two-set union, intersection or difference drawn with exact Java2D area operations. DRAW_PROGRESS
 * reveals the selected region while set outlines preserve spatial correspondence.
 */
public final class VennDiagram extends SceneObject {
  private final String operation, left, right;

  /**
   * Creates a reusable set construction with explicit labels.
   *
   * @param id stable nonblank identity, unique in its owning namespace
   * @param operation union, intersection or difference
   * @param left display label for the left set
   * @param right display label for the right set
   */
  public VennDiagram(String id, String operation, String left, String right) {
    super(id);
    if (!java.util.Set.of("union", "intersection", "difference").contains(operation))
      throw new IllegalArgumentException("Unknown set operation");
    this.operation = operation;
    this.left = left;
    this.right = right;
    set(Property.WIDTH, 800);
    set(Property.HEIGHT, 450);
  }

  /**
   * Computes and fills the selected set region, then draws persistent set boundaries.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param c scene-local clock, active theme and render settings
   */
  @Override
  protected void draw(Graphics2D g, RenderContext c) {
    double w = value(Property.WIDTH, c.time()), h = value(Property.HEIGHT, c.time());
    var a = new Ellipse2D.Double(w * .08, h * .08, w * .56, h * .8);
    var b = new Ellipse2D.Double(w * .36, h * .08, w * .56, h * .8);
    var region = new Area(a);
    switch (operation) {
      case "union" -> region.add(new Area(b));
      case "intersection" -> region.intersect(new Area(b));
      case "difference" -> region.subtract(new Area(b));
    }
    g.setColor(
        new Color(
            76,
            240,
            179,
            (int) (140 * Math.max(0, Math.min(1, value(Property.DRAW_PROGRESS, c.time()))))));
    g.fill(region);
    g.setStroke(new BasicStroke(3));
    g.setColor(c.theme().primary());
    g.draw(a);
    g.setColor(new Color(192, 157, 255));
    g.draw(b);
    g.setFont(new Font(c.theme().font(), Font.BOLD, 30));
    g.setColor(c.theme().foreground());
    TextRenderer.draw(g, left, (float) (w * .27), (float) (h * .49), "center");
    TextRenderer.draw(g, right, (float) (w * .73), (float) (h * .49), "center");
  }
}
