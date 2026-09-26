/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.video;

import io.jengacode.eduvideo.util.Checks;
import java.nio.file.Path;

/**
 * Future audio mixing metadata. Rendering and current MP4 export are silent.
 *
 * <p>Stores a source path, scene-independent project start time and nonnegative volume for future
 * mixing. Current exporters retain metadata but produce silent video; no speech synthesis is
 * performed.
 *
 * @param source expression source text or audio source path, according to the overload
 * @param start nonnegative start time in scene-local seconds
 * @param volume nonnegative audio gain metadata
 */
public record AudioCue(Path source, double start, double volume) {
  /**
   * Creates a configured AudioCue instance.
   *
   * @param source expression source text or audio source path, according to the overload
   * @param start nonnegative start time in scene-local seconds
   * @param volume nonnegative audio gain metadata
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public AudioCue {
    java.util.Objects.requireNonNull(source);
    Checks.nonnegative(start, "audio start");
    Checks.nonnegative(volume, "audio volume");
  }
}
