/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.audio;

/** Mixing policy: 48 kHz stereo PCM intermediate, AAC delivery and smooth music ducking. */
public record AudioSettings(
    int sampleRate, double duckGain, double attack, double release, double mergeGap) {
  /**
   * Validates gains and envelope durations.
   *
   * @param sampleRate PCM output sample rate in samples per second
   * @param duckGain music gain multiplier while narration is active
   * @param attack positive duck-envelope attack in seconds
   * @param release positive duck-envelope release in seconds
   * @param mergeGap largest speech gap merged into one duck interval, in seconds
   */
  public AudioSettings {
    if (sampleRate < 8000
        || sampleRate > 192000
        || !Double.isFinite(duckGain + attack + release + mergeGap)
        || duckGain < 0
        || duckGain > 1
        || attack <= 0
        || release <= 0
        || mergeGap < 0) throw new IllegalArgumentException("Invalid audio settings");
  }

  /**
   * Returns conservative music-under-speech defaults.
   *
   * @return the documented default configuration
   */
  public static AudioSettings defaults() {
    return new AudioSettings(48000, .22, .25, .6, .35);
  }
}
