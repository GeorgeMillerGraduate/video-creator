/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.production;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * Engine events delivered on the production worker. Listeners must return quickly and never mutate
 * frames.
 */
public interface ProductionListener {
  /** Reports an actual pipeline transition, without inventing overall stage percentages. */
  default void stage(String name) {}

  /** Reports a completed immutable frame; consumers should retain at most a bounded sample. */
  default void frame(BufferedImage image, int completed, int total, double time, double elapsed) {}

  /** Reports actual chapter counters alongside global frame progress. */
  default void part(PartProgress progress) {}

  /** Reports cache outcome and measured duration for a narration cue. */
  default void speech(String id, boolean cached, double seconds) {}

  /** Reports a diagnostic with a category suitable for filtering. */
  default void message(String category, String text) {}

  /** Reports a successfully published output. */
  default void completed(Path output) {}
}
