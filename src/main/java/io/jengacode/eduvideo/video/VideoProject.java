/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.video;

import io.jengacode.eduvideo.scene.Scene;
import java.util.*;

/**
 * Shared Java/XML model; do not modify during a render.
 *
 * <p>Holds output settings, default theme, ordered scenes, legacy audio cues and the production
 * audio timeline. Collection accessors return snapshots. Validate once after construction and do
 * not mutate the model during export.
 */
public final class VideoProject {

  private final VideoSettings settings;
  private final io.jengacode.eduvideo.audio.AudioTimeline audioTimeline =
      new io.jengacode.eduvideo.audio.AudioTimeline();

  /**
   * Returns the separate narration/music/effects timeline.
   *
   * @return the project's separate narration/music/effects timeline
   */
  public io.jengacode.eduvideo.audio.AudioTimeline audioTimeline() {
    return audioTimeline;
  }

  private Theme theme = Theme.dark();

  private final List<Scene> scenes = new ArrayList<>();

  private final List<AudioCue> audio = new ArrayList<>();

  /**
   * Creates a configured VideoProject instance.
   *
   * @param settings validated video output dimensions and frame rate
   */
  public VideoProject(VideoSettings settings) {
    this.settings = Objects.requireNonNull(settings);
  }

  /**
   * Returns settings for this configured component.
   *
   * @return settings for this configured component
   */
  public VideoSettings settings() {
    return settings;
  }

  /**
   * Returns theme for this configured component.
   *
   * @return theme for this configured component
   */
  public Theme theme() {
    return theme;
  }

  /**
   * Configures theme for this component.
   *
   * @param t non-null active palette
   * @return this instance for fluent configuration
   */
  public VideoProject theme(Theme t) {
    theme = Objects.requireNonNull(t);
    return this;
  }

  /**
   * Appends a scene in project playback order.
   *
   * @param s scene to append
   * @return this instance for fluent configuration
   */
  public VideoProject add(Scene s) {
    scenes.add(s);
    return this;
  }

  /**
   * Returns scenes for this configured component.
   *
   * @return unmodifiable snapshot of the configured contents
   */
  public List<Scene> scenes() {
    return List.copyOf(scenes);
  }

  /**
   * Configures audio for this component.
   *
   * @param cue audio metadata to retain for future mixing
   * @return this instance for fluent configuration
   */
  public VideoProject audio(AudioCue cue) {
    audio.add(cue);
    return this;
  }

  /**
   * Returns audio for this configured component.
   *
   * @return audio for this configured component
   */
  public List<AudioCue> audio() {
    return List.copyOf(audio);
  }

  /**
   * Returns duration for this configured component.
   *
   * @return time or duration in seconds
   */
  public double duration() {
    return scenes.stream().mapToDouble(Scene::duration).sum();
  }

  /**
   * Computes the number of frames required to cover the positive duration.
   *
   * @return computes the number of frames required to cover the positive duration
   */
  public int frameCount() {
    return settings.frameCount(duration());
  }

  /**
   * Checks scene identity, each scene model and the project frame count.
   *
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public void validate() {
    if (scenes.isEmpty()) throw new IllegalArgumentException("Video needs at least one scene");
    var ids = new HashSet<String>();
    for (var s : scenes) {
      if (!ids.add(s.id())) throw new IllegalArgumentException("Duplicate scene id: " + s.id());
      s.validate();
    }
    frameCount();
    audioTimeline.validate(duration());
  }
}
