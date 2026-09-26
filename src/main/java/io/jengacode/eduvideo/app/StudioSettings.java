/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.app;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Local, non-secret preferences shared by the CLI and Studio. System properties override saved
 * values.
 */
public final class StudioSettings {
  private final Properties values = new Properties();
  private final Path file;
  private String sessionKey;
  private boolean keyRemembered;
  private final java.util.prefs.Preferences credentials =
      java.util.prefs.Preferences.userRoot()
          .node(System.getProperty("eduvideo.credentialsNode", "/io/jengacode/eduvideo/openai"));

  /** Reads settings from ~/.jenga-code/studio.properties, or eduvideo.config when supplied. */
  public StudioSettings() {
    file =
        Path.of(
            System.getProperty(
                "eduvideo.config",
                Path.of(System.getProperty("user.home"), ".jenga-code", "studio.properties")
                    .toString()));
    sessionKey = credentials.get("apiKey", "");
    keyRemembered = !sessionKey.isBlank();
    if (Files.isRegularFile(file))
      try (var in = Files.newInputStream(file)) {
        values.load(in);
      } catch (IOException e) {
        System.err.println("Cannot load Studio settings: " + e.getMessage());
      }
  }

  /** Gets a setting, permitting -Deduvideo.key overrides for automation. */
  public String get(String key, String fallback) {
    return System.getProperty("eduvideo." + key, values.getProperty(key, fallback));
  }

  /** Changes a preference in memory; save publishes all changes together. */
  public void set(String key, String value) {
    values.setProperty(key, value);
  }

  /** Atomically persists preferences without credentials. */
  public void save() throws IOException {
    for (String obsolete : java.util.List.of("speech", "piper", "voices", "espeak", "voice"))
      values.remove(obsolete);
    Files.createDirectories(file.toAbsolutePath().getParent());
    Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "settings-", ".tmp");
    try {
      try (var out = Files.newOutputStream(temp)) {
        values.store(out, "Jenga-Code EduVideo Studio");
      }
      try {
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /** Returns the configured executable, passed as a single process argument. */
  public String ffmpeg() {
    return get("ffmpeg", "ffmpeg");
  }

  /** Returns the shared speech cache folder. */
  public Path cache() {
    return Path.of(
        get(
            "cache",
            Path.of(System.getProperty("user.home"), ".jenga-code", "speech-cache").toString()));
  }

  /** Returns the selected supported Speech API model. */
  public String speechModel() {
    return get("openai.model", io.jengacode.eduvideo.audio.OpenAIVoices.DEFAULT_MODEL);
  }

  /** Returns the selected voice, using a compatible default when settings predate this release. */
  public String speechVoice() {
    return get(
        "openai.voice",
        speechModel().startsWith("tts-")
            ? "alloy"
            : io.jengacode.eduvideo.audio.OpenAIVoices.DEFAULT_VOICE);
  }

  /** Returns the configured key, then OPENAI_API_KEY. Never log this return value. */
  public String apiKey() {
    if (sessionKey != null && !sessionKey.isBlank()) return sessionKey;
    String environment = System.getenv("OPENAI_API_KEY");
    return environment == null ? "" : environment.trim();
  }

  /**
   * Returns only the GUI-configured key for editing, never copies the environment into the form.
   */
  public String configuredApiKey() {
    return sessionKey == null ? "" : sessionKey;
  }

  /** Reports whether the user explicitly chose local preference storage. */
  public boolean keyRemembered() {
    return keyRemembered;
  }

  /** Sets a session-only key for previews, snapshots and tests; does not persist any secret. */
  public void setSessionApiKey(String key) {
    sessionKey = key == null ? "" : key.trim();
  }

  /**
   * Saves or forgets the key separately from normal settings. Java Preferences is local storage,
   * not encryption; the GUI makes remembering optional and discloses this limitation.
   */
  public void configureApiKey(String key, boolean remember) throws IOException {
    setSessionApiKey(key);
    if (sessionKey.length() > java.util.prefs.Preferences.MAX_VALUE_LENGTH)
      throw new IOException(
          "The API key is too long. Paste only the API key, not a configuration file.");
    keyRemembered = remember && !sessionKey.isBlank();
    if (keyRemembered) credentials.put("apiKey", sessionKey);
    else credentials.remove("apiKey");
    try {
      credentials.flush();
    } catch (java.util.prefs.BackingStoreException e) {
      throw new IOException("Could not save OpenAI credential preferences.");
    }
  }

  /** Captures the current session configuration for a render or preview without persisting it. */
  public StudioSettings copy() {
    StudioSettings copy = new StudioSettings();
    copy.values.clear();
    copy.values.putAll(values);
    copy.sessionKey = apiKey();
    copy.keyRemembered = keyRemembered;
    return copy;
  }
}
