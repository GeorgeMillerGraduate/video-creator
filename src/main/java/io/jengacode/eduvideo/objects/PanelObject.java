/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.objects;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.PanelRenderer;
import io.jengacode.eduvideo.scene.GroupObject;
import io.jengacode.eduvideo.util.Checks;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;

/**
 * Rounded container whose child coordinates start inside its padding. Uses the normal group
 * timeline.
 *
 * <p>Owns nested scene content while drawing its surface in outer coordinates. Padding translates
 * child origins but does not clip overflow; explicit width/height and child layout remain under
 * author control.
 */
public class PanelObject extends GroupObject {
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

  private double padding = 24, radius = 22, borderWidth = 1.3;
  private Color fill, bottom, border;

  private boolean shadow = true;

  /**
   * Returns the content translation applied before children are drawn.
   *
   * @return local content inset in pixels
   */
  public double padding() {
    return padding;
  }

  /**
   * Creates a configured PanelObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   */
  public PanelObject(String id) {
    super(id);
    set(Property.WIDTH, 400);
    set(Property.HEIGHT, 180);
  }

  /**
   * Configures gradient colours, rounded corners, border and shadow.
   *
   * @param fill fill colour or enabled fill mode
   * @param bottom lower gradient colour
   * @param border outline colour
   * @param radius nonnegative rounded-corner diameter in pixels
   * @param borderWidth nonnegative outline thickness in local pixels
   * @param shadow whether to render the restrained card shadow
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public PanelObject surface(
      Color fill, Color bottom, Color border, double radius, double borderWidth, boolean shadow) {
    this.fill = fill;
    this.bottom = bottom;
    this.border = border;
    this.radius = Checks.nonnegative(radius, "radius");
    this.borderWidth = Checks.nonnegative(borderWidth, "border width");
    this.shadow = shadow;
    return this;
  }

  /**
   * Sets the content inset in local pixels.
   *
   * @param padding nonnegative inset for child coordinates in pixels
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public PanelObject padding(double padding) {
    this.padding = Checks.nonnegative(padding, "padding");
    return this;
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    PanelRenderer.draw(
        g,
        0,
        0,
        value(Property.WIDTH, c.time()),
        value(Property.HEIGHT, c.time()),
        radius,
        fill == null ? c.theme().panel() : fill,
        bottom == null ? c.theme().backgroundSecondary() : bottom,
        border == null ? c.theme().panelBorder() : border,
        borderWidth,
        value(Property.GLOW, c.time()),
        shadow);
    g.translate(padding, padding);
    super.draw(g, c);
  }
}
