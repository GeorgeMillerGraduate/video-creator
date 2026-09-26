/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.render.TextRenderer;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;

/**
 * Deterministic data plot with live sample count, bar/histogram/scatter views and a fitted
 * normal-density curve. Labels report population variance and standard deviation. The displayed 95%
 * mean interval uses the normal approximation, not an exact small-sample CI.
 */
public final class StatisticsPlot extends SceneObject {
  private final double[] data;
  private final String mode;

  /**
   * Copies finite observations; PARAM_A selects the number of included samples.
   *
   * @param id stable nonblank identity, unique in its owning namespace
   * @param mode supported construction or plotting mode
   * @param data finite observations copied on construction
   */
  public StatisticsPlot(String id, String mode, double[] data) {
    super(id);
    if (data.length < 1 || !Set.of("bars", "histogram", "scatter", "normal").contains(mode))
      throw new IllegalArgumentException("Invalid statistics plot");
    this.data = data.clone();
    for (double d : data)
      if (!Double.isFinite(d)) throw new IllegalArgumentException("Nonfinite observation");
    this.mode = mode;
    set(Property.PARAM_A, data.length);
    set(Property.WIDTH, 1000);
    set(Property.HEIGHT, 550);
  }

  /**
   * * Enables deterministic sample-count animation.
   *
   * @param p normalised progress or validated component, according to this signature
   * @return true if the object accepts the requested property
   */
  @Override
  public boolean supports(Property p) {
    return p == Property.PARAM_A || super.supports(p);
  }

  /**
   * * Returns mean, median, population variance and population standard deviation.
   *
   * @param count requested observation count or output sample-frame count
   * @return mean, median, population variance and population standard deviation
   */
  public double[] statistics(int count) {
    count = Math.max(1, Math.min(data.length, count));
    double[] sorted = Arrays.copyOf(data, count);
    Arrays.sort(sorted);
    double mean = Arrays.stream(sorted).average().orElseThrow(), variance = 0;
    for (double d : sorted) variance += (d - mean) * (d - mean);
    variance /= count;
    return new double[] {
      mean, (sorted[(count - 1) / 2] + sorted[count / 2]) / 2, variance, Math.sqrt(variance)
    };
  }

  /**
   * Renders axes, observations and mathematically consistent summary labels.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param c scene-local clock, active theme and render settings
   */
  @Override
  protected void draw(Graphics2D g, RenderContext c) {
    g.clip(bounds(c.time()));
    int n = (int) Math.max(1, Math.min(data.length, Math.round(value(Property.PARAM_A, c.time()))));
    double[] stats = statistics(n);
    double w = value(Property.WIDTH, c.time()),
        h = value(Property.HEIGHT, c.time()),
        bottom = h - 90,
        top = 40;
    double min = Arrays.stream(data).min().orElse(0), max = Arrays.stream(data).max().orElse(1);
    if (max == min) {
      max += 1;
      min -= 1;
    }
    g.setColor(c.theme().secondary());
    g.setStroke(new BasicStroke(1));
    g.draw(new Line2D.Double(40, bottom, w - 40, bottom));
    if (mode.equals("histogram") || mode.equals("normal")) {
      int bins = Math.max(4, (int) Math.ceil(Math.sqrt(data.length)));
      int[] counts = new int[bins];
      for (int i = 0; i < n; i++)
        counts[Math.min(bins - 1, (int) ((data[i] - min) / (max - min) * bins))]++;
      int peak = Math.max(1, Arrays.stream(counts).max().orElse(1));
      double dx = (w - 100) / bins;
      for (int i = 0; i < bins; i++) {
        double bh = (bottom - top) * counts[i] / peak;
        g.setColor(new Color(50, 210, 255, 100));
        g.fill(new Rectangle2D.Double(50 + i * dx, bottom - bh, dx - 2, bh));
      }
      if (mode.equals("normal") && stats[3] > 1e-9) {
        var path = new Path2D.Double();
        for (int i = 0; i <= 300; i++) {
          double x = min + (max - min) * i / 300,
              density =
                  Math.exp(-.5 * Math.pow((x - stats[0]) / stats[3], 2))
                      / (stats[3] * Math.sqrt(2 * Math.PI));
          double y = bottom - density * n * (max - min) / bins / peak * (bottom - top);
          if (i == 0) path.moveTo(50 + (w - 100) * i / 300, y);
          else path.lineTo(50 + (w - 100) * i / 300, y);
        }
        g.setColor(c.theme().success());
        g.setStroke(new BasicStroke(3));
        g.draw(path);
      }
    } else {
      double lower = Math.min(0, min), upper = Math.max(0, max);
      double zero = bottom - (0 - lower) / (upper - lower) * (bottom - top),
          dx = (w - 100) / data.length;
      for (int i = 0; i < n; i++) {
        double y = bottom - (data[i] - lower) / (upper - lower) * (bottom - top);
        g.setColor(c.theme().primary());
        if (mode.equals("scatter"))
          g.fill(new Ellipse2D.Double(50 + (i + .5) * dx - 5, y - 5, 10, 10));
        else
          g.fill(
              new Rectangle2D.Double(
                  50 + i * dx, Math.min(y, zero), Math.max(1, dx - 5), Math.abs(zero - y)));
      }
    }
    g.setFont(new Font(c.theme().font(), Font.PLAIN, 21));
    g.setColor(c.theme().foreground());
    TextRenderer.draw(
        g,
        String.format(
            Locale.ROOT,
            "n = %d    mean %.2f    median %.2f    variance %.2f    σ %.2f",
            n,
            stats[0],
            stats[1],
            stats[2],
            stats[3]),
        (float) (w / 2),
        (float) (h - 45),
        "center");
    TextRenderer.draw(
        g,
        String.format(
            Locale.ROOT,
            "Approx. 95%% mean interval: [%.2f, %.2f]",
            stats[0] - 1.96 * stats[3] / Math.sqrt(n),
            stats[0] + 1.96 * stats[3] / Math.sqrt(n)),
        (float) (w / 2),
        (float) (h - 14),
        "center");
  }
}
