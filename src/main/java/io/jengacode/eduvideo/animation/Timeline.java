/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

import java.util.*;

/**
 * Non-overlapping property tracks, sampled without changing the scene model.
 *
 * <p>Tracks for the same property may touch but cannot overlap. Sampling selects the latest
 * applicable track; ordinary tracks hold endpoints while pulse tracks return zero outside their
 * interval.
 */
public final class Timeline {

  private final EnumMap<Property, List<Animation>> tracks = new EnumMap<>(Property.class);

  /**
   * Counts property tracks for measured production reports.
   *
   * @return total number of property animation tracks
   */
  public int trackCount() {
    return tracks.values().stream().mapToInt(java.util.List::size).sum();
  }

  /**
   * Adds a track; intervals for the same property may touch but not overlap.
   *
   * <p>Adds a track after rejecting overlapping intervals on the same property.
   *
   * @param a non-null track whose interval must not overlap the same property
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public void add(Animation a) {
    var list = tracks.computeIfAbsent(a.property(), p -> new ArrayList<>());
    for (var b : list)
      if (a.start() < b.end() && b.start() < a.end())
        throw new IllegalArgumentException("Overlapping animations for " + a.property().xml);
    list.add(a);
    list.sort(Comparator.comparingDouble(Animation::start));
  }

  /**
   * Returns the base value or latest applicable track, holding values across gaps.
   *
   * <p>Samples the latest applicable track, or returns the base value if no track exists.
   *
   * @param p property to query or configure
   * @param base fallback property value or lesson asset directory, according to the overload
   * @param time sample or event time in scene-local seconds
   * @return the base value or latest applicable track, holding values across gaps
   */
  public double value(Property p, double base, double time) {
    var list = tracks.get(p);
    if (list == null) return base;
    Animation chosen = list.get(0);
    for (var a : list) {
      if (a.start() > time) break;
      chosen = a;
    }
    return chosen.valueAt(time);
  }

  /**
   * Returns end for this configured component.
   *
   * @return time or duration in seconds
   */
  public double end() {
    return tracks.values().stream()
        .flatMap(List::stream)
        .mapToDouble(Animation::end)
        .max()
        .orElse(0);
  }
}
