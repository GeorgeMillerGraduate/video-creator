/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.objects;

import io.jengacode.eduvideo.render.*;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;

/**
 * Instruction card with a numbered badge and heading; place child content below y=40 inside
 * padding.
 *
 * <p>Adds a numbered badge and instructional heading to a PanelObject. Children use padded
 * coordinates; reserve the first 40 pixels for the header. Animate the group for coordinated
 * entrance effects.
 */
public final class StepObject extends PanelObject {

  private final int number;

  private final String title;

  /**
   * Creates a configured StepObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param number positive instructional step number
   * @param title display heading or filename
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public StepObject(String id, int number, String title) {
    super(id);
    if (number < 1) throw new IllegalArgumentException("Step number must be positive");
    this.number = number;
    this.title = java.util.Objects.requireNonNull(title);
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    Graphics2D content = (Graphics2D) g.create();
    try {
      super.draw(content, c);
    } finally {
      content.dispose();
    }
    g.setColor(c.theme().primary());
    g.setFont(FontManager.get(c.theme().font(), Font.BOLD, 20));
    GlowRenderer.draw(g, new Ellipse2D.Double(22, 17, 34, 34), c.theme().primary(), 1.8, .35);
    TextRenderer.draw(g, Integer.toString(number), 39, 41, "center");
    g.setColor(c.theme().foreground());
    TextRenderer.draw(g, title, 70, 41, "left");
  }
}
