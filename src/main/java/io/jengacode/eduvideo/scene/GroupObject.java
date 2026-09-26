/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.scene;

import io.jengacode.eduvideo.video.RenderContext;
import java.awt.Graphics2D;
import java.util.*;

/**
 * Nested local coordinate system with inherited opacity and stable z ordering.
 *
 * <p>Enforces single ownership and rejects cycles. Children render in stable z-order inside the
 * group transform; inherited opacity composes multiplicatively.
 */
public class GroupObject extends SceneObject {

  private final List<SceneObject> children = new ArrayList<>();

  /**
   * Unions transformed child bounds in this group's local coordinate system.
   *
   * @param time scene-local sample time
   * @return axis-aligned bounds, or the configured rectangle for an empty group
   */
  @Override
  public java.awt.geom.Rectangle2D bounds(double time) {
    java.awt.geom.Rectangle2D result = null;
    for (var child : children) {
      var transform = new java.awt.geom.AffineTransform();
      transform.translate(
          child.value(io.jengacode.eduvideo.animation.Property.X, time),
          child.value(io.jengacode.eduvideo.animation.Property.Y, time));
      transform.rotate(
          Math.toRadians(child.value(io.jengacode.eduvideo.animation.Property.ROTATION, time)));
      transform.scale(
          child.value(io.jengacode.eduvideo.animation.Property.SCALE_X, time),
          child.value(io.jengacode.eduvideo.animation.Property.SCALE_Y, time));
      var b = transform.createTransformedShape(child.bounds(time)).getBounds2D();
      if (result == null) result = b;
      else result.add(b);
    }
    return result == null ? super.bounds(time) : result;
  }

  /**
   * Creates a configured GroupObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   */
  public GroupObject(String id) {
    super(id);
  }

  /**
   * Attaches an unparented child, rejecting self-parenting and group cycles.
   *
   * @param child unparented scene object to attach
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public GroupObject add(SceneObject child) {
    if (child == this || child.parent() != null)
      throw new IllegalArgumentException("Object already parented or self-parent");
    for (GroupObject p = this; p != null; p = p.parent())
      if (p == child) throw new IllegalArgumentException("Cyclic group");
    child.parent(this);
    children.add(child);
    return this;
  }

  /**
   * Returns children for this configured component.
   *
   * @return unmodifiable snapshot of the configured contents
   */
  public List<SceneObject> children() {
    return List.copyOf(children);
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    children.stream().sorted(Comparator.comparingInt(SceneObject::z)).forEach(o -> o.render(g, c));
  }
}
