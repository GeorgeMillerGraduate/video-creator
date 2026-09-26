/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.audio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import javax.sound.sampled.*;

/**
 * Content-addressed PCM WAV cache. Writes are staged and atomically published when supported. Cache
 * hits are decoded and duration-checked too; corrupt entries are removed and regenerated.
 */
public final class SpeechCache {
  private final Path directory;

  /** Result of one cache lookup, including measured rather than estimated duration. */
  public record Clip(Path path, double duration, boolean hit) {}

  /**
   * Creates a cache facade; directories are created only when synthesis is needed.
   *
   * @param directory directory containing content-addressed speech clips
   */
  public SpeechCache(Path directory) {
    this.directory = directory.toAbsolutePath();
  }

  /**
   * * Hashes length-prefixed request fields to avoid delimiter ambiguity.
   *
   * @param provider speech provider; tests can supply an offline implementation
   * @param cue narration request and allowed timing window
   * @return 64-character lowercase SHA-256 cache identifier
   */
  public static String key(SpeechProvider provider, NarrationCue cue) {
    try {
      cue =
          new NarrationCue(
              cue.id(), cue.start(), cue.maxEnd(), cue.text(), provider.resolve(cue.voice()));
      var bytes = new ByteArrayOutputStream();
      try (var d = new DataOutputStream(bytes)) {
        var v = cue.voice();
        for (String s :
            new String[] {
              provider.id(),
              v.language(),
              v.name(),
              Double.toString(v.rate()),
              Double.toString(v.pitch()),
              Boolean.toString(v.ssml()),
              "WAV/validated-pcm16/v3",
              cue.text()
            }) {
          byte[] b = s.getBytes(StandardCharsets.UTF_8);
          d.writeInt(b.length);
          d.write(b);
        }
      }
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * * * Obtains valid speech, then rejects any cue whose actual duration exceeds its narration
   * window.
   *
   * @param provider speech provider; tests can supply an offline implementation
   * @param cue narration request and allowed timing window
   * @return validated semantic reference or measured cache result, as described above
   */
  public Clip resolve(SpeechProvider provider, NarrationCue cue) throws IOException {
    cue =
        new NarrationCue(
            cue.id(), cue.start(), cue.maxEnd(), cue.text(), provider.resolve(cue.voice()));
    Files.createDirectories(directory);
    Path out = directory.resolve(key(provider, cue) + ".wav");
    boolean hit = Files.isRegularFile(out);
    if (hit) {
      try {
        duration(out);
      } catch (IOException corrupt) {
        Files.deleteIfExists(out);
        hit = false;
      }
    }
    if (!hit) {
      Path temp = Files.createTempFile(directory, "speech-", ".wav");
      try {
        Files.write(temp, WavAudio.normalize(provider.synthesize(cue.text(), cue.voice())).bytes());
        duration(temp);
        try {
          Files.move(temp, out, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
          Files.move(temp, out, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        Files.deleteIfExists(temp);
      }
    }
    double d = duration(out);
    if (cue.start() + d > cue.maxEnd() + .001)
      throw new IOException(
          "Narration '"
              + cue.id()
              + "' needs "
              + String.format(java.util.Locale.ROOT, "%.2f", d)
              + "s but its window is "
              + (cue.maxEnd() - cue.start())
              + "s; extend max-end or shorten the script. Audio is never time-stretched.");
    return new Clip(out, d, hit);
  }

  /**
   * * Measures PCM WAV frame duration and rejects unsupported or empty files.
   *
   * @param path path to a complete signed PCM WAV file
   * @return measured PCM duration in seconds
   */
  public static double duration(Path path) throws IOException {
    return WavAudio.normalize(Files.readAllBytes(path)).seconds();
  }

  /**
   * Deletes only content-addressed WAV entries; unrelated files are preserved.
   *
   * @return number of content-addressed WAV entries deleted
   */
  public int clear() throws IOException {
    if (!Files.exists(directory)) return 0;
    int count = 0;
    try (var files = Files.list(directory)) {
      for (var p :
          files.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.wav")).toList()) {
        Files.delete(p);
        count++;
      }
    }
    return count;
  }
}
