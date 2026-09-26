/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.animation.Easing;
import io.jengacode.eduvideo.render.TextRenderer;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * Structured equation with stable token identities. Matching tokens move continuously; removed
 * tokens shrink away and new tokens grow in. This is a presentation model, not a CAS.
 */
public final class TokenEquation extends SceneObject {
  /** One semantic token; identities must be unique within a state. */
  public record Term(String id, String text) {
    /**
     * Requires nonempty identities and labels.
     *
     * @param id stable nonblank identity, unique in its owning namespace
     * @param text plain text or SSML when explicitly enabled
     */
    public Term {
      if (id.isBlank() || text.isEmpty()) throw new IllegalArgumentException("Empty equation term");
    }
  }

  /** Immutable timed destination of a matched transformation. */
  private record Change(double start, double end, List<Term> before, List<Term> after) {}

  private final List<Term> initial;
  private final List<Change> changes = new ArrayList<>();
  private final double size, spacing;

  /**
   * * Creates a centred equation with fixed token slots, suitable for deliberate authored spacing.
   *
   * @param id stable nonblank identity, unique in its owning namespace
   * @param terms destination token state with unique identities
   * @param size positive font size in logical pixels
   * @param spacing distance between neighbouring token centres
   */
  public TokenEquation(String id, List<Term> terms, double size, double spacing) {
    super(id);
    initial = checked(terms);
    if (size <= 0 || spacing <= 0) throw new IllegalArgumentException("Invalid equation metrics");
    this.size = size;
    this.spacing = spacing;
  }

  /**
   * * Parses semicolon-separated identity=label pairs, preserving equals signs in labels.
   *
   * @param specification semicolon-separated identity=label token definitions
   * @return parsed immutable token sequence
   */
  public static List<Term> parse(String specification) {
    return Arrays.stream(specification.split(";"))
        .map(
            s -> {
              var p = s.strip().split("=", 2);
              if (p.length != 2) throw new IllegalArgumentException("Expected id=label");
              return new Term(p[0], p[1]);
            })
        .toList();
  }

  /**
   * * Rejects ambiguous token identities and returns an immutable state.
   *
   * @param terms destination token state with unique identities
   * @return validated immutable token state
   */
  private static List<Term> checked(List<Term> terms) {
    var ids = new HashSet<String>();
    for (var t : terms)
      if (!ids.add(t.id())) throw new IllegalArgumentException("Duplicate term " + t.id());
    return List.copyOf(terms);
  }

  /**
   * * Adds a nonoverlapping transformation; edits must be configured in chronological order.
   *
   * @param terms destination token state with unique identities
   * @param start nonnegative interval start in scene-local seconds (global seconds for audio cues)
   * @param duration positive interval length in seconds
   * @return Adds a nonoverlapping transformation; edits must be configured in chronological order
   */
  public TokenEquation transform(List<Term> terms, double start, double duration) {
    if (start < 0
        || duration <= 0
        || !Double.isFinite(start + duration)
        || (!changes.isEmpty() && start < changes.get(changes.size() - 1).end))
      throw new IllegalArgumentException("Equation transforms overlap or have invalid timing");
    changes.add(
        new Change(
            start,
            start + duration,
            changes.isEmpty() ? initial : changes.get(changes.size() - 1).after,
            checked(terms)));
    return this;
  }

  /**
   * Returns the final transformation boundary.
   *
   * @return exclusive end time in scene-local seconds
   */
  public double end() {
    return changes.isEmpty() ? 0 : changes.get(changes.size() - 1).end;
  }

  /**
   * * Computes a token's centred slot coordinate.
   *
   * @param i zero-based token index
   * @param count requested observation count or output sample-frame count
   * @return centred horizontal slot coordinate
   */
  private double x(int i, int count) {
    return (i - (count - 1) * .5) * spacing;
  }

  /**
   * Returns conservative baseline-relative bounds of the current token layout.
   *
   * @param time scene-local sample time
   * @return local token-row rectangle, including glyph ascenders and descenders
   */
  @Override
  public Rectangle2D bounds(double time) {
    int count = initial.size();
    for (var change : changes) {
      if (time < change.start) break;
      count =
          time < change.end
              ? Math.max(change.before.size(), change.after.size())
              : change.after.size();
    }
    double width = Math.max(size, count * spacing);
    return new Rectangle2D.Double(-width / 2, -size, width, size * 1.3);
  }

  /**
   * * Resolves term:identity to its live interpolated baseline location.
   *
   * @param name local semantic anchor name supported by this object
   * @param time sample time in scene-local seconds
   * @return semantic point in this object's local coordinates
   */
  @Override
  public Point2D anchor(String name, double time) {
    if (!name.startsWith("term:")) return super.anchor(name, time);
    String id = name.substring(5);
    var state = initial;
    for (var c : changes) {
      if (time < c.start) break;
      if (time >= c.end) {
        state = c.after;
        continue;
      }
      int a = index(c.before, id), b = index(c.after, id);
      double u = Easing.SMOOTH_STEP.applyAsDouble((time - c.start) / (c.end - c.start));
      if (a >= 0 && b >= 0)
        return new Point2D.Double(x(a, c.before.size()) * (1 - u) + x(b, c.after.size()) * u, 0);
      state = b >= 0 ? c.after : c.before;
      break;
    }
    int i = index(state, id);
    if (i < 0) throw new IllegalArgumentException("Unknown term " + id);
    return new Point2D.Double(x(i, state.size()), 0);
  }

  /**
   * * Locates a semantic identity without comparing rendered labels.
   *
   * @param state ordered token state to inspect
   * @param id stable nonblank identity, unique in its owning namespace
   * @return matching token index, or -1 when absent
   */
  private static int index(List<Term> state, String id) {
    for (int i = 0; i < state.size(); i++) if (state.get(i).id.equals(id)) return i;
    return -1;
  }

  /**
   * Paints each token with independent translation, scale and opacity.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param text plain text or SSML when explicitly enabled
   * @param x horizontal coordinate in the units documented by this operation
   * @param alpha opacity multiplier in the closed interval [0,1]
   * @param scale glyph scale relative to its configured font size
   */
  private void term(Graphics2D g, String text, double x, double alpha, double scale) {
    if (alpha <= 0) return;
    var q = (Graphics2D) g.create();
    try {
      float inherited = q.getComposite() instanceof AlphaComposite a ? a.getAlpha() : 1;
      q.setComposite(AlphaComposite.SrcOver.derive((float) alpha * inherited));
      q.translate(x, 0);
      q.scale(scale, scale);
      TextRenderer.draw(q, text, 0, 0, "center");
    } finally {
      q.dispose();
    }
  }

  /**
   * Samples one transformation or holds the latest complete state.
   *
   * @param g isolated graphics context in the current local coordinate system
   * @param ctx scene-local clock, active theme and render settings
   */
  @Override
  protected void draw(Graphics2D g, RenderContext ctx) {
    g.setFont(new Font("Serif", Font.PLAIN, (int) size));
    var state = initial;
    for (var c : changes) {
      if (ctx.time() < c.start) break;
      if (ctx.time() >= c.end) {
        state = c.after;
        continue;
      }
      double u = Easing.SMOOTH_STEP.applyAsDouble((ctx.time() - c.start) / (c.end - c.start));
      for (int i = 0; i < c.before.size(); i++) {
        var a = c.before.get(i);
        int j = index(c.after, a.id);
        double px = x(i, c.before.size());
        if (j >= 0) {
          px = px * (1 - u) + x(j, c.after.size()) * u;
          if (a.text.equals(c.after.get(j).text)) term(g, a.text, px, 1, 1);
          else {
            term(g, a.text, px, 1 - u, 1 - .2 * u);
            term(g, c.after.get(j).text, px, u, .8 + .2 * u);
          }
        } else term(g, a.text, px, 1 - u, 1 - .4 * u);
      }
      for (int j = 0; j < c.after.size(); j++) {
        var b = c.after.get(j);
        if (index(c.before, b.id) < 0) term(g, b.text, x(j, c.after.size()), u, .6 + .4 * u);
      }
      return;
    }
    for (int i = 0; i < state.size(); i++) term(g, state.get(i).text, x(i, state.size()), 1, 1);
  }
}
