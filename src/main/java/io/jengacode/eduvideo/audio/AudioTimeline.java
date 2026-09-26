/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.audio;

import java.nio.file.*;
import java.util.*;

/** Audio-only project model; it has no dependency on frame rendering or a wall clock. */
public final class AudioTimeline {
  /** One file-backed track. End is exclusive; looping is useful for music beds. */
  public record Track(
      Path source,
      double start,
      double end,
      double volume,
      double fadeIn,
      double fadeOut,
      boolean loop,
      boolean music) {
    /**
     * Rejects missing assets and invalid gain/timing values.
     *
     * @param source source object, matrix row or existing audio file, as described above
     * @param start nonnegative interval start in scene-local seconds (global seconds for audio
     *     cues)
     * @param end exclusive interval end in seconds
     * @param volume linear gain multiplier from zero through four
     * @param fadeIn nonnegative fade-in duration in seconds
     * @param fadeOut nonnegative fade-out duration in seconds
     * @param loop whether FFmpeg loops the source to fill its interval
     * @param music whether narration ducking applies to this track
     */
    public Track {
      source = source.toAbsolutePath();
      if (!Files.isRegularFile(source)
          || !Double.isFinite(start + end + volume + fadeIn + fadeOut)
          || start < 0
          || end <= start
          || volume < 0
          || volume > 4
          || fadeIn < 0
          || fadeOut < 0
          || fadeIn + fadeOut > end - start)
        throw new IllegalArgumentException("Invalid audio track: " + source);
    }
  }

  private final List<NarrationCue> narration = new ArrayList<>();
  private final List<Track> tracks = new ArrayList<>();

  /**
   * Appends a narration cue; overlap and project bounds are checked during validation.
   *
   * @param cue narration request and allowed timing window
   */
  public void speak(NarrationCue cue) {
    narration.add(cue);
  }

  /**
   * Appends a file-backed music or sound-effect track.
   *
   * @param track validated file-backed audio track
   */
  public void add(Track track) {
    tracks.add(track);
  }

  /**
   * Returns a snapshot of narration requests in authoring order.
   *
   * @return immutable snapshot of narration requests
   */
  public List<NarrationCue> narration() {
    return List.copyOf(narration);
  }

  /**
   * Returns a snapshot of file-backed tracks.
   *
   * @return immutable snapshot of file-backed tracks
   */
  public List<Track> tracks() {
    return List.copyOf(tracks);
  }

  /**
   * Checks unique cue identities, nonoverlapping speech windows and project bounds.
   *
   * @param duration positive interval length in seconds
   */
  public void validate(double duration) {
    double end = 0;
    var ids = new HashSet<String>();
    for (var n :
        narration.stream().sorted(Comparator.comparingDouble(NarrationCue::start)).toList()) {
      if (!ids.add(n.id()) || n.start() < end || n.maxEnd() > duration + .0001)
        throw new IllegalArgumentException("Narration overlaps or exceeds project: " + n.id());
      end = n.maxEnd();
    }
    for (var t : tracks)
      if (t.end() > duration + .0001)
        throw new IllegalArgumentException("Audio exceeds project: " + t.source());
  }

  /**
   * * Merges measured narration ranges so short pauses do not pump music volume.
   *
   * @param ranges measured narration intervals, expressed as start/end pairs
   * @param gap maximum pause between narration ranges to merge, in seconds
   * @return copied, sorted and merged narration ranges
   */
  public static List<double[]> merged(List<double[]> ranges, double gap) {
    var sorted =
        ranges.stream().map(double[]::clone).sorted(Comparator.comparingDouble(a -> a[0])).toList();
    var result = new ArrayList<double[]>();
    for (var r : sorted) {
      if (result.isEmpty() || r[0] > result.get(result.size() - 1)[1] + gap) result.add(r);
      else result.get(result.size() - 1)[1] = Math.max(result.get(result.size() - 1)[1], r[1]);
    }
    return result;
  }

  /**
   * * Samples a smooth duck envelope, returning one outside speech attack/release regions.
   *
   * @param time sample time in scene-local seconds
   * @param ranges measured narration intervals, expressed as start/end pairs
   * @param s validated audio settings
   * @return music gain multiplier after smooth attack/release envelopes
   */
  public static double duck(double time, List<double[]> ranges, AudioSettings s) {
    double gain = 1;
    for (var r : ranges) {
      double u;
      if (time < r[0]) u = (time - r[0] + s.attack()) / s.attack();
      else if (time <= r[1]) u = 1;
      else u = 1 - (time - r[1]) / s.release();
      u = Math.max(0, Math.min(1, u));
      u = u * u * (3 - 2 * u);
      gain = Math.min(gain, 1 - u * (1 - s.duckGain()));
    }
    return gain;
  }
}
