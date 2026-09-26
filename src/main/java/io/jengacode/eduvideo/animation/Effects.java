/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

import io.jengacode.eduvideo.scene.SceneObject;

/**
 * Convenience effects backed exclusively by the existing non-overlapping property timeline.
 *
 * <p>Configures normal property tracks before rendering; it does not create a second clock. Effects
 * on the same property must respect Timeline overlap rules. Pop-in scales around the local origin.
 */
public final class Effects {
  /** Prevents instantiation of this static utility. */
  private Effects() {}

  /**
   * Animates opacity from zero to one using ease-out timing.
   *
   * @param o scene object receiving the effect tracks
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @return animates opacity from zero to one using ease-out timing
   */
  public static SceneObject fadeIn(SceneObject o, double start, double duration) {
    return o.animate(Property.OPACITY, 0, 1, start, duration, Easing.EASE_OUT);
  }

  /**
   * Animates opacity from one to zero using ease-in timing.
   *
   * @param o scene object receiving the effect tracks
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @return animates opacity from one to zero using ease-in timing
   */
  public static SceneObject fadeOut(SceneObject o, double start, double duration) {
    return o.animate(Property.OPACITY, 1, 0, start, duration, Easing.EASE_IN);
  }

  /**
   * Reveals the object outline with smooth-step progress.
   *
   * @param o scene object receiving the effect tracks
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @return reveals the object outline with smooth-step progress
   */
  public static SceneObject drawOn(SceneObject o, double start, double duration) {
    return o.animate(Property.DRAW_PROGRESS, 0, 1, start, duration, Easing.SMOOTH_STEP);
  }

  /**
   * Adds opacity and scale entrance tracks around the local origin.
   *
   * @param o scene object receiving the effect tracks
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @return adds opacity and scale entrance tracks around the local origin
   */
  public static SceneObject popIn(SceneObject o, double start, double duration) {
    fadeIn(o, start, duration);
    o.animate(Property.SCALE_X, .92, 1, start, duration, Easing.EASE_OUT);
    return o.animate(Property.SCALE_Y, .92, 1, start, duration, Easing.EASE_OUT);
  }

  /**
   * Moves horizontally from an offset position while fading in.
   *
   * @param o scene object receiving the effect tracks
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @param distance horizontal entrance offset in local pixels
   * @return moves horizontally from an offset position while fading in
   */
  public static SceneObject slideIn(SceneObject o, double start, double duration, double distance) {
    double end = o.value(Property.X, 0);
    fadeIn(o, start, duration);
    return o.animate(Property.X, end + distance, end, start, duration, Easing.EASE_OUT);
  }

  /**
   * Raises and lowers glow intensity using three keyframes.
   *
   * @param o scene object receiving the effect tracks
   * @param start nonnegative start time in scene-local seconds
   * @param duration positive interval length in seconds
   * @return raises and lowers glow intensity using three keyframes
   */
  public static SceneObject glowPulse(SceneObject o, double start, double duration) {
    return o.animate(
        new PropertyAnimation(
            Property.GLOW,
            java.util.List.of(
                new Keyframe(start, 0),
                new Keyframe(start + duration / 2, 1, Easing.SMOOTH_STEP),
                new Keyframe(start + duration, 0, Easing.SMOOTH_STEP))));
  }
}
