/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.objects;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.*;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;

/**
 * Semantic answer card with centered text, inherited opacity and animatable glow/scale.
 *
 * <p>Chooses accent colours from SUCCESS, ANSWER, WARNING or INFO semantics and centers the answer
 * within a rounded card. Normal opacity, scale and glow properties can emphasize the result.
 */
public final class ResultObject extends SceneObject {
  /**
   * Semantic result colour roles. ANSWER and WARNING use the warning accent, SUCCESS uses success,
   * and INFO uses primary.
   */
  public enum Style {
    /** Success-coloured answer card. */
    SUCCESS,
    /** Highlighted answer using the warning/gold accent. */
    ANSWER,
    /** Warning-accented message card. */
    WARNING,
    /** Primary-accented information card. */
    INFO
  }

  private final String text;

  private final Style style;

  private final float size;

  /**
   * Creates a configured ResultObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param text Unicode display text
   * @param style non-null semantic answer colour role
   * @param size positive font size in local pixels
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public ResultObject(String id, String text, Style style, double size) {
    super(id);
    this.text = java.util.Objects.requireNonNull(text);
    this.style = java.util.Objects.requireNonNull(style);
    this.size = (float) Checks.positive(size, "result size");
    set(Property.WIDTH, 400);
    set(Property.HEIGHT, 95);
    set(Property.GLOW, .35);
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    Color accent =
        switch (style) {
          case SUCCESS -> c.theme().success();
          case ANSWER, WARNING -> c.theme().warning();
          case INFO -> c.theme().primary();
        };
    double w = value(Property.WIDTH, c.time()), h = value(Property.HEIGHT, c.time());
    PanelRenderer.draw(
        g,
        0,
        0,
        w,
        h,
        20,
        GlowRenderer.alpha(accent, .10),
        c.theme().backgroundSecondary(),
        accent,
        1.8,
        value(Property.GLOW, c.time()),
        true);
    g.setColor(accent);
    g.setFont(FontManager.get("Serif", Font.PLAIN, size));
    TextRenderer.draw(g, text, (float) w / 2, (float) h / 2 + size * .32f, "center");
  }
}
