/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.production;

/** Measured frame-stage statistics. ETA is withheld until at least one frame has completed. */
public record ProductionProgress(int completed, int total, double elapsed) {
  /** Rejects impossible counters and non-finite elapsed durations. */
  public ProductionProgress {
    if (total <= 0
        || completed < 0
        || completed > total
        || !Double.isFinite(elapsed)
        || elapsed < 0) throw new IllegalArgumentException("Invalid production statistics");
  }

  /**
   * Fraction of video frames actually submitted to the encoder, not an overall pipeline estimate.
   */
  public double fraction() {
    return (double) completed / total;
  }

  /** Mean completed-frame throughput since visual rendering began. */
  public double fps() {
    return elapsed > 0 ? completed / elapsed : 0;
  }

  /** Estimated remaining frame-stage seconds; NaN denotes insufficient observations. */
  public double remaining() {
    return completed == total ? 0 : fps() > 0 ? (total - completed) / fps() : Double.NaN;
  }
}
