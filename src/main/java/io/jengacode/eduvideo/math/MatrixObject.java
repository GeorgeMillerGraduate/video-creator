/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.render.*;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;

/**
 * Rectangular immutable labels with independently timed highlights and cell visibility. All times
 * are scene-local seconds.
 *
 * <p>Copies rectangular cell labels and stores visibility changes in per-cell ordered maps.
 * Row/column/cell highlights use half-open time intervals; the last configured overlapping
 * highlight wins. All indices are zero-based and independent of render order.
 */
public final class MatrixObject extends SceneObject {
  /**
   * Immutable row/column highlight event. A negative row or column is an internal wildcard; bounds
   * are validated by public configuration methods before construction.
   *
   * @param row zero-based matrix row index
   * @param column zero-based matrix column index
   * @param start nonnegative start time in scene-local seconds
   * @param end exclusive end time in seconds
   * @param color display or highlight colour
   */
  private record Mark(int row, int column, double start, double end, Color color) {}

  private final java.util.List<java.util.List<String>> rows;

  private final java.util.List<Mark> marks = new ArrayList<>();

  private final Map<Integer, NavigableMap<Double, Boolean>> visibility = new HashMap<>();

  private boolean initiallyVisible = true;

  /**
   * * Returns the matrix's complete cell-grid bounds in local pixels.
   *
   * @param time sample time in scene-local seconds
   * @return axis-aligned local bounds at the requested time
   */
  @Override
  public Rectangle2D bounds(double time) {
    return new Rectangle2D.Double(0, 0, columnCount() * cellWidth, rowCount() * cellHeight);
  }

  /**
   * * Accepts cell:r:c, row:r and column:c anchors with zero-based indices.
   *
   * @param name local semantic anchor name supported by this object
   * @param time sample time in scene-local seconds
   * @return semantic point in this object's local coordinates
   */
  @Override
  public Point2D anchor(String name, double time) {
    String[] p = name.split(":");
    if (p[0].equals("cell") && p.length == 3) {
      int r = Integer.parseInt(p[1]), c = Integer.parseInt(p[2]);
      check(r, c);
      return new Point2D.Double((c + .5) * cellWidth, (r + .5) * cellHeight);
    }
    if (p[0].equals("row") && p.length == 2) {
      int r = Integer.parseInt(p[1]);
      check(r, 0);
      return new Point2D.Double(columnCount() * cellWidth / 2, (r + .5) * cellHeight);
    }
    if (p[0].equals("column") && p.length == 2) {
      int c = Integer.parseInt(p[1]);
      check(0, c);
      return new Point2D.Double((c + .5) * cellWidth, rowCount() * cellHeight / 2);
    }
    return super.anchor(name, time);
  }

  private double cellWidth = 70, cellHeight = 55, fontSize = 28;

  /**
   * Creates a configured MatrixObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param rows nonempty rectangular matrix labels
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  public MatrixObject(String id, java.util.List<java.util.List<String>> rows) {
    super(id);
    if (rows.isEmpty() || rows.get(0).isEmpty())
      throw new IllegalArgumentException("Matrix cannot be empty");
    int n = rows.get(0).size();
    for (var row : rows) if (row.size() != n) throw new IllegalArgumentException("Ragged matrix");
    this.rows = rows.stream().map(java.util.List::copyOf).toList();
  }

  /**
   * Returns the number of matrix rows.
   *
   * @return the number of matrix rows
   */
  public int rowCount() {
    return rows.size();
  }

  /**
   * Returns the number of matrix columns.
   *
   * @return the number of matrix columns
   */
  public int columnCount() {
    return rows.get(0).size();
  }

  /**
   * Returns the immutable label at a zero-based row and column.
   *
   * @param row zero-based matrix row index
   * @param column zero-based matrix column index
   * @return the immutable label at a zero-based row and column
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  public String cell(int row, int column) {
    check(row, column);
    return rows.get(row).get(column);
  }

  /**
   * Configures cell dimensions and label font size in local pixels.
   *
   * @param width local pixel width
   * @param height local pixel height
   * @param size positive font size in local pixels
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  public MatrixObject cells(double width, double height, double size) {
    cellWidth = Checks.positive(width, "cell width");
    cellHeight = Checks.positive(height, "cell height");
    fontSize = Checks.positive(size, "font size");
    return this;
  }

  /**
   * Sets default cell visibility before any per-cell visibility event.
   *
   * @param visible initial or event visibility state
   * @return this instance for fluent configuration
   */
  public MatrixObject initiallyVisible(boolean visible) {
    initiallyVisible = visible;
    return this;
  }

  /**
   * Rejects matrix indices outside the rectangular label grid.
   *
   * @param row zero-based matrix row index
   * @param column zero-based matrix column index
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  private void check(int row, int column) {
    if (row < 0 || row >= rowCount() || column < 0 || column >= columnCount())
      throw new IllegalArgumentException(
          "Matrix index outside " + rowCount() + "x" + columnCount() + ": " + row + "," + column);
  }

  /**
   * Appends an internally validated highlight, allowing a row or column wildcard.
   *
   * @param row zero-based matrix row index
   * @param column zero-based matrix column index
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @param color display or highlight colour
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  private MatrixObject mark(int row, int column, double start, double duration, Color color) {
    Checks.nonnegative(start, "highlight start");
    Checks.positive(duration, "highlight duration");
    marks.add(
        new Mark(
            row,
            column,
            start,
            Checks.finite(start + duration, "highlight end"),
            Objects.requireNonNull(color)));
    return this;
  }

  /**
   * Adds a highlight across a zero-based matrix row during [start,start+duration).
   *
   * @param row zero-based matrix row index
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @param color display or highlight colour
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  public MatrixObject highlightRow(int row, double start, double duration, Color color) {
    check(row, 0);
    return mark(row, -1, start, duration, color);
  }

  /**
   * Adds a highlight down a zero-based matrix column during [start,start+duration).
   *
   * @param column zero-based matrix column index
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @param color display or highlight colour
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  public MatrixObject highlightColumn(int column, double start, double duration, Color color) {
    check(0, column);
    return mark(-1, column, start, duration, color);
  }

  /**
   * Adds a highlight to one matrix cell during [start,start+duration).
   *
   * @param row zero-based matrix row index
   * @param column zero-based matrix column index
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @param color display or highlight colour
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  public MatrixObject highlightCell(
      int row, int column, double start, double duration, Color color) {
    check(row, column);
    return mark(row, column, start, duration, color);
  }

  /**
   * Stores a timestamped cell visibility change; the latest event wins when sampled.
   *
   * @param row zero-based matrix row index
   * @param column zero-based matrix column index
   * @param time sample or event time in scene-local seconds
   * @param visible initial or event visibility state
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  private MatrixObject visibility(int row, int column, double time, boolean visible) {
    check(row, column);
    Checks.nonnegative(time, "cell time");
    visibility
        .computeIfAbsent(row * columnCount() + column, k -> new TreeMap<>())
        .put(time, visible);
    return this;
  }

  /**
   * Makes one matrix cell visible from the specified time until a later visibility event.
   *
   * @param row zero-based matrix row index
   * @param column zero-based matrix column index
   * @param time sample or event time in scene-local seconds
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  public MatrixObject revealCell(int row, int column, double time) {
    return visibility(row, column, time, true);
  }

  /**
   * Hides one matrix cell from the specified time until a later visibility event.
   *
   * @param row zero-based matrix row index
   * @param column zero-based matrix column index
   * @param time sample or event time in scene-local seconds
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  public MatrixObject hideCell(int row, int column, double time) {
    return visibility(row, column, time, false);
  }

  /**
   * Samples the last cell visibility event at or before time without changing state.
   *
   * @param row zero-based matrix row index
   * @param column zero-based matrix column index
   * @param time sample or event time in scene-local seconds
   * @return true when the documented condition holds; false otherwise
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  public boolean isCellVisible(int row, int column, double time) {
    check(row, column);
    var events = visibility.get(row * columnCount() + column);
    var entry = events == null ? null : events.floorEntry(time);
    return entry == null ? initiallyVisible : entry.getValue();
  }

  /**
   * Samples the last configured active row, column or cell highlight.
   *
   * @param row zero-based matrix row index
   * @param column zero-based matrix column index
   * @param time sample or event time in scene-local seconds
   * @return active highlight colour, or null when no highlight applies
   * @throws IllegalArgumentException if an index, time or dimension is outside its documented range
   */
  public Color highlightAt(int row, int column, double time) {
    check(row, column);
    Color color = null;
    for (var m : marks)
      if ((m.row < 0 || m.row == row)
          && (m.column < 0 || m.column == column)
          && time >= m.start
          && time < m.end) color = m.color;
    return color;
  }

  /**
   * Returns the latest semantic event endpoint in scene-local seconds for scene validation.
   *
   * @return latest semantic event time in seconds, or zero when none exist
   */
  public double eventEnd() {
    return Math.max(
        marks.stream().mapToDouble(Mark::end).max().orElse(0),
        visibility.values().stream()
            .filter(v -> !v.isEmpty())
            .mapToDouble(NavigableMap::lastKey)
            .max()
            .orElse(0));
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    double w = columnCount() * cellWidth, h = rowCount() * cellHeight, t = c.time();
    g.setFont(FontManager.get(c.theme().font(), Font.PLAIN, (float) fontSize));
    for (int i = 0; i < rowCount(); i++)
      for (int j = 0; j < columnCount(); j++) {
        Color mark = highlightAt(i, j, t);
        double x = j * cellWidth + 7, y = i * cellHeight + 4;
        if (mark != null)
          PanelRenderer.draw(
              g,
              x,
              y,
              cellWidth - 14,
              cellHeight - 8,
              10,
              GlowRenderer.alpha(mark, .17),
              GlowRenderer.alpha(mark, .05),
              GlowRenderer.alpha(mark, .8),
              1,
              .3,
              false);
        g.setColor(c.theme().foreground());
        if (isCellVisible(i, j, t))
          TextRenderer.draw(
              g,
              rows.get(i).get(j),
              (float) (j * cellWidth + cellWidth / 2),
              (float) (i * cellHeight + cellHeight / 2 + fontSize * .34),
              "center");
        else {
          g.setColor(c.theme().muted());
          g.fill(
              new Ellipse2D.Double(
                  j * cellWidth + cellWidth / 2 - 2, i * cellHeight + cellHeight / 2 - 2, 4, 4));
        }
      }
    var p = new Path2D.Double();
    p.moveTo(10, 0);
    p.lineTo(0, 0);
    p.lineTo(0, h);
    p.lineTo(10, h);
    p.moveTo(w - 10, 0);
    p.lineTo(w, 0);
    p.lineTo(w, h);
    p.lineTo(w - 10, h);
    GlowRenderer.draw(g, p, color(t), 2, .2);
  }
}
