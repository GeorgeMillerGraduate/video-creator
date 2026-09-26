/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.cli;

import io.jengacode.eduvideo.audio.*;
import io.jengacode.eduvideo.export.*;
import io.jengacode.eduvideo.preview.PreviewApp;
import io.jengacode.eduvideo.video.*;
import io.jengacode.eduvideo.xml.ProjectParser;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Production CLI with deterministic frame/range sampling and explicit editorial audio mode. Errors
 * exit with status 2. Existing outputs are never replaced implicitly.
 */
public final class Main {
  /** Prevents utility construction. */
  private Main() {}

  /**
   * Runs the CLI, converting failures into concise diagnostics and status 2.
   *
   * @param args command-line arguments; see --help for supported syntax
   */
  public static void main(String[] args) {
    try {
      run(args);
    } catch (Exception e) {
      System.err.println("EduVideo: " + e.getMessage());
      System.exit(2);
    }
  }

  /**
   * Parses a command; programmatic callers receive the original exception.
   *
   * @param args command-line arguments; see --help for supported syntax
   */
  public static void run(String[] args) throws Exception {
    if (args.length == 0 || args[0].equals("--help")) {
      System.out.println(
          "EduVideo Studio 3.2 | Jenga-Code production engine\n"
              + "validate lesson.xml (or an edu-production manifest)\n"
              + "frame lesson.xml --time SECONDS output.png [--profile preview|standard|final]\n"
              + "build lesson.xml output.mp4 [--profile final] [--from SECONDS --to SECONDS]"
              + " [--scene ID] [--no-speech]\n"
              + "render (alias for build; directory output retains legacy PNG export)\n"
              + "preview lesson.xml [output.mp4 --scene ID --from SECONDS --to SECONDS"
              + " --no-speech]\n"
              + "--list-voices\n"
              + "--test-voice VOICE_ID [output.wav]\n"
              + "clear-speech-cache [directory]\n"
              + "Speech uses OpenAI (API billing applies). --no-speech explicitly omits narration"
              + " but keeps music/effects.");
      return;
    }
    Path cache = Path.of(System.getProperty("user.home"), ".jenga-code", "speech-cache");
    if (args[0].equals("clear-speech-cache")) {
      System.out.println(
          "Removed "
              + new SpeechCache(args.length > 1 ? Path.of(args[1]) : cache).clear()
              + " cached clips");
      return;
    }
    if (args[0].equals("--list-voices") || args[0].equals("voices")) {
      var provider =
          new OpenAISpeechProvider(
              new io.jengacode.eduvideo.app.StudioSettings(),
              new io.jengacode.eduvideo.production.Cancellation());
      for (var voice : provider.voices())
        System.out.println(voice.id() + "\t" + voice.language() + "\t" + voice.name());
      return;
    }
    if (args[0].equals("--test-voice")) {
      if (args.length < 2) throw new IllegalArgumentException("--test-voice VOICE_ID [output.wav]");
      var provider =
          new OpenAISpeechProvider(
              new io.jengacode.eduvideo.app.StudioSettings(),
              new io.jengacode.eduvideo.production.Cancellation());
      Path out = Path.of(args.length > 2 ? args[2] : "voice-test.wav");
      Files.write(
          out,
          provider.synthesize(
              "Welcome to Jenga Code EduVideo Studio. Let us explore mathematics together.",
              new VoiceSettings("en-GB", args[1], 1, 0, false)),
          StandardOpenOption.CREATE_NEW);
      System.out.println("Wrote " + out.toAbsolutePath());
      return;
    }
    if (args[0].equals("preview") && args.length <= 2) {
      PreviewApp.open(args.length == 2 ? Path.of(args[1]) : null);
      return;
    }
    if (args.length < 2) throw new IllegalArgumentException("Missing lesson XML; use --help");
    Path source = Path.of(args[1]);
    String xml = Files.readString(source);
    var productionParser = new io.jengacode.eduvideo.xml.ProductionParser();
    var production =
        productionParser.isProduction(xml) ? productionParser.parse(xml, source) : null;
    var project = production == null ? new ProjectParser().parse(xml, source) : null;
    var options = new HashMap<String, String>();
    var positional = new ArrayList<String>();
    for (int i = 2; i < args.length; i++) {
      String a = args[i];
      if (a.equals("--no-speech")) {
        options.put(a, "true");
        continue;
      }
      if (a.startsWith("--")) {
        if (!Set.of("--time", "--profile", "--from", "--to", "--scene", "--cache", "--voice")
                .contains(a)
            || i + 1 >= args.length)
          throw new IllegalArgumentException("Unknown/incomplete option " + a);
        if (options.put(a, args[++i]) != null)
          throw new IllegalArgumentException("Duplicate option " + a);
      } else positional.add(a);
    }
    var profile =
        ProductionBuilder.Profile.valueOf(
            options
                .getOrDefault("--profile", args[0].equals("preview") ? "preview" : "final")
                .toUpperCase(Locale.ROOT));
    if (options.containsKey("--cache")) cache = Path.of(options.get("--cache"));
    if (production != null) {
      if (options.containsKey("--scene"))
        throw new IllegalArgumentException(
            "Use --from/--to for a production, or render the selected part XML with --scene.");
      if (args[0].equals("validate")) {
        System.out.println(
            "Valid production: "
                + production.parts().size()
                + " parts, "
                + production.duration()
                + " seconds");
        return;
      }
      if (args[0].equals("frame") || args[0].equals("preview-frame")) {
        boolean timed = options.containsKey("--time");
        if (positional.size() != (timed ? 1 : 2))
          throw new IllegalArgumentException("frame production.xml --time SECONDS output.png");
        var outputSettings = timed ? profile.settings() : production.settings();
        int index =
            timed
                ? (int) Math.floor(Double.parseDouble(options.get("--time")) * outputSettings.fps())
                : Integer.parseInt(positional.get(0));
        Path out = Path.of(positional.get(timed ? 0 : 1));
        if (Files.exists(out)) throw new IllegalArgumentException("Output exists: " + out);
        Files.createDirectories(out.toAbsolutePath().getParent());
        ImageIO.write(
            new io.jengacode.eduvideo.production.FrameSequence(production, outputSettings)
                .renderFrame(index),
            "png",
            out.toFile());
        return;
      }
      if (!Set.of("build", "render", "preview").contains(args[0]))
        throw new IllegalArgumentException("Unknown command: " + args[0]);
      if (positional.size() > 1) throw new IllegalArgumentException("Expected one MP4 output path");
      var settings = new io.jengacode.eduvideo.app.StudioSettings();
      if (options.containsKey("--voice")) settings.set("openai.voice", options.get("--voice"));
      var token = new io.jengacode.eduvideo.production.Cancellation();
      try {
        new ProductionBuilder(
                settings,
                token,
                new io.jengacode.eduvideo.production.ProductionListener() {
                  /** Prints real stage transitions for automated jobs. */
                  public void stage(String name) {
                    System.out.println(name);
                  }

                  /** Samples real chapter/global time for terminal progress. */
                  public void part(io.jengacode.eduvideo.production.PartProgress p) {
                    if (p.completed() == 1 || p.completed() == p.total())
                      System.out.printf(
                          "Part %d/%d %s: %d/%d frames%n",
                          p.index(), p.count(), p.title(), p.completed(), p.total());
                  }
                })
            .build(
                production,
                positional.isEmpty() ? production.output() : Path.of(positional.get(0)),
                profile,
                cache,
                options.containsKey("--no-speech"),
                Double.parseDouble(options.getOrDefault("--from", "0")),
                Double.parseDouble(
                    options.getOrDefault("--to", Double.toString(production.duration()))));
      } finally {
        token.finish();
      }
      return;
    }
    switch (args[0]) {
      case "validate" -> {
        AudioMixer.preflight();
        System.out.printf(
            Locale.ROOT,
            "Valid: %d scenes, %.2f seconds, %d narration cues. FFmpeg available.%n",
            project.scenes().size(),
            project.duration(),
            project.audioTimeline().narration().size());
        if (!project.audioTimeline().narration().isEmpty())
          System.out.println(
              "OpenAI settings and actual voice duration are checked during build, before"
                  + " rendering.");
      }
      case "frame", "preview-frame" -> {
        boolean timed = options.containsKey("--time");
        if (positional.size() != (timed ? 1 : 2))
          throw new IllegalArgumentException("frame lesson.xml --time SECONDS output.png");
        var renderer =
            timed ? new VideoRenderer(project, profile.settings()) : new VideoRenderer(project);
        int index =
            timed
                ? (int)
                    Math.floor(Double.parseDouble(options.get("--time")) * profile.settings().fps())
                : Integer.parseInt(positional.get(0));
        Path output = Path.of(positional.get(timed ? 0 : 1));
        if (Files.exists(output)) throw new IllegalArgumentException("Output exists: " + output);
        Files.createDirectories(output.toAbsolutePath().getParent());
        if (!ImageIO.write(renderer.renderFrame(index), "png", output.toFile()))
          throw new IllegalStateException("PNG writer unavailable");
        System.out.println("Wrote " + output);
      }
      case "build", "render", "preview" -> {
        if (positional.size() > 1) throw new IllegalArgumentException("Expected one output path");
        Path output = Path.of(positional.isEmpty() ? "lesson.mp4" : positional.get(0));
        if (args[0].equals("render") && !output.toString().endsWith(".mp4")) {
          new VideoRenderer(project).renderAllFrames(output);
          return;
        }
        double from = Double.parseDouble(options.getOrDefault("--from", "0")),
            to =
                Double.parseDouble(
                    options.getOrDefault("--to", Double.toString(project.duration())));
        if (options.containsKey("--scene")) {
          double offset = 0;
          boolean found = false;
          for (var scene : project.scenes()) {
            if (scene.id().equals(options.get("--scene"))) {
              from = offset;
              to = offset + scene.duration();
              found = true;
              break;
            }
            offset += scene.duration();
          }
          if (!found) throw new IllegalArgumentException("Unknown scene");
        }
        var settings = new io.jengacode.eduvideo.app.StudioSettings();
        if (options.containsKey("--voice")) settings.set("openai.voice", options.get("--voice"));
        new ProductionBuilder(
                settings,
                new io.jengacode.eduvideo.production.Cancellation(),
                new io.jengacode.eduvideo.production.ProductionListener() {
                  /** Receives an actual production-stage transition from the shared pipeline. */
                  public void stage(String name) {
                    System.out.println(name);
                  }

                  /** Receives a completed engine frame and its measured production counters. */
                  public void frame(
                      java.awt.image.BufferedImage image,
                      int done,
                      int total,
                      double time,
                      double elapsed) {
                    if (done == total || done % 60 == 0)
                      System.out.printf(
                          "Frames %d/%d (%.1f%%)%n", done, total, 100.0 * done / total);
                  }

                  /** Receives actual speech-cache provenance and measured clip duration. */
                  public void speech(String id, boolean hit, double seconds) {
                    System.out.println(
                        id + ": " + (hit ? "cached" : "generated") + " " + seconds + " s");
                  }

                  /**
                   * Shows measured output size and exposes local playback, folder opening and path
                   * copying.
                   */
                  public void completed(Path path) {
                    System.out.println("Completed " + path);
                  }
                })
            .build(project, output, profile, cache, options.containsKey("--no-speech"), from, to);
      }
      default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
    }
  }
}
