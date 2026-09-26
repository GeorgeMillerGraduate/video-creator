/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.objects;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.PathRenderer;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;

/**
 * Shared fill and progressively drawn outline behavior.
 *
 * <p>Provides a template method for time-sampled geometry. Fill occurs after complete draw
 * progress; before completion the outline is revealed. Dashed strokes are rendered without
 * multi-pass glow to retain dash spacing.
 */
public abstract class ShapeObject extends SceneObject {

  private boolean fill;

  private boolean dashed;

  /**
   * Enables dashed construction strokes on shape outlines.
   *
   * @param dashed whether shape outlines use a dashed stroke
   * @return this instance for fluent configuration
   */
  public ShapeObject dashed(boolean dashed) {
    this.dashed = dashed;
    return this;
  }

  /**
   * Creates a configured ShapeObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param fill fill colour or enabled fill mode
   */
  protected ShapeObject(String id, boolean fill) {
    super(id);
    this.fill = fill;
  }

  /**
   * Selects filled geometry or an outlined shape.
   *
   * @param fill fill colour or enabled fill mode
   * @return this instance for fluent configuration
   */
  public ShapeObject fill(boolean fill) {
    this.fill = fill;
    return this;
  }

  /**
   * Builds the local geometry at the requested scene time.
   *
   * @param time sample or event time in scene-local seconds
   * @return builds the local geometry at the requested scene time
   */
  protected abstract Shape shape(double time);

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    Shape s = shape(c.time());
    double p = value(Property.DRAW_PROGRESS, c.time());
    if (fill && p >= 1) g.fill(s);
    else {
      Shape partial = PathRenderer.partial(s, p);
      if (dashed) {
        g.setStroke(
            new BasicStroke(
                (float) Math.max(.01, value(Property.STROKE_WIDTH, c.time())),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                10,
                new float[] {8, 7},
                0));
        g.draw(partial);
      } else
        io.jengacode.eduvideo.render.GlowRenderer.draw(
            g,
            partial,
            g.getColor(),
            value(Property.STROKE_WIDTH, c.time()),
            value(Property.GLOW, c.time()));
    }
  }
}
