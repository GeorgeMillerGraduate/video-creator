/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.algorithm;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.TextRenderer;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.FontManager;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;

/**
 * Labeled graph vertex; position is relative to the containing graph.
 *
 * <p>Draws a labeled graph vertex with gradient fill and semantic state colours. Width/height
 * describe an origin-centered ellipse; transient highlight can override the configured state.
 */
public final class NodeObject extends SceneObject {
  /**
   * * Returns centred local shape bounds, consistent with the drawing origin.
   *
   * @param time sample time in scene-local seconds
   * @return axis-aligned local bounds at the requested time
   */
  @Override
  public java.awt.geom.Rectangle2D bounds(double time) {
    double w = value(io.jengacode.eduvideo.animation.Property.WIDTH, time),
        h = value(io.jengacode.eduvideo.animation.Property.HEIGHT, time);
    return new java.awt.geom.Rectangle2D.Double(-w / 2, -h / 2, w, h);
  }

  private final String label;

  /**
   * Persistent graph vertex display state configured before rendering. Transient execution emphasis
   * can additionally use the highlight timeline property.
   */
  public enum State {
    /** Use the configured vertex colour. */
    DEFAULT,
    /** Use the secondary accent for a selected vertex. */
    SELECTED,
    /** Use the success accent for a visited vertex. */
    VISITED,
    /** Use the warning accent and restrained glow for the active vertex. */
    ACTIVE
  }

  private State state = State.DEFAULT;

  /**
   * Sets the persistent semantic colour state of this graph vertex.
   *
   * @param state persistent semantic node state
   * @return this instance for fluent configuration
   */
  public NodeObject state(State state) {
    this.state = java.util.Objects.requireNonNull(state);
    return this;
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
   * Creates a configured NodeObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param label display label
   */
  public NodeObject(String id, String label) {
    super(id);
    this.label = label;
    set(Property.WIDTH, 64);
    set(Property.HEIGHT, 64);
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    double w = Math.max(0, value(Property.WIDTH, c.time())),
        h = Math.max(0, value(Property.HEIGHT, c.time()));
    Color accent =
        value(Property.HIGHLIGHT, c.time()) > .5
            ? c.theme().warning()
            : switch (state) {
              case DEFAULT -> color(c.time());
              case SELECTED -> c.theme().secondary();
              case VISITED -> c.theme().success();
              case ACTIVE -> c.theme().warning();
            };
    var shape = new Ellipse2D.Double(-w / 2, -h / 2, w, h);
    g.setPaint(
        new GradientPaint(
            0,
            (float) -h / 2,
            io.jengacode.eduvideo.render.GlowRenderer.alpha(accent, .3),
            0,
            (float) Math.max(.01, h / 2),
            c.theme().panel()));
    g.fill(shape);
    io.jengacode.eduvideo.render.GlowRenderer.draw(
        g,
        shape,
        accent,
        2,
        Math.max(value(Property.GLOW, c.time()), state == State.ACTIVE ? .5 : 0));
    g.setColor(c.theme().foreground());
    g.setFont(FontManager.get(c.theme().font(), Font.BOLD, 22));
    TextRenderer.draw(g, label, 0, 8, "center");
  }
}
