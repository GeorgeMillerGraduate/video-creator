/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.audio;

import java.util.List;
import java.util.Set;

/** Official built-in Speech API catalogue, verified against OpenAI documentation on 2026-09-25. */
public final class OpenAIVoices {
  public static final String DEFAULT_MODEL = "gpt-4o-mini-tts";
  public static final String DEFAULT_VOICE = "marin";
  public static final List<String> MODELS =
      List.of(DEFAULT_MODEL, "gpt-4o-mini-tts-2025-12-15", "tts-1", "tts-1-hd");
  private static final List<String> IDS =
      List.of(
          "alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer",
          "verse", "marin", "cedar");
  private static final Set<String> LEGACY =
      Set.of("alloy", "ash", "coral", "echo", "fable", "onyx", "nova", "sage", "shimmer");

  /** Static catalogue only. */
  private OpenAIVoices() {}

  /** Returns the official voice subset for the selected model; no API key or discovery needed. */
  public static List<SpeechVoice> voices(String model) {
    if (!MODELS.contains(model))
      throw new IllegalArgumentException(
          "Unsupported OpenAI speech model. Select one in Preferences > Narration.");
    return IDS.stream()
        .filter(id -> !model.startsWith("tts-") || LEGACY.contains(id))
        .map(
            id ->
                new SpeechVoice(
                    id,
                    "Multilingual",
                    Character.toUpperCase(id.charAt(0)) + id.substring(1),
                    "OpenAI"))
        .toList();
  }
}
