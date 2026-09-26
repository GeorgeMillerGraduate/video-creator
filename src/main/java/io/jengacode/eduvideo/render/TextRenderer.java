/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.render;

import java.awt.*;
import java.awt.font.TextLayout;

/**
 * Unicode text layout with baseline positioning.
 *
 * <p>Uses TextLayout for antialiased Unicode glyph placement and fractional advances. Positions
 * describe the text baseline; alignment adjusts horizontal origin without changing content.
 */
public final class TextRenderer {
  /** Prevents instantiation of this static utility. */
  private TextRenderer() {}

  /** Immutable layout key includes the actual font and device transform. */
  private record Key(String text, Font font, java.awt.font.FontRenderContext context) {}

  private static final java.util.Map<Key, TextLayout> CACHE =
      new java.util.LinkedHashMap<>(256, .75f, true);

  /**
   * * * Bounded cache avoids rebuilding static labels while supporting different output
   * resolutions.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param text plain text or SSML when explicitly enabled
   * @return cached immutable text or equation layout
   */
  private static synchronized TextLayout layout(Graphics2D g, String text) {
    var key = new Key(text, g.getFont(), g.getFontRenderContext());
    var result = CACHE.get(key);
    if (result == null) {
      result = new TextLayout(text, key.font(), key.context());
      if (CACHE.size() >= 2048) CACHE.remove(CACHE.keySet().iterator().next());
      CACHE.put(key, result);
    }
    return result;
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param text Unicode display text
   * @param x local pixel x coordinate (mathematical units for plot inputs)
   * @param y local pixel y coordinate (mathematical units for plot inputs)
   * @param align left, center or right horizontal baseline alignment
   */
  public static void draw(Graphics2D g, String text, float x, float y, String align) {
    if (text.isEmpty()) return;
    var layout = layout(g, text);
    float offset =
        switch (align) {
          case "center" -> layout.getAdvance() / 2;
          case "right" -> layout.getAdvance();
          default -> 0;
        };
    layout.draw(g, x - offset, y);
  }
}
