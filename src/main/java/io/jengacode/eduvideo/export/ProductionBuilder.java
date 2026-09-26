/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.export;

import io.jengacode.eduvideo.audio.*;
import io.jengacode.eduvideo.video.*;
import io.jengacode.eduvideo.production.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Preflight-first production pipeline. Speech is generated, cached, measured and mixed before
 * visual rendering. BGR frames stream directly to FFmpeg, avoiding a huge PNG sequence. Temporary
 * products stay beside the destination and are removed after success, failure or cancellation.
 */
public final class ProductionBuilder {
  private final SpeechProvider speech;
  private final io.jengacode.eduvideo.production.Cancellation cancellation;
  private final io.jengacode.eduvideo.production.ProductionListener listener;
  private final String ffmpeg;

  /** Uses the user's OpenAI narration and FFmpeg settings for both frontends. */
  public ProductionBuilder() {
    this(
        new io.jengacode.eduvideo.app.StudioSettings(),
        new io.jengacode.eduvideo.production.Cancellation(),
        new io.jengacode.eduvideo.production.ProductionListener() {});
  }

  /** Creates a monitored job with its own cancellation token. */
  public ProductionBuilder(
      io.jengacode.eduvideo.app.StudioSettings settings,
      io.jengacode.eduvideo.production.Cancellation cancellation,
      io.jengacode.eduvideo.production.ProductionListener listener) {
    this(
        new OpenAISpeechProvider(settings, cancellation),
        settings.ffmpeg(),
        cancellation,
        listener);
  }

  /** Injects a provider and process policy for integrations and deterministic tests. */
  public ProductionBuilder(
      SpeechProvider speech,
      String ffmpeg,
      io.jengacode.eduvideo.production.Cancellation cancellation,
      io.jengacode.eduvideo.production.ProductionListener listener) {
    this.speech = speech;
    this.ffmpeg = ffmpeg;
    this.cancellation = cancellation;
    this.listener = listener;
  }

  /** Delivery profiles preserve the lesson's logical coordinate system. */
  public enum Profile {
    /** Fast editorial preview. */
    PREVIEW,
    /** Full-HD delivery with 30 frames per second. */
    STANDARD,
    /** Full-HD delivery with 60 frames per second. */
    FINAL;

    /**
     * Returns concrete raster and frame-rate settings.
     *
     * @return concrete delivery dimensions and frame rate
     */
    public VideoSettings settings() {
      return switch (this) {
        case PREVIEW -> new VideoSettings(960, 540, 30);
        case STANDARD -> new VideoSettings(1920, 1080, 30);
        case FINAL -> new VideoSettings(1920, 1080, 60);
      };
    }
  }

  /**
   * * Builds a protected MP4 and a report. noSpeech is explicit editorial mode, never a silent
   * fallback.
   *
   * @param project validated lesson model, kept unchanged during rendering
   * @param output destination file or delivery settings, according to this signature
   * @param profile delivery resolution and frame-rate preset
   * @param cacheDirectory directory for reusable speech WAV files
   * @param noSpeech explicit editorial mode omitting narration while preserving music and effects
   * @param from source position or start of the requested mathematical/render interval
   * @param to destination position or end of the requested mathematical/render interval
   */
  public void build(
      VideoProject project,
      Path output,
      Profile profile,
      Path cacheDirectory,
      boolean noSpeech,
      double from,
      double to)
      throws Exception {
    build(new FrameSequence(project, profile.settings()), null, output, profile, cacheDirectory, noSpeech, from, to);
  }

  /** Builds a complete or ranged manifest through the same mixer, frame pipe and encoder as a lesson. */
  public void build(ProductionProject production, Path output, Profile profile, Path cacheDirectory,
                    boolean noSpeech, double from, double to) throws Exception {
    build(new FrameSequence(production, profile.settings()), production, output, profile, cacheDirectory, noSpeech, from, to);
  }

  /** Owns workspace cleanup on every outcome, including cancellation before the first frame. */
  private void build(FrameSequence project, ProductionProject production, Path output, Profile profile,
                     Path cacheDirectory, boolean noSpeech, double from, double to) throws Exception {
    cancellation.check();
    listener.stage("Validation and preflight");
    project.validate();
    if(production!=null) for(var part:production.parts())
      if(!Files.isRegularFile(part.source())) throw new IOException("Missing XML part: "+part.source());
    for(var track:project.audioTimeline().tracks()) if(!Files.isRegularFile(track.source()))
      throw new IOException("Missing audio asset: "+track.source());
    if (!noSpeech
        && !project.audioTimeline().narration().isEmpty()
        && speech instanceof OpenAISpeechProvider openai) openai.preflight();
    if (!noSpeech) for (var cue : project.audioTimeline().narration()) {
      try { speech.resolve(cue.voice()); }
      catch (IOException e) { throw new IOException(cue.id() + ": " + e.getMessage(), e); }
    }
    cancellation.run(List.of(ffmpeg, "-version"), 20);
    if (Files.exists(output)
        || Files.exists(output.resolveSibling(output.getFileName() + ".report.txt")))
      throw new IOException("Output or report already exists: " + output);
    if (!Double.isFinite(from + to) || from < 0 || to <= from || to > project.duration() + 1e-9)
      throw new IllegalArgumentException("Invalid render range");
    Files.createDirectories(output.toAbsolutePath().getParent());
    Path work = Files.createTempDirectory(output.toAbsolutePath().getParent(), "eduvideo-build-");
    try {
    long began = System.nanoTime();
    var timeline = project.audioTimeline();
    if (noSpeech) {
      timeline = new AudioTimeline();
      for (var t : project.audioTimeline().tracks()) timeline.add(t);
      System.out.println(
          "Editorial mode: narration intentionally omitted; music/effects retained.");
    }
    Path mix = work.resolve("mix.wav");
    int hits =
        new AudioMixer(AudioSettings.defaults(), cancellation, listener, ffmpeg)
            .mix(timeline, project.duration(), speech, new SpeechCache(cacheDirectory), work, mix);
    double audioSeconds = (System.nanoTime() - began) / 1e9;
    var renderer = project;
    var settings = profile.settings();
    int first = (int) Math.floor(from * settings.fps()),
        last = Math.min(renderer.frameCount(), (int) Math.ceil(to * settings.fps())),
        count = last - first;
    Path video = work.resolve("picture.mp4");
    var command =
        List.of(
            ffmpeg,
            "-v",
            "error",
            "-nostdin",
            "-n",
            "-f",
            "rawvideo",
            "-pixel_format",
            "bgr24",
            "-video_size",
            settings.width() + "x" + settings.height(),
            "-framerate",
            Double.toString(settings.fps()),
            "-i",
            "pipe:0",
            "-an",
            "-c:v",
            "libx264",
            "-preset",
            "fast",
            "-crf",
            profile == Profile.PREVIEW ? "22" : "18",
            "-threads",
            "2",
            "-pix_fmt",
            "yuv420p",
            "-movflags",
            "+faststart",
            video.toString());
    listener.stage("Rendering and encoding frames");
    var process =
        new ProcessBuilder(command)
            .redirectError(work.resolve("encoder.log").toFile())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
    long visuals = System.nanoTime();
    try {
      cancellation.own(process);
      var raster =
          new BufferedImage(settings.width(), settings.height(), BufferedImage.TYPE_3BYTE_BGR);
      byte[] pixels =
          ((java.awt.image.DataBufferByte) raster.getRaster().getDataBuffer()).getData();
      try (var pipe = new BufferedOutputStream(process.getOutputStream(), pixels.length)) {
        for (int frame = first; frame < last; frame++) {
          cancellation.check();
          var image = renderer.renderFrame(frame);
          var g = raster.createGraphics();
          try {
            g.drawImage(image, 0, 0, null);
          } finally {
            g.dispose();
          }
          pipe.write(pixels);
          listener.part(renderer.progress(frame, first, last));
          listener.frame(
              image,
              frame - first + 1,
              count,
              frame / settings.fps(),
              (System.nanoTime() - visuals) / 1e9);
        }
      }
      if (process.waitFor() != 0 || Files.size(video) == 0)
        throw new IOException(
            "Video encoder failed in "
                + work
                + "\n"
                + Files.readString(work.resolve("encoder.log")));
    } finally {
      cancellation.terminateAndWait(process);
    }
    cancellation.check();
    double renderSeconds = (System.nanoTime() - visuals) / 1e9;
    Path mux = work.resolve("delivery.mp4");
    listener.stage("Final audio/video mux");
    cancellation.run(
        List.of(
            ffmpeg,
            "-v",
            "error",
            "-nostdin",
            "-n",
            "-i",
            video.toString(),
            "-ss",
            Double.toString(first / settings.fps()),
            "-i",
            mix.toString(),
            "-map",
            "0:v:0",
            "-map",
            "1:a:0",
            "-c:v",
            "copy",
            "-c:a",
            "aac",
            "-b:a",
            "192k",
            "-ar",
            "48000",
            "-ac",
            "2",
            "-t",
            Double.toString(count / settings.fps()),
            "-movflags",
            "+faststart",
            mux.toString()),
        3600);
    if (Files.size(mux) == 0) throw new IOException("Muxer produced an empty file");
    cancellation.check();
    listener.stage("Publishing output");
    Files.move(mux, output);
    String report =
        String.format(
            Locale.ROOT,
            "Jenga-Code EduVideo production report%nProfile: %s%nLogical canvas: %dx%d%nOutput:"
                + " %dx%d at %.0f fps%nFrames: %d%nRange: %.3f to %.3f seconds%nScenes:"
                + " %d%nObjects: %d%nNarration requests: %d%nSpeech cache hits: %d%nNarration"
                + " omitted: %s%nAudio preparation: %.2f s%nVisual render + encode: %.2f s%nRender"
                + " throughput: %.2f fps%nVideo: H.264 / yuv420p%nAudio: AAC stereo / 48 kHz / 192"
                + " kb/s%nTotal elapsed: %.2f s%n",
            profile,
            project.settings().width(),
            project.settings().height(),
            settings.width(),
            settings.height(),
            settings.fps(),
            count,
            first / settings.fps(),
            last / settings.fps(),
            project.lessons().stream().mapToInt(p -> p.scenes().size()).sum(),
            project.lessons().stream().flatMap(p -> p.scenes().stream()).mapToInt(s -> s.objects().size()).sum(),
            timeline.narration().size(),
            hits,
            noSpeech,
            audioSeconds,
            renderSeconds,
            count / renderSeconds,
            (System.nanoTime() - began) / 1e9);
    report +=
        "Property animation tracks: "
            + project.lessons().stream().flatMap(p -> p.scenes().stream())
                .flatMap(s -> s.objects().values().stream())
                .mapToInt(o -> o.timeline().trackCount())
                .sum()
            + System.lineSeparator()
            + "Original narration cues: "
            + project.audioTimeline().narration().size()
            + System.lineSeparator()
            + "Speech synthesis requests: "
            + (timeline.narration().size() - hits)
            + System.lineSeparator()
            + "Music tracks: "
            + timeline.tracks().stream().filter(AudioTimeline.Track::music).count()
            + System.lineSeparator()
            + "Output: "
            + output.getFileName()
            + System.lineSeparator()
            + "Encoding overlaps rendering; separate encoder CPU time is not measured."
            + System.lineSeparator();
    Files.writeString(
        output.resolveSibling(output.getFileName() + ".report.txt"),
        report,
        StandardOpenOption.CREATE_NEW);
    if (production != null) {
      Path chapters = output.resolveSibling(output.getFileName() + ".chapters.txt");
      try { Files.writeString(chapters, production.chapters(first/settings.fps(),last/settings.fps()), StandardOpenOption.CREATE_NEW); }
      catch (IOException e) { listener.message("WARNING", "Video complete; chapter list not written: " + e.getMessage()); }
    }
    listener.message("INFO", report);
    listener.completed(output);
    } finally {
      cancellation.finish();
      try {
        List<Path> cleanup;
        try (var paths = Files.walk(work)) { cleanup = paths.sorted(Comparator.reverseOrder()).toList(); }
        for (Path path : cleanup) Files.deleteIfExists(path);
      } catch (IOException e) { listener.message("WARNING", "Temporary workspace could not be removed: " + work + ": " + e.getMessage()); }
    }
  }
}
