/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.audio;

/**
 * Immutable speech synthesis settings. Credentials are deliberately absent from this model. The
 * OpenAI provider validates supported controls.
 */
public record VoiceSettings(String language, String name, double rate, double pitch, boolean ssml) {
  /**
   * Rejects missing voice identities and settings outside the supported numeric ranges.
   *
   * @param language BCP-47 language code such as en-GB
   * @param name OpenAI voice ID, or default
   * @param rate speech speed multiplier in the inclusive range 0.25 to 4.0
   * @param pitch voice pitch offset in semitones, from -20 through 20
   * @param ssml whether the script is SSML instead of plain text
   */
  public VoiceSettings {
    if (language == null
        || language.isBlank()
        || name == null
        || name.isBlank()
        || !Double.isFinite(rate + pitch)
        || rate < .25
        || rate > 4
        || pitch < -20
        || pitch > 20) throw new IllegalArgumentException("Invalid voice settings");
  }

  /**
   * Returns the British English voice used by the production lessons.
   *
   * @return the documented default configuration
   */
  public static VoiceSettings defaults() {
    return new VoiceSettings("en-GB", "default", 1, 0, false);
  }
}
