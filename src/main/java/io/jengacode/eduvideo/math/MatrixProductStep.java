/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.animation.*;
import io.jengacode.eduvideo.render.*;
import io.jengacode.eduvideo.scene.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;
import java.util.Locale;

/**
 * Reusable explanatory row-by-column operation. Copies operands from matrix anchors, simplifies
 * products, gathers the sum and flies it into the destination cell. Sources remain visible;
 * transient working content retires automatically. Numeric labels are checked against the authored
 * result so a beautiful animation cannot hide wrong arithmetic.
 */
public final class MatrixProductStep extends SceneObject {
  private final MatrixObject left, right, result;
  private final int row, column;
  private final double start, duration, workX, workY;
  private final double[] a, b;
  private final double sum;

  /**
   * Configures one cell computation using scene-coordinate work-centre and baseline.
   *
   * @param id stable nonblank identity, unique in its owning namespace
   * @param left left operand or set label
   * @param right right operand or set label
   * @param result matrix receiving the computed result
   * @param row zero-based output row
   * @param column zero-based output column
   * @param start nonnegative interval start in scene-local seconds (global seconds for audio cues)
   * @param duration positive interval length in seconds
   * @param workX scene-coordinate centre of the temporary working equation
   * @param workY scene-coordinate baseline of the working equation
   */
  public MatrixProductStep(
      String id,
      MatrixObject left,
      MatrixObject right,
      MatrixObject result,
      int row,
      int column,
      double start,
      double duration,
      double workX,
      double workY) {
    super(id);
    this.left = left;
    this.right = right;
    this.result = result;
    this.row = row;
    this.column = column;
    this.start = start;
    this.duration = duration;
    this.workX = workX;
    this.workY = workY;
    if (left.columnCount() != right.rowCount()
        || result.rowCount() != left.rowCount()
        || result.columnCount() != right.columnCount()
        || start < 0
        || duration <= 0
        || !Double.isFinite(start + duration + workX + workY))
      throw new IllegalArgumentException("Invalid matrix product dimensions or timing");
    a = new double[left.columnCount()];
    b = new double[a.length];
    double s = 0;
    for (int k = 0; k < a.length; k++) {
      a[k] = Double.parseDouble(left.cell(row, k));
      b[k] = Double.parseDouble(right.cell(k, column));
      s += a[k] * b[k];
    }
    sum = s;
    if (!Double.isFinite(sum)
        || Math.abs(Double.parseDouble(result.cell(row, column)) - sum) > 1e-8)
      throw new IllegalArgumentException(
          "Incorrect result matrix at " + row + "," + column + ": expected " + sum);
    left.highlightRow(row, start, duration, new Color(50, 212, 255));
    right.highlightColumn(column, start, duration, new Color(192, 157, 255));
    result.hideCell(row, column, 0);
    result.revealCell(row, column, start + duration * .91);
    result.highlightCell(
        row, column, start + duration * .91, duration * .09, new Color(76, 240, 179));
    z(900);
  }

  /**
   * Returns the exclusive end of the transient operation.
   *
   * @return exclusive end time in scene-local seconds
   */
  public double end() {
    return start + duration;
  }

  /**
   * Returns the independently computed result, useful for verification and captions.
   *
   * @return computed result or reserved conclusion rectangle, as described above
   */
  public double result() {
    return sum;
  }

  /**
   * * Formats integral values cleanly while retaining nonintegral arithmetic.
   *
   * @param x horizontal coordinate in the units documented by this operation
   * @return Formats integral values cleanly while retaining nonintegral arithmetic
   */
  private static String number(double x) {
    return Math.abs(x - Math.rint(x)) < 1e-9
        ? Long.toString(Math.round(x))
        : String.format(Locale.ROOT, "%.3f", x);
  }

  /**
   * Draws a label centred at a semantic position using inherited opacity.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param text plain text or SSML when explicitly enabled
   * @param x horizontal coordinate in the units documented by this operation
   * @param y vertical coordinate in the units documented by this operation
   * @param color paint colour for the travelling value
   * @param alpha opacity multiplier in the closed interval [0,1]
   * @param size positive font size in logical pixels
   */
  private void label(
      Graphics2D g, String text, double x, double y, Color color, double alpha, double size) {
    if (alpha <= 0) return;
    var q = (Graphics2D) g.create();
    try {
      float inherited = q.getComposite() instanceof AlphaComposite c ? c.getAlpha() : 1;
      q.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, alpha) * inherited));
      q.setColor(color);
      q.setFont(new Font("SansSerif", Font.BOLD, (int) size));
      TextRenderer.draw(q, text, (float) x, (float) y, "center");
    } finally {
      q.dispose();
    }
  }

  /**
   * * Samples a restrained arcing flight with zero velocity at both ends.
   *
   * @param from source path control point, copied on construction
   * @param to source path control point, copied on construction
   * @param p normalised path progress in [0,1]
   * @param bend signed vertical offset for the Bezier control points
   * @return Samples a restrained arcing flight with zero velocity at both ends
   */
  private Point2D flight(Point2D from, Point2D to, double p, double bend) {
    return MotionPath.bezier(
            from,
            new Point2D.Double(from.getX(), from.getY() + bend),
            new Point2D.Double(to.getX(), to.getY() + bend),
            to)
        .point(Easing.SMOOTH_STEP.applyAsDouble(p));
  }

  /**
   * Paints operand correspondence, simplification and answer transfer at any requested time.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param c scene-local clock, active theme and render settings
   */
  @Override
  protected void draw(Graphics2D g, RenderContext c) {
    double t = c.time();
    if (t < start || t >= end()) return;
    double p = (t - start) / duration;
    double gap = 230;
    Color cyan = c.theme().primary(),
        purple = new Color(192, 157, 255),
        green = c.theme().success();
    label(
        g,
        "ROW " + (row + 1) + "  ×  COLUMN " + (column + 1),
        workX,
        workY - 116,
        c.theme().secondary(),
        Math.min(1, p * 12),
        19);
    for (int k = 0; k < a.length; k++) {
      double x = workX + (k - (a.length - 1) * .5) * gap;
      double simplify = Easing.SMOOTH_STEP.applyAsDouble((p - .40) / .16),
          gather = Easing.SMOOTH_STEP.applyAsDouble((p - .65) / .12);
      if (p < .57) {
        double u = (p - .035 * k) / .26;
        var l =
            flight(
                left.worldAnchor("cell:" + row + ":" + k, t),
                new Point2D.Double(x - 61, workY - 14),
                u,
                75);
        var r =
            flight(
                right.worldAnchor("cell:" + k + ":" + column, t),
                new Point2D.Double(x + 61, workY - 14),
                u,
                100);
        double f = Math.min(1, Math.max(0, u * 8)) * (1 - simplify);
        label(g, number(a[k]), l.getX(), l.getY() + 14 - 24 * simplify, cyan, f, 42);
        label(g, number(b[k]), r.getX(), r.getY() + 14 - 24 * simplify, purple, f, 42);
        label(
            g,
            "×",
            x,
            workY - 24 * simplify,
            c.theme().secondary(),
            Math.max(0, Math.min(1, (p - .25) * 12)) * (1 - simplify),
            30);
      }
      label(
          g,
          number(a[k] * b[k]),
          x * (1 - gather) + workX * gather,
          workY + 32 * (1 - simplify),
          green,
          simplify * (1 - gather),
          48);
      if (k < a.length - 1)
        label(
            g,
            "+",
            x + gap / 2,
            workY,
            c.theme().secondary(),
            Math.max(0, Math.min(1, (p - .25) * 12)) * (1 - gather),
            30);
    }
    if (p >= .67) {
      double gather = Easing.SMOOTH_STEP.applyAsDouble((p - .67) / .1);
      var position =
          flight(
              new Point2D.Double(workX, workY - 15),
              result.worldAnchor("cell:" + row + ":" + column, t),
              (p - .79) / .12,
              -120);
      if (p < .91)
        label(
            g,
            number(sum),
            position.getX(),
            position.getY() + 15,
            green,
            gather,
            52 - 12 * Easing.SMOOTH_STEP.applyAsDouble((p - .79) / .12));
    }
  }
}
