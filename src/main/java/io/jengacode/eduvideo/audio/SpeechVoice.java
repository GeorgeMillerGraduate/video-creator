/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.audio;

/** A documented OpenAI voice identity used consistently by the GUI and CLI. */
public record SpeechVoice(String id, String language, String name, String provider) {
  /** Displays the engine's real voice identity and language in selectors. */
  @Override
  public String toString() {
    return name;
  }
}
