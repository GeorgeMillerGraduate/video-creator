/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.audio;

/**
 * Narration window on the global project clock. XML scene-local times are resolved by the parser.
 * maxEnd is a strict upper bound, not a request to stretch speech.
 */
public record NarrationCue(
    String id, double start, double maxEnd, String text, VoiceSettings voice) {
  /**
   * Rejects empty scripts, invalid intervals and missing settings before synthesis.
   *
   * @param id stable nonblank identity, unique in its owning namespace
   * @param start nonnegative interval start in scene-local seconds (global seconds for audio cues)
   * @param maxEnd latest permitted global completion time for the measured narration
   * @param text plain text or SSML when explicitly enabled
   * @param voice language, exact voice name, speed, pitch and SSML selection
   */
  public NarrationCue {
    if (id == null
        || id.isBlank()
        || text == null
        || text.isBlank()
        || voice == null
        || !Double.isFinite(start + maxEnd)
        || start < 0
        || maxEnd <= start) throw new IllegalArgumentException("Invalid narration cue");
  }
}
