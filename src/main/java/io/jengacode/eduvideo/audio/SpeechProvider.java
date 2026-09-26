/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.audio;

import java.io.IOException;

/** Injectable narration boundary. Unit tests can supply WAV fixtures without network access. */
public interface SpeechProvider {
  /**
   * Stable provider/version identity included in cache keys.
   *
   * @return stable provider identity included in speech cache keys
   */
  String id();

  /** Resolves a default voice before hashing and synthesis; fixtures may use settings unchanged. */
  default VoiceSettings resolve(VoiceSettings requested) throws IOException {
    return requested;
  }

  /**
   * * Returns complete PCM WAV bytes, including a WAV header; never an MP3 payload.
   *
   * @param text plain text or SSML when explicitly enabled
   * @param voice language, exact voice name, speed, pitch and SSML selection
   * @return complete signed PCM WAV bytes, including the container header
   */
  byte[] synthesize(String text, VoiceSettings voice) throws IOException;
}
