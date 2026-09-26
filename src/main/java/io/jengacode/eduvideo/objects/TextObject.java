/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.objects;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.TextRenderer;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;

/**
 * Baseline-aligned Unicode text with character or word reveal.
 *
 * <p>Stores immutable text with font, alignment and character/word reveal configuration. Character
 * reveal respects Unicode code points; multi-line text advances by 1.3 times the font size.
 */
public class TextObject extends SceneObject {

  protected final String text;

  protected String font = "SansSerif", align = "left";

  protected float size = 40;

  private boolean words;

  /**
   * * Measures multiline baseline-aligned bounds using the configured physical font metrics.
   *
   * @param time sample time in scene-local seconds
   * @return axis-aligned local bounds at the requested time
   */
  @Override
  public java.awt.geom.Rectangle2D bounds(double time) {
    var f = FontManager.get(font, fontStyle, size);
    var context = new java.awt.font.FontRenderContext(null, true, true);
    String[] lines = text.split("\\n", -1);
    double width = 0;
    for (String line : lines) width = Math.max(width, f.getStringBounds(line, context).getWidth());
    double x = align.equals("center") ? -width / 2 : align.equals("right") ? -width : 0;
    return new java.awt.geom.Rectangle2D.Double(
        x, -size, width, size * (1.3 * (lines.length - 1) + 1.25));
  }

  private int fontStyle = Font.PLAIN;

  /**
   * Selects bold or regular text weight.
   *
   * @param bold whether to use bold text
   * @return this instance for fluent configuration
   */
  public TextObject weight(boolean bold) {
    fontStyle = bold ? Font.BOLD : Font.PLAIN;
    return this;
  }

  /**
   * Creates a configured TextObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param text Unicode display text
   */
  public TextObject(String id, String text) {
    super(id);
    this.text = java.util.Objects.requireNonNull(text);
  }

  /**
   * Configures text family, positive font size and left/center/right alignment.
   *
   * @param font font family or measured Font, as required by the overload
   * @param size positive font size in local pixels
   * @param align left, center or right horizontal baseline alignment
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public TextObject style(String font, double size, String align) {
    if (!java.util.Set.of("left", "center", "right").contains(align))
      throw new IllegalArgumentException("Invalid alignment: " + align);
    this.font = font;
    this.size = (float) Checks.positive(size, "text size");
    this.align = align;
    return this;
  }

  /**
   * Selects word-based instead of Unicode code-point reveal.
   *
   * @param words whether reveal counts words instead of code points
   * @return this instance for fluent configuration
   */
  public TextObject words(boolean words) {
    this.words = words;
    return this;
  }

  /**
   * Reports whether this object accepts the requested animated property.
   *
   * @param p property to query or configure
   * @return true when the documented condition holds; false otherwise
   */
  public boolean supports(Property p) {
    return super.supports(p) || p == Property.REVEAL || p == Property.HIGHLIGHT;
  }

  /**
   * Returns the text prefix visible at the requested scene-local time.
   *
   * @param t sample time in scene-local seconds
   * @return the text prefix visible at the requested scene-local time
   */
  protected String revealed(double t) {
    double p = Checks.clamp(value(Property.REVEAL, t));
    if (words) {
      String[] a = text.split("\\s+");
      return String.join(" ", java.util.Arrays.copyOf(a, (int) Math.floor(a.length * p + 1e-9)));
    }
    int count = (int) Math.floor(text.codePointCount(0, text.length()) * p + 1e-9);
    return text.substring(0, text.offsetByCodePoints(0, count));
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    g.setFont(FontManager.get(font, fontStyle, size));
    String s = revealed(c.time());
    if (value(Property.HIGHLIGHT, c.time()) > .5) {
      g.setColor(c.theme().warning());
    }
    String[] lines = s.split("\n", -1);
    for (int i = 0; i < lines.length; i++)
      TextRenderer.draw(g, lines[i], 0, i * size * 1.3f, align);
  }
}
