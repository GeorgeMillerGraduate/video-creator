/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.video;

import io.jengacode.eduvideo.scene.Scene;
import java.awt.geom.AffineTransform;

/**
 * Per-frame values; time is local to the active scene.
 *
 * <p>Carries output settings, active scene palette and the scene-local sample time into each
 * object. Camera transforms are copied both on construction and access to prevent shared mutable
 * state.
 *
 * @param frame zero-based global output frame index
 * @param time sample or event time in scene-local seconds
 * @param settings validated video output dimensions and frame rate
 * @param theme active semantic palette
 * @param scene owning scene with its object namespace
 * @param cameraTransform camera transform, defensively copied
 */
public record RenderContext(
    int frame,
    double time,
    VideoSettings settings,
    Theme theme,
    Scene scene,
    AffineTransform cameraTransform) {
  /**
   * Creates a configured RenderContext instance.
   *
   * @param frame zero-based global frame index
   * @param time scene-local sample time in seconds
   * @param settings validated video output dimensions and frame rate
   * @param theme active semantic palette
   * @param scene owning scene with its object namespace
   * @param cameraTransform camera transform, defensively copied
   */
  public RenderContext {
    cameraTransform = new AffineTransform(cameraTransform);
  }

  /**
   * Returns camera transform for this configured component.
   *
   * @return a defensive copy of the camera transform
   */
  @Override
  public AffineTransform cameraTransform() {
    return new AffineTransform(cameraTransform);
  }
}
