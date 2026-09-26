/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

import io.jengacode.eduvideo.scene.Anchor;

/**
 * Deterministic camera pan track following a live semantic anchor. No previous-frame velocity is
 * stored, so scrubbing and offline rendering produce the same position.
 */
public record CameraFollow(
    Property property, Anchor anchor, double centre, double start, double end)
    implements Animation {
  /**
   * Accepts X or Y only and requires a positive finite scene interval.
   *
   * @param property property driven by this track
   * @param anchor validated semantic anchor or local anchor name
   * @param centre logical viewport centre along the controlled camera axis
   * @param start nonnegative interval start in scene-local seconds (global seconds for audio cues)
   * @param end exclusive interval end in seconds
   */
  public CameraFollow {
    if ((property != Property.X && property != Property.Y)
        || anchor == null
        || !Double.isFinite(centre + start + end)
        || start < 0
        || end <= start) throw new IllegalArgumentException("Invalid camera follow");
  }

  /**
   * * * Samples the anchor's world position at a clamped scene time and subtracts the viewport
   * centre.
   *
   * @param time sample time in scene-local seconds
   * @return the requested property value at the clamped sample time
   */
  @Override
  public double valueAt(double time) {
    var p = anchor.at(Math.max(start, Math.min(end, time)));
    return (property == Property.X ? p.getX() : p.getY()) - centre;
  }
}
