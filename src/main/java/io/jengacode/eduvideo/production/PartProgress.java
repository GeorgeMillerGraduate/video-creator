/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.production;

/** Actual chapter frame counters and both clocks, emitted alongside the global frame counter. */
public record PartProgress(
    int index,
    int count,
    String title,
    int completed,
    int total,
    double localTime,
    double productionTime) {
  /** Fraction of this chapter's requested frames submitted to the encoder. */
  public double fraction() {
    return total == 0 ? 0 : (double) completed / total;
  }
}
