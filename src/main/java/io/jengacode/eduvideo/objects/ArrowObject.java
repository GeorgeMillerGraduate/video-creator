/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.objects;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.util.Checks;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;

/**
 * Arrowhead tracks the progressively drawn endpoint.
 *
 * <p>Adds a filled arrowhead to LineObject. The head follows the visible endpoint as drawProgress
 * changes and is omitted when progress is zero.
 */
public class ArrowObject extends LineObject {
  /**
   * Creates a configured ArrowObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   */
  public ArrowObject(String id) {
    super(id);
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
    double x = value(Property.WIDTH, c.time()) * p,
        y = value(Property.HEIGHT, c.time()) * p,
        a = Math.atan2(y, x),
        r = Math.min(15, Math.hypot(x, y) / 2);
    var tip = new Path2D.Double();
    tip.moveTo(x, y);
    tip.lineTo(x - r * Math.cos(a - .5), y - r * Math.sin(a - .5));
    tip.lineTo(x - r * Math.cos(a + .5), y - r * Math.sin(a + .5));
    tip.closePath();
    g.fill(tip);
  }
}
