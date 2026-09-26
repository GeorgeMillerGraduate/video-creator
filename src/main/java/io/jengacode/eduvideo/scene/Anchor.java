/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.scene;

import java.awt.geom.Point2D;

/** A validated semantic connection to a scene object, evaluated at the requested scene time. */
public record Anchor(SceneObject object, String name) {
  /**
   * Rejects null objects and unsupported anchor names before a render starts.
   *
   * @param object scene object whose geometry is sampled
   * @param name local semantic anchor name supported by this object
   */
  public Anchor {
    java.util.Objects.requireNonNull(object);
    object.anchor(name, 0);
  }

  /**
   * * Samples the connection in scene coordinates.
   *
   * @param time sample time in scene-local seconds
   * @return Samples the connection in scene coordinates
   */
  public Point2D at(double time) {
    return object.worldAnchor(name, time);
  }

  /**
   * * Resolves object@anchor notation; a bare identity selects its centre.
   *
   * @param scene owning scene with a validated object namespace
   * @param reference object@anchor reference; a bare identity selects centre
   * @return validated semantic reference or measured cache result, as described above
   */
  public static Anchor resolve(Scene scene, String reference) {
    String[] p = reference.split("@", 2);
    return new Anchor(scene.find(p[0]), p.length == 1 ? "center" : p[1]);
  }
}
