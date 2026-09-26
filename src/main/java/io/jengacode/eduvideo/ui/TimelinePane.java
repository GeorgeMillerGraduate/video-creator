/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.ui;

import io.jengacode.eduvideo.video.VideoProject;
import java.util.function.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Read-only scene, speech and audio tracks derived directly from the validated engine model. */
public final class TimelinePane extends VBox {
  /** Builds proportionate clickable blocks from actual source timings. */
  public void project(VideoProject p, DoubleConsumer seek, Consumer<String> inspect) {
    getChildren().clear();
    if (p == null) return;
    setSpacing(3);
    if (!getStyleClass().contains("timeline")) getStyleClass().add("timeline");
    Pane video = row("VIDEO"), speech = row("SPEECH"), audio = row("AUDIO");
    double offset = 0;
    for (var scene : p.scenes()) {
      double start = offset;
      block(
          video,
          scene.id(),
          start,
          scene.duration(),
          p.duration(),
          () -> {
            seek.accept(start);
            inspect.accept(
                "SCENE\n"
                    + scene.id()
                    + "\nStart: "
                    + start
                    + " s\nDuration: "
                    + scene.duration()
                    + " s\nObjects: "
                    + scene.objects().size());
          },
          "video-block");
      offset += scene.duration();
    }
    for (var cue : p.audioTimeline().narration())
      block(
          speech,
          cue.id(),
          cue.start(),
          cue.maxEnd() - cue.start(),
          p.duration(),
          () -> {
            seek.accept(cue.start());
            inspect.accept(
                "NARRATION\n"
                    + cue.text()
                    + "\n\nWindow: "
                    + cue.start()
                    + " – "
                    + cue.maxEnd()
                    + " s\nVoice: "
                    + cue.voice().name()
                    + "\nRate: "
                    + cue.voice().rate()
                    + "\nMeasured duration/cache: see production log after synthesis");
          },
          "speech-block");
    for (var track : p.audioTimeline().tracks())
      block(
          audio,
          track.source().getFileName().toString(),
          track.start(),
          track.end() - track.start(),
          p.duration(),
          () -> {
            seek.accept(track.start());
            inspect.accept(
                "AUDIO\n"
                    + track.source()
                    + "\nVolume: "
                    + track.volume()
                    + "\nLoop: "
                    + track.loop());
          },
          "audio-block");
  }

  /** Shows chapter boundaries and already-offset speech/music on the complete production clock. */
  public void production(
      io.jengacode.eduvideo.production.ProductionProject p,
      DoubleConsumer seek,
      Consumer<String> inspect) {
    getChildren().clear();
    if (p == null) return;
    setSpacing(3);
    if (!getStyleClass().contains("timeline")) getStyleClass().add("timeline");
    Pane video = row("PARTS"), speech = row("SPEECH"), audio = row("AUDIO");
    for (var part : p.parts())
      block(
          video,
          part.title(),
          part.offset(),
          part.project().duration(),
          p.duration(),
          () -> {
            seek.accept(part.offset());
            inspect.accept(
                "PART\n"
                    + part.title()
                    + "\n"
                    + part.source()
                    + "\nProduction start: "
                    + part.offset()
                    + " s\nLocal duration: "
                    + part.project().duration()
                    + " s");
          },
          "video-block");
    var composed = p.audioTimeline();
    for (var cue : composed.narration())
      block(
          speech,
          cue.id(),
          cue.start(),
          cue.maxEnd() - cue.start(),
          p.duration(),
          () -> {
            seek.accept(cue.start());
            inspect.accept(
                "NARRATION\n"
                    + cue.id()
                    + "\n"
                    + cue.text()
                    + "\nProduction window: "
                    + cue.start()
                    + " – "
                    + cue.maxEnd()
                    + " s");
          },
          "speech-block");
    for (var track : composed.tracks())
      block(
          audio,
          track.source().getFileName().toString(),
          track.start(),
          track.end() - track.start(),
          p.duration(),
          () -> {
            seek.accept(track.start());
            inspect.accept(
                (track.music() ? "CONTINUOUS MUSIC" : "LOCAL AUDIO")
                    + "\n"
                    + track.source()
                    + "\nProduction interval: "
                    + track.start()
                    + " – "
                    + track.end()
                    + " s\nLoop: "
                    + track.loop()
                    + "\nVolume: "
                    + track.volume());
          },
          "audio-block");
  }

  /** Creates a labeled timeline lane with a resizable time coordinate region. */
  private Pane row(String name) {
    var pane = new Pane();
    pane.setMinHeight(24);
    pane.setPrefHeight(24);
    var label = new Label(name);
    label.setMinWidth(65);
    var h = new HBox(8, label, pane);
    HBox.setHgrow(pane, Priority.ALWAYS);
    getChildren().add(h);
    return pane;
  }

  /** Positions a source-derived interval proportionally and exposes its inspection action. */
  private void block(
      Pane pane,
      String text,
      double start,
      double length,
      double total,
      Runnable action,
      String style) {
    var b = Ui.button(text, text, action);
    b.getStyleClass().add(style);
    b.layoutXProperty().bind(pane.widthProperty().multiply(start / total));
    b.prefWidthProperty().bind(pane.widthProperty().multiply(length / total).subtract(2));
    b.setMinWidth(0);
    b.setMinHeight(0);
    b.setMaxHeight(23);
    pane.getChildren().add(b);
  }
}
