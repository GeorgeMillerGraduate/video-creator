/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.audio;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/**
 * FFmpeg decoder plus deterministic sample-domain mixer. Memory is bounded to one second of output
 * and one decoded stream per track. Music ducking uses measured speech windows. Mixed samples use a
 * soft limiter; final loudness compliance still requires editorial QA.
 */
public final class AudioMixer {
  private final AudioSettings settings;
  private final io.jengacode.eduvideo.production.Cancellation cancellation;
  private final io.jengacode.eduvideo.production.ProductionListener listener;
  private final String ffmpeg;

  /** Selects per-job process ownership and monitoring without changing sample-domain mixing. */
  public AudioMixer(
      AudioSettings settings,
      io.jengacode.eduvideo.production.Cancellation cancellation,
      io.jengacode.eduvideo.production.ProductionListener listener,
      String ffmpeg) {
    this.settings = settings;
    this.cancellation = cancellation;
    this.listener = listener;
    this.ffmpeg = ffmpeg;
  }

  /**
   * Selects a validated audio policy.
   *
   * @param settings validated rendering or mixing configuration
   */
  public AudioMixer(AudioSettings settings) {
    this(
        settings,
        new io.jengacode.eduvideo.production.Cancellation(),
        new io.jengacode.eduvideo.production.ProductionListener() {},
        new io.jengacode.eduvideo.app.StudioSettings().ffmpeg());
  }

  /** Checks FFmpeg availability without starting any expensive visual rendering. */
  public static void preflight() throws IOException, InterruptedException {
    new io.jengacode.eduvideo.production.Cancellation()
        .run(List.of(new io.jengacode.eduvideo.app.StudioSettings().ffmpeg(), "-version"), 20);
  }

  /**
   * * Resolves cached narration, verifies its duration and mixes all tracks to a WAV file.
   *
   * @param timeline independent narration, music and effects model
   * @param duration positive interval length in seconds
   * @param provider speech provider; tests can supply an offline implementation
   * @param cache content-addressed speech cache
   * @param workspace temporary directory for decoded audio intermediates
   * @param output destination file or delivery settings, according to this signature
   * @return number of narration clips obtained from cache
   */
  public int mix(
      AudioTimeline timeline,
      double duration,
      SpeechProvider provider,
      SpeechCache cache,
      Path workspace,
      Path output)
      throws IOException, InterruptedException {
    Files.createDirectories(workspace);
    var tracks = new ArrayList<>(timeline.tracks());
    var speech = new ArrayList<double[]>();
    int hits = 0;
    listener.stage("Narration");
    int narrationIndex = 0;
    for (var cue : timeline.narration()) {
      listener.stage(
          "Preparing narration " + (++narrationIndex) + " / " + timeline.narration().size());
      cancellation.check();
      listener.message("SPEECH", "Resolving " + cue.id());
      SpeechCache.Clip clip;
      try {
        clip = cache.resolve(provider, cue);
      } catch (IOException | IllegalStateException e) {
        throw new IOException(cue.id() + ": " + e.getMessage(), e);
      }
      listener.speech(cue.id(), clip.hit(), clip.duration());
      listener.stage(
          (clip.hit() ? "Reusing cached narration " : "Generated narration ")
              + narrationIndex
              + " / "
              + timeline.narration().size());
      if (clip.hit()) hits++;
      tracks.add(
          new AudioTimeline.Track(
              clip.path(), cue.start(), cue.start() + clip.duration(), 1, 0, 0, false, false));
      speech.add(new double[] {cue.start(), cue.start() + clip.duration()});
    }
    listener.stage("Audio mixing");
    var ranges = AudioTimeline.merged(speech, settings.mergeGap());
    var decoded = new ArrayList<Path>();
    var streams = new ArrayList<InputStream>();
    try {
      for (int i = 0; i < tracks.size(); i++) {
        var track = tracks.get(i);
        Path pcm = workspace.resolve("track-" + i + ".pcm");
        var cmd = new ArrayList<>(List.of(ffmpeg, "-v", "error", "-nostdin", "-y"));
        if (track.loop()) cmd.addAll(List.of("-stream_loop", "-1"));
        cmd.addAll(
            List.of(
                "-i",
                track.source().toString(),
                "-t",
                Double.toString(track.end() - track.start()),
                "-f",
                "f32le",
                "-ac",
                "2",
                "-ar",
                Integer.toString(settings.sampleRate()),
                pcm.toString()));
        cancellation.run(cmd, 3600);
        decoded.add(pcm);
        streams.add(null);
      }
      long count = (long) Math.ceil(duration * settings.sampleRate());
      if (count * 4 > 0xffffffffL - 36) throw new IOException("WAV exceeds RIFF limit");
      try (var out =
          new BufferedOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW))) {
        wavHeader(out, count, settings.sampleRate());
        byte[] sample = new byte[8];
        var sampleBuffer = ByteBuffer.wrap(sample).order(ByteOrder.LITTLE_ENDIAN);
        var ordered = new ArrayList<Integer>();
        for (int i = 0; i < tracks.size(); i++) ordered.add(i);
        ordered.sort(Comparator.comparingDouble(i -> tracks.get(i).start()));
        var active = new ArrayList<Integer>();
        boolean[] exhausted = new boolean[tracks.size()];
        int next = 0, rangeIndex = 0;
        for (long frame = 0; frame < count; frame++) {
          if (frame % 4096 == 0) cancellation.check();
          double t = frame / (double) settings.sampleRate(), left = 0, right = 0;
          while (next < ordered.size() && tracks.get(ordered.get(next)).start() <= t)
            active.add(ordered.get(next++));
          while (rangeIndex < ranges.size() && ranges.get(rangeIndex)[1] + settings.release() < t)
            rangeIndex++;
          double duck = duckAt(t, ranges, rangeIndex);
          for (var iterator = active.iterator(); iterator.hasNext(); ) {
            int i = iterator.next();
            var track = tracks.get(i);
            if (t >= track.end() || exhausted[i]) {
              if (streams.get(i) != null) {
                streams.get(i).close();
                streams.set(i, null);
              }
              iterator.remove();
              continue;
            }
            if (streams.get(i) == null)
              streams.set(i, new BufferedInputStream(Files.newInputStream(decoded.get(i))));
            int n = streams.get(i).readNBytes(sample, 0, 8);
            if (n != 8) {
              exhausted[i] = true;
              continue;
            }
            var b = sampleBuffer;
            b.rewind();
            double gain = track.volume();
            if (track.fadeIn() > 0) gain *= smooth((t - track.start()) / track.fadeIn());
            if (track.fadeOut() > 0) gain *= smooth((track.end() - t) / track.fadeOut());
            if (track.music()) gain *= duck;
            left += b.getFloat() * gain;
            right += b.getFloat() * gain;
          }
          writeSample(out, left);
          writeSample(out, right);
        }
      }
    } finally {
      for (var stream : streams) if (stream != null) stream.close();
      for (var p : decoded) Files.deleteIfExists(p);
    }
    return hits;
  }

  /**
   * Samples only nearby speech envelopes; completed cues do not add per-sample work to long
   * productions.
   */
  private double duckAt(double time, List<double[]> ranges, int first) {
    double gain = 1;
    for (int i = first; i < ranges.size(); i++) {
      var r = ranges.get(i);
      if (r[0] - settings.attack() > time) break;
      double u =
          time < r[0]
              ? (time - r[0] + settings.attack()) / settings.attack()
              : time <= r[1] ? 1 : 1 - (time - r[1]) / settings.release();
      gain = Math.min(gain, 1 - smooth(u) * (1 - settings.duckGain()));
    }
    return gain;
  }

  /**
   * Writes a little-endian sample with transparent gain below the soft-limit threshold.
   *
   * @param out output stream receiving little-endian PCM bytes
   * @param v mixed floating-point sample before soft limiting
   */
  private static void writeSample(OutputStream out, double v) throws IOException {
    double a = Math.abs(v);
    if (a > .9) v = Math.copySign(.9 + .1 * Math.tanh((a - .9) / .1), v);
    int s = (int) Math.round(Math.max(-1, Math.min(1, v)) * 32767);
    out.write(s & 255);
    out.write((s >>> 8) & 255);
  }

  /**
   * * Clamps and smooths a fade envelope.
   *
   * @param u normalised envelope progress, clamped before evaluation
   * @return clamped cubic smoothstep envelope
   */
  private static double smooth(double u) {
    u = Math.max(0, Math.min(1, u));
    return u * u * (3 - 2 * u);
  }

  /**
   * Writes a conventional stereo signed-16-bit PCM WAV header.
   *
   * @param out output stream receiving little-endian PCM bytes
   * @param frames number of stereo sample frames to write
   * @param rate PCM sample rate in samples per second
   */
  private static void wavHeader(OutputStream out, long frames, int rate) throws IOException {
    int size = (int) (frames * 4);
    var b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    b.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        .putInt(size + 36)
        .put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 2)
        .putInt(rate)
        .putInt(rate * 4)
        .putShort((short) 4)
        .putShort((short) 16)
        .put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        .putInt(size);
    out.write(b.array());
  }

  /**
   * Executes FFmpeg with inherited diagnostics and fails on a nonzero status.
   *
   * @param command complete process argument list, passed without a shell
   */
  public static void run(List<String> command) throws IOException, InterruptedException {
    new io.jengacode.eduvideo.production.Cancellation().run(command, 3600);
  }
}
