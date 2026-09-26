/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.algorithm;

import io.jengacode.eduvideo.render.*;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;

/**
 * Monospaced source code with pluggable syntax tokens and 1-based line highlights.
 *
 * <p>Keeps source immutable and delegates token colours to SyntaxHighlighter. Lines are 1-based;
 * selection columns are zero-based UTF-16 offsets with an exclusive end. Timed line highlights also
 * draw an execution pointer.
 */
public final class CodeBlockObject extends SceneObject {
  /**
   * Immutable timed highlight descriptor. Highlight windows are half-open, so adjacent events can
   * meet without ambiguity.
   *
   * @param line 1-based source line number (or raw source text for a highlighter)
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   */
  public record Highlight(int line, double start, double duration) {}

  private final String[] lines;

  private final java.util.List<Highlight> highlights = new ArrayList<>();

  private SyntaxHighlighter highlighter = new JavaHighlighter();

  private final float size;

  private String title = "";

  private boolean lineNumbers = true;

  /**
   * Immutable source selection on one line, with an exclusive column end and scene-local time
   * interval. Configuration validates bounds against the stored source.
   *
   * @param line 1-based source line number (or raw source text for a highlighter)
   * @param from from retained by this immutable descriptor
   * @param to to retained by this immutable descriptor
   * @param start nonnegative start time in scene-local seconds
   * @param end exclusive end time in seconds
   */
  private record Selection(int line, int from, int to, double start, double end) {}

  private final java.util.List<Selection> selections = new ArrayList<>();

  /**
   * Sets the optional editor filename or heading.
   *
   * @param title display heading or filename
   * @return this instance for fluent configuration
   */
  public CodeBlockObject title(String title) {
    this.title = Objects.requireNonNull(title);
    return this;
  }

  /**
   * Shows or hides the code panel line-number gutter.
   *
   * @param enabled whether this optional display mode is enabled
   * @return this instance for fluent configuration
   */
  public CodeBlockObject lineNumbers(boolean enabled) {
    lineNumbers = enabled;
    return this;
  }

  /**
   * Adds a timed code selection using a 1-based line and an exclusive UTF-16 column end.
   *
   * @param line 1-based source line number
   * @param from zero-based UTF-16 column start
   * @param to zero-based UTF-16 column exclusive end
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public CodeBlockObject select(int line, int from, int to, double start, double duration) {
    if (line < 1 || line > lines.length || from < 0 || to <= from || to > lines[line - 1].length())
      throw new IllegalArgumentException(
          "Invalid code selection (1-based line, zero-based UTF-16 columns)");
    Checks.nonnegative(start, "selection start");
    Checks.positive(duration, "selection duration");
    selections.add(new Selection(line, from, to, start, start + duration));
    return this;
  }

  /**
   * Creates a configured CodeBlockObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param code source code; common indentation is stripped
   * @param size positive font size in local pixels
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public CodeBlockObject(String id, String code, double size) {
    super(id);
    lines = code.stripIndent().strip().split("\n", -1);
    this.size = (float) Checks.positive(size, "code size");
  }

  /**
   * Replaces the source-preserving syntax token strategy.
   *
   * @param h replacement source tokenization strategy
   * @return this instance for fluent configuration
   */
  public CodeBlockObject highlighter(SyntaxHighlighter h) {
    highlighter = java.util.Objects.requireNonNull(h);
    return this;
  }

  /**
   * Adds a timed highlight and execution pointer to a 1-based source line.
   *
   * @param line 1-based source line number
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public CodeBlockObject highlight(int line, double start, double duration) {
    if (line < 1 || line > lines.length)
      throw new IllegalArgumentException("Code line out of range: " + line);
    Checks.nonnegative(start, "highlight start");
    Checks.positive(duration, "highlight duration");
    highlights.add(new Highlight(line, start, duration));
    return this;
  }

  /**
   * Returns the latest semantic event endpoint in scene-local seconds for scene validation.
   *
   * @return latest semantic event time in seconds, or zero when none exist
   */
  public double eventEnd() {
    return Math.max(
        highlights.stream().mapToDouble(h -> h.start + h.duration).max().orElse(0),
        selections.stream().mapToDouble(Selection::end).max().orElse(0));
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    g.setFont(FontManager.get("Monospaced", Font.PLAIN, size));
    var fm = g.getFontMetrics();
    int width = Arrays.stream(lines).mapToInt(fm::stringWidth).max().orElse(0) + 85;
    double lineHeight = size * 1.55;
    double header = title.isEmpty() ? 0 : 42;
    PanelRenderer.draw(
        g,
        0,
        -size - header,
        width + 20,
        lines.length * lineHeight + size + header,
        16,
        c.theme().panel(),
        c.theme().backgroundSecondary(),
        c.theme().panelBorder(),
        1,
        0,
        true);
    if (!title.isEmpty()) {
      g.setColor(c.theme().muted());
      g.drawString(title, 18, -size - 14);
    }

    for (int i = 0; i < lines.length; i++) {
      double y = i * lineHeight;
      for (var h : highlights)
        if (h.line == i + 1 && c.time() >= h.start && c.time() < h.start + h.duration) {
          g.setColor(c.theme().selection());
          g.fill(new RoundRectangle2D.Double(5, y - size, width + 10, lineHeight, 8, 8));
          g.setColor(c.theme().primary());
          g.fill(new Rectangle2D.Double(5, y - size, 3, lineHeight));
          var pointer = new Path2D.Double();
          pointer.moveTo(48, y - 10);
          pointer.lineTo(56, y - 5);
          pointer.lineTo(48, y);
          pointer.closePath();
          g.fill(pointer);
        }
      g.setColor(c.theme().muted());
      if (lineNumbers)
        g.drawString(String.format(java.util.Locale.ROOT, "%2d", i + 1), 12, (float) y);
      for (var s : selections)
        if (s.line == i + 1 && c.time() >= s.start && c.time() < s.end) {
          g.setColor(GlowRenderer.alpha(c.theme().warning(), .25));
          g.fill(
              new Rectangle2D.Double(
                  65 + fm.stringWidth(lines[i].substring(0, s.from)),
                  y - size,
                  fm.stringWidth(lines[i].substring(s.from, s.to)),
                  lineHeight));
        }
      float x = 65;
      for (var token : highlighter.highlight(lines[i], c.theme())) {
        g.setColor(token.color());
        g.drawString(token.text(), x, (float) y);
        x += fm.stringWidth(token.text());
      }
    }
  }
}
