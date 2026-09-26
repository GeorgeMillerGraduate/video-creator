/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.scene;

import io.jengacode.eduvideo.animation.*;
import io.jengacode.eduvideo.util.Checks;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.util.*;

/**
 * Base visual object. Configure before rendering; sampling never mutates properties.
 *
 * <p>Owns identity, base properties, timeline, parent and z-order. Rendering creates an isolated
 * Graphics2D context, combines inherited opacity and applies local translation, rotation and scale
 * before drawing. Configure instances before starting a render.
 */
public abstract class SceneObject {

  private final String id;

  private final EnumMap<Property, Double> base = new EnumMap<>(Property.class);

  private final Timeline timeline = new Timeline();

  private GroupObject parent;

  private int z;

  private boolean visible = true;
  private final java.util.List<double[]> focusIntervals = new ArrayList<>();

  /**
   * * Dims this object during a half-open interval with smooth 200 ms edges.
   *
   * @param start nonnegative interval start in scene-local seconds (global seconds for audio cues)
   * @param end exclusive interval end in seconds
   * @param opacity dimmed opacity multiplier in [0,1]
   * @return Dims this object during a half-open interval with smooth 200 ms edges
   */
  public SceneObject dim(double start, double end, double opacity) {
    if (!Double.isFinite(start + end + opacity)
        || start < 0
        || end <= start
        || opacity < 0
        || opacity > 1) throw new IllegalArgumentException("Invalid focus interval");
    focusIntervals.add(new double[] {start, end, opacity});
    return this;
  }

  /**
   * Returns the final focus interval boundary for scene validation.
   *
   * @return latest configured focus interval endpoint, or zero
   */
  public double focusEnd() {
    return focusIntervals.stream().mapToDouble(f -> f[1]).max().orElse(0);
  }

  /**
   * * Returns local rectangular bounds; specialised shapes override their natural origin.
   *
   * @param time sample time in scene-local seconds
   * @return axis-aligned local bounds at the requested time
   */
  public java.awt.geom.Rectangle2D bounds(double time) {
    return new java.awt.geom.Rectangle2D.Double(
        0, 0, value(Property.WIDTH, time), value(Property.HEIGHT, time));
  }

  /**
   * * Resolves a named local anchor. Coordinates precede this object's transform.
   *
   * @param name local semantic anchor name supported by this object
   * @param time sample time in scene-local seconds
   * @return semantic point in this object's local coordinates
   */
  public java.awt.geom.Point2D anchor(String name, double time) {
    var b = bounds(time);
    return switch (name) {
      case "center" -> new java.awt.geom.Point2D.Double(b.getCenterX(), b.getCenterY());
      case "left" -> new java.awt.geom.Point2D.Double(b.getMinX(), b.getCenterY());
      case "right" -> new java.awt.geom.Point2D.Double(b.getMaxX(), b.getCenterY());
      case "top" -> new java.awt.geom.Point2D.Double(b.getCenterX(), b.getMinY());
      case "bottom" -> new java.awt.geom.Point2D.Double(b.getCenterX(), b.getMaxY());
      case "origin", "baseline" -> new java.awt.geom.Point2D.Double();
      default -> throw new IllegalArgumentException(id + ": unknown anchor " + name);
    };
  }

  /**
   * * Returns this object's complete local-to-scene transform, including panel content insets.
   *
   * @param time sample time in scene-local seconds
   * @return complete local-to-scene affine transform
   */
  public java.awt.geom.AffineTransform worldTransform(double time) {
    var a = parent == null ? new java.awt.geom.AffineTransform() : parent.worldTransform(time);
    if (parent instanceof io.jengacode.eduvideo.objects.PanelObject p)
      a.translate(p.padding(), p.padding());
    a.translate(value(Property.X, time), value(Property.Y, time));
    a.rotate(Math.toRadians(value(Property.ROTATION, time)));
    a.scale(value(Property.SCALE_X, time), value(Property.SCALE_Y, time));
    return a;
  }

  /**
   * * Resolves an anchor in scene coordinates, independent of camera and output resolution.
   *
   * @param name local semantic anchor name supported by this object
   * @param time sample time in scene-local seconds
   * @return semantic point in scene coordinates before the camera transform
   */
  public java.awt.geom.Point2D worldAnchor(String name, double time) {
    return worldTransform(time).transform(anchor(name, time), null);
  }

  /**
   * Creates a configured SceneObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  protected SceneObject(String id) {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Object id is required");
    this.id = id;
  }

  /**
   * Returns id for this configured component.
   *
   * @return id for this configured component
   */
  public final String id() {
    return id;
  }

  /**
   * Returns or internally assigns the single owning group.
   *
   * @return or internally assigns the single owning group
   */
  public GroupObject parent() {
    return parent;
  }

  /**
   * Returns or internally assigns the single owning group.
   *
   * @param p animated property (or owning group for the internal parent setter)
   */
  void parent(GroupObject p) {
    parent = p;
  }

  /**
   * Returns or configures stable sibling drawing order.
   *
   * @return or configures stable sibling drawing order
   */
  public int z() {
    return z;
  }

  /**
   * Returns or configures stable sibling drawing order.
   *
   * @param z sibling draw order; lower values render first
   * @return this instance for fluent configuration
   */
  public SceneObject z(int z) {
    this.z = z;
    return this;
  }

  /**
   * Enables or disables rendering of this object and its descendants.
   *
   * @param v whether the object should render
   * @return this instance for fluent configuration
   */
  public SceneObject visible(boolean v) {
    visible = v;
    return this;
  }

  /**
   * Reports whether this object accepts the requested animated property.
   *
   * @param p animated property (or owning group for the internal parent setter)
   * @return true when the documented condition holds; false otherwise
   */
  public boolean supports(Property p) {
    return p != Property.ANGLE_HIGHLIGHT
        && p != Property.ZOOM
        && p != Property.PARAM_A
        && p != Property.PARAM_B
        && p != Property.PARAM_C
        && p != Property.REVEAL
        && p != Property.HIGHLIGHT;
  }

  /**
   * Whether width/height represent signed endpoints instead of object bounds.
   *
   * <p>Reports whether width and height represent signed endpoints instead of nonnegative bounds.
   *
   * @return true when the documented condition holds; false otherwise
   */
  protected boolean signedDimensions() {
    return false;
  }

  /**
   * Sets an initial property. A track for this property takes precedence when sampled.
   *
   * <p>Sets a finite initial property value after checking object support and bounds.
   *
   * @param p animated property (or owning group for the internal parent setter)
   * @param v new property value
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public SceneObject set(Property p, double v) {
    if (!supports(p)) throw new IllegalArgumentException(id + " does not support " + p.xml);
    Checks.finite(v, p.xml);
    if ((p == Property.STROKE_WIDTH
            || (!signedDimensions() && (p == Property.WIDTH || p == Property.HEIGHT)))
        && v < 0) throw new IllegalArgumentException(p.xml + " cannot be negative");
    base.put(p, v);
    return this;
  }

  /**
   * Sets the initial local position in pixels.
   *
   * @param x local pixel x coordinate (mathematical units for plot inputs)
   * @param y local pixel y coordinate (mathematical units for plot inputs)
   * @return this instance for fluent configuration
   */
  public SceneObject at(double x, double y) {
    set(Property.X, x);
    set(Property.Y, y);
    return this;
  }

  /**
   * Sets the initial colour or samples its animated ARGB value.
   *
   * @param c display colour
   * @return this instance for fluent configuration
   */
  public SceneObject color(Color c) {
    return set(Property.COLOR, c.getRGB());
  }

  /**
   * Samples a property at scene-local time without modifying this object.
   *
   * <p>Samples one property at a scene-local time without mutating the object.
   *
   * @param p animated property (or owning group for the internal parent setter)
   * @param t sample time in scene-local seconds
   * @return samples a property at scene-local time without modifying this object
   */
  public double value(Property p, double t) {
    return timeline.value(p, base.getOrDefault(p, p.initial), t);
  }

  /**
   * Sets the initial colour or samples its animated ARGB value.
   *
   * @param t sample time in scene-local seconds
   * @return sets the initial colour or samples its animated ARGB value
   */
  public Color color(double t) {
    return new Color((int) (long) value(Property.COLOR, t), true);
  }

  /**
   * Returns timeline for this configured component.
   *
   * @return timeline for this configured component
   */
  public Timeline timeline() {
    return timeline;
  }

  /**
   * Appends a non-overlapping two-keyframe track; first/last values hold outside its interval.
   *
   * <p>Adds a validated animation to the existing property timeline.
   *
   * @param p animated property (or owning group for the internal parent setter)
   * @param from initial property value
   * @param to final property value
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @param easing curve applied to normalized segment progress
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public SceneObject animate(
      Property p, double from, double to, double start, double duration, Easing easing) {
    Checks.positive(duration, "animation duration");
    return animate(
        new PropertyAnimation(
            p,
            java.util.List.of(
                new Keyframe(start, from), new Keyframe(start + duration, to, easing))));
  }

  /**
   * Adds a validated animation to the existing property timeline.
   *
   * @param a animation track to attach
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public SceneObject animate(Animation a) {
    if (!supports(a.property()))
      throw new IllegalArgumentException(id + " does not support " + a.property().xml);
    timeline.add(a);
    return this;
  }

  /**
   * Renders in a child graphics context, composing inherited alpha and transforms.
   *
   * <p>Renders this object with isolated graphics state and composed parent opacity and transforms.
   *
   * @param graphics caller graphics context, preserved where a child context is created
   * @param c frame context with scene-local time and active theme
   */
  public final void render(Graphics2D graphics, RenderContext c) {
    if (!visible) return;
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      double t = c.time();
      float alpha = (float) Checks.clamp(value(Property.OPACITY, t));
      for (var f : focusIntervals)
        if (t >= f[0] && t < f[1]) {
          double edge = Math.min(.2, (f[1] - f[0]) / 2);
          double u = Easing.SMOOTH_STEP.applyAsDouble(Math.min(t - f[0], f[1] - t) / edge);
          alpha *= 1 - u * (1 - f[2]);
        }
      if (g.getComposite() instanceof AlphaComposite inherited) alpha *= inherited.getAlpha();
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
      g.translate(value(Property.X, t), value(Property.Y, t));
      g.rotate(Math.toRadians(value(Property.ROTATION, t)));
      g.scale(value(Property.SCALE_X, t), value(Property.SCALE_Y, t));
      g.setColor(color(t));
      g.setStroke(
          new BasicStroke(
              (float) Math.max(.01, value(Property.STROKE_WIDTH, t)),
              BasicStroke.CAP_ROUND,
              BasicStroke.JOIN_ROUND));
      draw(g, c);
    } finally {
      g.dispose();
    }
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected abstract void draw(Graphics2D g, RenderContext c);
}
