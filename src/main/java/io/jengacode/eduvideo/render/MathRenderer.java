/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.render;

import java.awt.*;
import java.awt.font.TextLayout;
import java.util.*;

/**
 * Small dependency-free equation layout for Unicode, fractions, roots, and braced scripts. Not a
 * full LaTeX interpreter.
 *
 * <p>Builds lightweight layout boxes for Unicode plus \frac{a}{b}, \sqrt{x}, ^{superscripts} and
 * _{subscripts}. Box metrics reserve ascent/descent for fractions. This intentionally remains a
 * small subset, not a general LaTeX engine.
 */
public final class MathRenderer implements EquationRenderer {
  /** Immutable typesetting cache key includes device metrics and the actual font. */
  private record CacheKey(String equation, Font font, java.awt.font.FontRenderContext context) {}

  private static final java.util.Map<CacheKey, Box> CACHE =
      new java.util.LinkedHashMap<>(64, .75f, true);

  /**
   * Reuses immutable box trees for unchanged equations, bounded to 512 layouts.
   *
   * @param equation validated mathematical markup
   * @param g graphics context supplying font metrics
   * @return immutable measured box tree
   */
  private static synchronized Box layout(String equation, Graphics2D g) {
    var key = new CacheKey(equation, g.getFont(), g.getFontRenderContext());
    Box result = CACHE.get(key);
    if (result == null) {
      result = new Parser(equation, g).row(g.getFont(), false);
      if (CACHE.size() >= 512) CACHE.remove(CACHE.keySet().iterator().next());
      CACHE.put(key, result);
    }
    return result;
  }

  /**
   * Measures a typeset expression for semantic bounds and equation alignment.
   *
   * @param equation mathematical markup using the supported fraction/root/script syntax
   * @param font actual glyph font
   * @param alignment left, center or right baseline alignment
   * @return baseline-relative expression bounds in logical pixels
   */
  public static java.awt.geom.Rectangle2D measure(String equation, Font font, String alignment) {
    var raster = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    var g = raster.createGraphics();
    try {
      io.jengacode.eduvideo.video.VideoRenderer.quality(g);
      g.setFont(font);
      var box = layout(equation, g);
      double x =
          alignment.equals("center")
              ? -box.width() / 2.
              : alignment.equals("right") ? -box.width() : 0;
      return new java.awt.geom.Rectangle2D.Double(
          x, -box.above(), box.width(), box.above() + box.below());
    } finally {
      g.dispose();
    }
  }

  /**
   * Internal baseline-based mathematical layout contract. Width, ascent and descent permit nested
   * fractions and scripts to align without raster intermediates.
   */
  private interface Box {
    /**
     * Returns the horizontal advance of this mathematical layout box.
     *
     * @return the horizontal advance of this mathematical layout box
     */
    float width();

    /**
     * Returns the box extent above its baseline.
     *
     * @return the box extent above its baseline
     */
    float above();

    /**
     * Returns the box extent below its baseline.
     *
     * @return the box extent below its baseline
     */
    float below();

    /**
     * Draws the visual content in the supplied local coordinate system.
     *
     * @param g graphics context positioned in local coordinates
     * @param x local pixel x coordinate (mathematical units for plot inputs)
     * @param y local pixel y coordinate (mathematical units for plot inputs)
     */
    void draw(Graphics2D g, float x, float y);
  }

  /**
   * Measured immutable glyph run with its font and baseline metrics. Drawing does not remeasure or
   * alter the source equation.
   *
   * @param text Unicode display text
   * @param font font family or measured Font, as required by the overload
   * @param width local pixel width
   * @param above above retained by this immutable descriptor
   * @param below below retained by this immutable descriptor
   */
  private record Glyph(String text, Font font, float width, float above, float below)
      implements Box {
    /**
     * Draws the visual content in the supplied local coordinate system.
     *
     * @param g graphics context positioned in local coordinates
     * @param x local pixel x coordinate (mathematical units for plot inputs)
     * @param y local pixel y coordinate (mathematical units for plot inputs)
     */
    public void draw(Graphics2D g, float x, float y) {
      g.setFont(font);
      TextRenderer.draw(g, text, x, y, "left");
    }
  }

  /**
   * Horizontal sequence of equation boxes. Width is the sum of advances; ascent and descent are
   * maxima across children.
   *
   * @param boxes boxes retained by this immutable descriptor
   * @param width local pixel width
   * @param above above retained by this immutable descriptor
   * @param below below retained by this immutable descriptor
   */
  private record Row(java.util.List<Box> boxes, float width, float above, float below)
      implements Box {
    /**
     * Draws the visual content in the supplied local coordinate system.
     *
     * @param g graphics context positioned in local coordinates
     * @param x local pixel x coordinate (mathematical units for plot inputs)
     * @param y local pixel y coordinate (mathematical units for plot inputs)
     */
    public void draw(Graphics2D g, float x, float y) {
      for (var b : boxes) {
        b.draw(g, x, y);
        x += b.width();
      }
    }
  }

  /**
   * Stacks numerator and denominator around a rule above the surrounding baseline. Padding
   * separates glyph extents from the fraction rule.
   *
   * @param top top retained by this immutable descriptor
   * @param bottom lower gradient colour
   * @param width local pixel width
   * @param above above retained by this immutable descriptor
   * @param below below retained by this immutable descriptor
   * @param size positive font size in local pixels
   */
  private record Fraction(Box top, Box bottom, float width, float above, float below, float size)
      implements Box {
    /**
     * Draws the visual content in the supplied local coordinate system.
     *
     * @param g graphics context positioned in local coordinates
     * @param x local pixel x coordinate (mathematical units for plot inputs)
     * @param y local pixel y coordinate (mathematical units for plot inputs)
     */
    public void draw(Graphics2D g, float x, float y) {
      float line = y - size * .25f;
      top.draw(g, x + (width - top.width()) / 2, line - 5 - top.below());
      bottom.draw(g, x + (width - bottom.width()) / 2, line + 5 + bottom.above());
      g.setStroke(new BasicStroke(Math.max(1, size / 28)));
      g.draw(new java.awt.geom.Line2D.Float(x, line, x + width, line));
    }
  }

  /**
   * Offsets a smaller box vertically for superscript or subscript layout. Its metrics account for
   * the offset in the enclosing row.
   *
   * @param box box retained by this immutable descriptor
   * @param offset offset retained by this immutable descriptor
   */
  private record Script(Box box, float offset) implements Box {
    /**
     * Returns the horizontal advance of this mathematical layout box.
     *
     * @return the horizontal advance of this mathematical layout box
     */
    public float width() {
      return box.width();
    }

    /**
     * Returns the box extent above its baseline.
     *
     * @return the box extent above its baseline
     */
    public float above() {
      return Math.max(0, box.above() - offset);
    }

    /**
     * Returns the box extent below its baseline.
     *
     * @return the box extent below its baseline
     */
    public float below() {
      return Math.max(0, box.below() + offset);
    }

    /**
     * Draws the visual content in the supplied local coordinate system.
     *
     * @param g graphics context positioned in local coordinates
     * @param x local pixel x coordinate (mathematical units for plot inputs)
     * @param y local pixel y coordinate (mathematical units for plot inputs)
     */
    public void draw(Graphics2D g, float x, float y) {
      box.draw(g, x, y + offset);
    }
  }

  /**
   * Draws a radical glyph and overbar around its child box. Width includes room for the radical;
   * the overbar follows the child ascent.
   *
   * @param box box retained by this immutable descriptor
   * @param size positive font size in local pixels
   */
  private record Root(Box box, float size) implements Box {
    /**
     * Returns the horizontal advance of this mathematical layout box.
     *
     * @return the horizontal advance of this mathematical layout box
     */
    public float width() {
      return box.width() + size * .7f;
    }

    /**
     * Returns the box extent above its baseline.
     *
     * @return the box extent above its baseline
     */
    public float above() {
      return box.above() + 5;
    }

    /**
     * Returns the box extent below its baseline.
     *
     * @return the box extent below its baseline
     */
    public float below() {
      return box.below();
    }

    /**
     * Draws the visual content in the supplied local coordinate system.
     *
     * @param g graphics context positioned in local coordinates
     * @param x local pixel x coordinate (mathematical units for plot inputs)
     * @param y local pixel y coordinate (mathematical units for plot inputs)
     */
    public void draw(Graphics2D g, float x, float y) {
      g.setFont(g.getFont().deriveFont(size));
      TextRenderer.draw(g, "√", x, y, "left");
      box.draw(g, x + size * .65f, y);
      g.setStroke(new BasicStroke(1.2f));
      g.draw(
          new java.awt.geom.Line2D.Float(
              x + size * .58f, y - box.above() - 3, x + width(), y - box.above() - 3));
    }
  }

  /**
   * Internal cursor-based equation layout parser. Recursive rows consume braced groups and
   * construct boxes using the active font metrics.
   */
  private static final class Parser {

    private final String source;

    private int pos;

    private final Graphics2D g;

    /**
     * Creates a configured Parser instance.
     *
     * @param source expression source text or audio source path, according to the overload
     * @param g graphics context positioned in local coordinates
     */
    Parser(String source, Graphics2D g) {
      this.source = source;
      this.g = g;
    }

    /**
     * Parses a row of mathematical boxes, optionally stopping at a closing brace.
     *
     * @param font font family or measured Font, as required by the overload
     * @param nested whether a closing brace ends this row
     * @return parses a row of mathematical boxes, optionally stopping at a closing brace
     */
    Box row(Font font, boolean nested) {
      var list = new ArrayList<Box>();
      StringBuilder plain = new StringBuilder();
      while (pos < source.length()) {
        char ch = source.charAt(pos);
        if (ch == '}' && nested) {
          pos++;
          break;
        }
        boolean fraction = source.startsWith("\\frac{", pos),
            root = source.startsWith("\\sqrt{", pos);
        if (fraction || root || ch == '^' || ch == '_') {
          if (plain.length() > 0) {
            list.add(glyph(plain.toString(), font));
            plain.setLength(0);
          }
          if (fraction) {
            pos += 6;
            Box top = row(font.deriveFont(font.getSize2D() * .85f), true);
            expect('{');
            Box bottom = row(font.deriveFont(font.getSize2D() * .85f), true);
            float size = font.getSize2D();
            list.add(
                new Fraction(
                    top,
                    bottom,
                    Math.max(top.width(), bottom.width()) + 12,
                    top.above() + top.below() + 5 + size * .25f,
                    bottom.above() + bottom.below() + 5 - size * .25f,
                    size));
          } else if (root) {
            pos += 6;
            list.add(new Root(row(font, true), font.getSize2D()));
          } else {
            pos++;
            Font small = font.deriveFont(font.getSize2D() * .65f);
            Box content;
            if (pos < source.length() && source.charAt(pos) == '{') {
              pos++;
              content = row(small, true);
            } else if (pos < source.length())
              content = glyph(String.valueOf(source.charAt(pos++)), small);
            else content = glyph("", small);
            list.add(new Script(content, font.getSize2D() * (ch == '^' ? -.55f : .25f)));
          }
        } else {
          plain.append(ch);
          pos++;
        }
      }
      if (plain.length() > 0) list.add(glyph(plain.toString(), font));
      float width = 0, above = 0, below = 0;
      for (var b : list) {
        width += b.width();
        above = Math.max(above, b.above());
        below = Math.max(below, b.below());
      }
      return new Row(java.util.List.copyOf(list), width, above, below);
    }

    /**
     * Requires and consumes the expected delimiter.
     *
     * @param ch expected delimiter or token character
     * @throws IllegalArgumentException if an input violates the constraints described above
     */
    private void expect(char ch) {
      if (pos >= source.length() || source.charAt(pos++) != ch)
        throw new IllegalArgumentException("Expected '" + ch + "' in mathematical text");
    }

    /**
     * Measures a glyph run with the active font render context.
     *
     * @param text Unicode display text
     * @param font font family or measured Font, as required by the overload
     * @return measures a glyph run with the active font render context
     */
    private Box glyph(String text, Font font) {
      if (text.isEmpty()) return new Glyph(text, font, 0, 0, 0);
      var layout = new TextLayout(text, font, g.getFontRenderContext());
      return new Glyph(text, font, layout.getAdvance(), layout.getAscent(), layout.getDescent());
    }
  }

  /**
   * Rejects unbalanced braces in structured mathematical text.
   *
   * @param equation equation text in the selected renderer notation
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static void validate(String equation) {
    int depth = 0;
    for (int i = 0; i < equation.length(); i++) {
      char ch = equation.charAt(i);
      if (ch == '{') depth++;
      else if (ch == '}' && --depth < 0)
        throw new IllegalArgumentException("Unmatched mathematical brace");
    }
    if (depth != 0) throw new IllegalArgumentException("Unmatched mathematical brace");
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param graphics caller graphics context, preserved where a child context is created
   * @param equation equation text in the selected renderer notation
   * @param alignment left, center or right horizontal baseline alignment
   */
  public void draw(Graphics2D graphics, String equation, String alignment) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      Box box = layout(equation, g);
      float x =
          switch (alignment) {
            case "center" -> -box.width() / 2;
            case "right" -> -box.width();
            default -> 0;
          };
      box.draw(g, x, 0);
    } finally {
      g.dispose();
    }
  }
}
