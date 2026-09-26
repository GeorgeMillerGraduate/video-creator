/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.algorithm;

import io.jengacode.eduvideo.animation.*;
import io.jengacode.eduvideo.render.TextRenderer;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;

/**
 * Array, list, queue or stack. Swaps are reconstructed from immutable initial labels.
 *
 * <p>Models array/list/queue/stack labels without mutating initial data during playback. Completed
 * swaps rebuild slot contents; active swaps interpolate positions with a curved lift. Overlapping
 * swaps are rejected.
 */
public final class ArrayObject extends SceneObject {
  /**
   * Immutable exchange of two zero-based array slots over a positive duration. Labels commit at the
   * end of the interval; positions interpolate while it is active.
   *
   * @param a a retained by this immutable descriptor
   * @param b b retained by this immutable descriptor
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   */
  public record Swap(int a, int b, double start, double duration) {}

  /**
   * Immutable timed highlight descriptor. Highlight windows are half-open, so adjacent events can
   * meet without ambiguity.
   *
   * @param index zero-based array slot index
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @param color display or highlight colour
   */
  public record Highlight(int index, double start, double duration, Color color) {}

  private final java.util.List<String> initial;

  private final java.util.List<Swap> swaps = new ArrayList<>();

  private final java.util.List<Highlight> highlights = new ArrayList<>();

  private boolean vertical;

  /**
   * Creates a configured ArrayObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param values nonempty initial labels
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public ArrayObject(String id, java.util.List<String> values) {
    super(id);
    if (values.isEmpty()) throw new IllegalArgumentException("Array cannot be empty");
    initial = java.util.List.copyOf(values);
    set(Property.WIDTH, 76);
    set(Property.HEIGHT, 70);
  }

  /**
   * Chooses vertical stacking instead of horizontal cells.
   *
   * @param vertical whether to lay out array cells vertically
   * @return this instance for fluent configuration
   */
  public ArrayObject vertical(boolean vertical) {
    this.vertical = vertical;
    return this;
  }

  /**
   * * Resolves element:index to the current animated slot centre.
   *
   * @param name local semantic anchor name supported by this object
   * @param time sample time in scene-local seconds
   * @return semantic point in this object's local coordinates
   */
  @Override
  public java.awt.geom.Point2D anchor(String name, double time) {
    if (!name.startsWith("element:")) return super.anchor(name, time);
    int index = Integer.parseInt(name.substring(8));
    if (index < 0 || index >= size())
      throw new IllegalArgumentException("Array index out of bounds");
    double slot = index,
        lift = 0,
        w = value(Property.WIDTH, time),
        h = value(Property.HEIGHT, time);
    for (var s : swaps)
      if (time >= s.start && time < s.start + s.duration && (index == s.a || index == s.b)) {
        double p = Easing.EASE_IN_OUT.applyAsDouble((time - s.start) / s.duration);
        slot = index + ((index == s.a ? s.b : s.a) - index) * p;
        lift = (index == s.a ? -1 : 1) * Math.sin(Math.PI * p) * (vertical ? w : h) * .85;
      }
    return new java.awt.geom.Point2D.Double(
        (vertical ? lift : slot * (w + 12)) + w / 2, (vertical ? slot * (h + 12) : lift) + h / 2);
  }

  /**
   * Returns the number of stored array slots.
   *
   * @return the number of stored array slots
   */
  public int size() {
    return initial.size();
  }

  /**
   * Rejects array indices outside the stored label list.
   *
   * @param i zero-based array slot index
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  private void index(int i) {
    if (i < 0 || i >= initial.size())
      throw new IllegalArgumentException("Array index out of bounds: " + i);
  }

  /**
   * Schedules a non-overlapping exchange between distinct array slots.
   *
   * @param a zero-based first slot in the swap
   * @param b zero-based second slot, distinct from a
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public ArrayObject swap(int a, int b, double start, double duration) {
    index(a);
    index(b);
    if (a == b) throw new IllegalArgumentException("Swap indices must differ");
    Checks.nonnegative(start, "swap start");
    Checks.positive(duration, "swap duration");
    for (var s : swaps)
      if (start < s.start + s.duration && s.start < start + duration)
        throw new IllegalArgumentException("Overlapping swaps on " + id());
    swaps.add(new Swap(a, b, start, duration));
    swaps.sort(Comparator.comparingDouble(Swap::start));
    return this;
  }

  /**
   * Adds a timed highlight to a zero-based array slot.
   *
   * @param index zero-based array slot index
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @param color display or highlight colour
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public ArrayObject highlight(int index, double start, double duration, Color color) {
    index(index);
    Checks.nonnegative(start, "highlight start");
    Checks.positive(duration, "highlight duration");
    highlights.add(new Highlight(index, start, duration, color));
    return this;
  }

  /**
   * Returns the latest semantic event endpoint in scene-local seconds for scene validation.
   *
   * @return latest semantic event time in seconds, or zero when none exist
   */
  public double eventEnd() {
    return Math.max(
        swaps.stream().mapToDouble(s -> s.start + s.duration).max().orElse(0),
        highlights.stream().mapToDouble(h -> h.start + h.duration).max().orElse(0));
  }

  /**
   * Reconstructs labels by applying only swaps completed by the requested time.
   *
   * @param t sample time in scene-local seconds
   * @return immutable snapshot of labels at the requested time
   */
  public java.util.List<String> valuesAt(double t) {
    var values = new ArrayList<>(initial);
    for (var s : swaps) if (t >= s.start + s.duration) Collections.swap(values, s.a, s.b);
    return java.util.List.copyOf(values);
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    double t = c.time(),
        w = Math.max(1, value(Property.WIDTH, t)),
        h = Math.max(1, value(Property.HEIGHT, t)),
        gap = 12;
    var values = valuesAt(t);
    Swap active =
        swaps.stream()
            .filter(s -> t >= s.start && t < s.start + s.duration)
            .findFirst()
            .orElse(null);
    g.setFont(FontManager.get("Monospaced", Font.BOLD, 28));
    for (int i = 0; i < values.size(); i++) {
      double slot = i, lift = 0;
      if (active != null && (i == active.a || i == active.b)) {
        double p = Easing.EASE_IN_OUT.applyAsDouble((t - active.start) / active.duration);
        int destination = i == active.a ? active.b : active.a;
        slot = i + (destination - i) * p;
        lift = (i == active.a ? -1 : 1) * Math.sin(Math.PI * p) * (vertical ? w : h) * .85;
      }
      double x = vertical ? lift : slot * (w + gap), y = vertical ? slot * (h + gap) : lift;
      Color fill = c.theme().panel();
      for (var mark : highlights)
        if (mark.index == i && t >= mark.start && t < mark.start + mark.duration) fill = mark.color;
      io.jengacode.eduvideo.render.PanelRenderer.draw(
          g,
          x,
          y,
          w,
          h,
          12,
          io.jengacode.eduvideo.render.GlowRenderer.alpha(fill, .6),
          c.theme().panel(),
          color(t),
          1.4,
          value(Property.GLOW, t),
          true);
      g.setColor(c.theme().foreground());
      TextRenderer.draw(g, values.get(i), (float) (x + w / 2), (float) (y + h / 2 + 10), "center");
    }
    g.setFont(FontManager.get("Monospaced", Font.PLAIN, 15));
    g.setColor(c.theme().secondary());
    for (int i = 0; i < values.size(); i++)
      TextRenderer.draw(
          g,
          Integer.toString(i),
          (float) (vertical ? -20 : i * (w + gap) + w / 2),
          (float) (vertical ? i * (h + gap) + h / 2 : h + 25),
          "center");
  }
}
