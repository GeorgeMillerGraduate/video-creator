/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.production;

import io.jengacode.eduvideo.audio.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Composes clocks once, before mixing, so a music region has only one decoder and one fade
 * envelope.
 */
public final class ProductionAudio {
  /** Prevents construction of the stateless audio composition utility. */
  private ProductionAudio() {}

  /**
   * Offsets narration and effects; joins touching, equivalent music beds across chapter boundaries.
   */
  public static AudioTimeline assemble(ProductionProject production) {
    var result = new AudioTimeline();
    var tracks = new ArrayList<AudioTimeline.Track>();
    tracks.addAll(production.music());
    int index = 0;
    for (var part : production.parts()) {
      String prefix = "Part " + (++index) + " (" + part.source().getFileName() + ") / ";
      for (var cue : part.project().audioTimeline().narration())
        result.speak(
            new NarrationCue(
                prefix + cue.id(),
                part.global(cue.start()),
                part.global(cue.maxEnd()),
                cue.text(),
                cue.voice()));
      for (var t : part.project().audioTimeline().tracks()) {
        // An equivalent global bed already owns playback; avoid layering duplicate part music.
        if (t.music()
            && production.music().stream()
                .anyMatch(
                    g ->
                        samePlayback(g, t)
                            && g.start() <= part.global(t.start())
                            && g.end() >= part.global(t.end()))) continue;
        var shifted =
            new AudioTimeline.Track(
                canonical(t.source()),
                part.global(t.start()),
                part.global(t.end()),
                t.volume(),
                t.fadeIn(),
                t.fadeOut(),
                t.loop(),
                t.music());
        int join = -1;
        if (t.music() && Math.abs(t.start()) < 1e-8) {
          for (int j = 0; j < tracks.size(); j++) {
            var prior = tracks.get(j);
            if (prior.music()
                && samePlayback(prior, shifted)
                && Math.abs(prior.end() - shifted.start()) < 1e-8
                && ((prior.fadeOut() == 0 && shifted.fadeIn() == 0)
                    || (prior.fadeIn() == shifted.fadeIn()
                        && prior.fadeOut() == shifted.fadeOut()))) {
              join = j;
              break;
            }
          }
        }
        if (join >= 0) {
          var prior = tracks.get(join);
          tracks.set(
              join,
              new AudioTimeline.Track(
                  prior.source(),
                  prior.start(),
                  shifted.end(),
                  prior.volume(),
                  prior.fadeIn(),
                  shifted.fadeOut(),
                  prior.loop(),
                  true));
        } else tracks.add(shifted);
      }
    }
    tracks.forEach(result::add);
    result.validate(production.duration());
    return result;
  }

  /**
   * Compares normalized assets and gain/loop policy; timing and outer fades describe the region.
   */
  private static boolean samePlayback(AudioTimeline.Track a, AudioTimeline.Track b) {
    return canonical(a.source()).equals(canonical(b.source()))
        && Double.compare(a.volume(), b.volume()) == 0
        && a.loop() == b.loop();
  }

  /** Resolves aliases to an existing asset before comparing music regions. */
  private static Path canonical(Path p) {
    try {
      return p.toRealPath();
    } catch (IOException e) {
      return p.toAbsolutePath().normalize();
    }
  }
}
