/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

/**
 * Pure time-sampled animation contract.
 *
 * <p>Implementations expose their property and scene-local interval and must sample without mutable
 * playback state. This permits rendering frames out of order and scrubbing backward.
 */
public interface Animation {
  /**
   * Returns property for this configured component.
   *
   * @return property for this configured component
   */
  Property property();

  /**
   * Returns start for this configured component.
   *
   * @return time or duration in seconds
   */
  double start();

  /**
   * Returns end for this configured component.
   *
   * @return time or duration in seconds
   */
  double end();

  /**
   * Samples the animation at scene-local time without changing playback state.
   *
   * @param time sample or event time in scene-local seconds
   * @return samples the animation at scene-local time without changing playback state
   */
  double valueAt(double time);
}
