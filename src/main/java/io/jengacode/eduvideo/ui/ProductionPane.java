/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.ui;

import io.jengacode.eduvideo.production.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.animation.AnimationTimer;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

/** Latest-frame mailbox sampled at 10 Hz. Full-resolution frames are never queued onto JavaFX. */
public final class ProductionPane extends BorderPane implements ProductionListener, AutoCloseable {
  /** Latest completed production frame and measured statistics; never accumulated in a list. */
  private record Frame(BufferedImage image, int done, int total, double time, double elapsed) {}

  /** Bounded log mailbox entry emitted by a worker. */
  private record Notice(String category, String text) {}

  private final AtomicReference<Frame> latest = new AtomicReference<>();
  private final AtomicReference<String> stage = new AtomicReference<>();
  private final BlockingQueue<Notice> notices = new ArrayBlockingQueue<>(500);
  private final ImageView view = new ImageView();
  private final Label operation = new Label("No production running"),
      stats = new Label("Queue a project to begin");
  private final ProgressBar progress = new ProgressBar(0);
  private final HBox filmstrip = new HBox(6);
  private final AnimationTimer timer;
  private final LogPane log;
  private final AtomicReference<PartProgress> chapter = new AtomicReference<>();
  private final Label chapterLabel = new Label();
  private final ProgressBar chapterProgress = new ProgressBar(0);
  private final AtomicReference<Path> completed = new AtomicReference<>();
  private Runnable cancel = () -> {};
  private java.util.function.Consumer<String> progressStatus = s -> {};

  /** Receives measured status text on JavaFX at the monitor refresh rate. */
  public void onProgress(java.util.function.Consumer<String> callback) {
    progressStatus = callback;
  }

  /** Creates real progress indicators and a bounded twelve-thumbnail filmstrip. */
  public ProductionPane(LogPane log) {
    this.log = log;
    view.setPreserveRatio(true);
    setMinSize(0, 0);
    var picture = new StackPane(view);
    picture.setMinSize(0, 0);
    picture.setPrefWidth(280);
    view.fitWidthProperty().bind(picture.widthProperty().subtract(12));
    view.fitHeightProperty().bind(picture.heightProperty().subtract(12));
    chapterProgress.setMaxWidth(Double.MAX_VALUE);
    operation.setWrapText(true);
    stats.setWrapText(true);
    chapterLabel.setWrapText(true);
    progress.setMaxWidth(Double.MAX_VALUE);
    var info =
        new VBox(
            8,
            new Label("PRODUCTION MONITOR"),
            operation,
            stats,
            new Label("Overall frames"),
            progress,
            chapterLabel,
            chapterProgress,
            Ui.button(
                "Cancel render",
                "Stop the current render and its subprocesses",
                () -> cancel.run()));
    info.getStyleClass().add("panel");
    info.setMinWidth(200);
    var infoScroll = new ScrollPane(info);
    infoScroll.setFitToWidth(true);
    infoScroll.setMinSize(0, 0);
    var content = new SplitPane(picture, infoScroll);
    content.setDividerPositions(.35);
    content.setMinSize(0, 0);
    setCenter(content);
    var scroll = new ScrollPane(filmstrip);
    scroll.setFitToHeight(true);
    scroll.setMinHeight(0);
    scroll.setPrefHeight(88);
    scroll.setMaxHeight(88);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    var showFilmstrip = new CheckBox("Recent frames");
    showFilmstrip.setSelected(false);
    scroll.visibleProperty().bind(showFilmstrip.selectedProperty());
    scroll.managedProperty().bind(showFilmstrip.selectedProperty());
    setBottom(new VBox(4, showFilmstrip, scroll));
    timer =
        new AnimationTimer() {
          long last;

          /**
           * Samples pending state on a JavaFX animation pulse without queuing every engine frame.
           */
          public void handle(long now) {
            if (now - last < 100_000_000) return;
            last = now;
            String s = stage.getAndSet(null);
            if (s != null) {
              operation.setText(s);
              log.add("INFO", s);
            }
            Notice n;
            int limit = 30;
            while (limit-- > 0 && (n = notices.poll()) != null) log.add(n.category, n.text);
            PartProgress part = chapter.getAndSet(null);
            if (part != null) {
              chapterLabel.setText(
                  String.format(
                      Locale.ROOT,
                      "Part %d / %d · %s · %.1f%%\nLocal %.3f s · Production %.3f s",
                      part.index(),
                      part.count(),
                      part.title(),
                      100 * part.fraction(),
                      part.localTime(),
                      part.productionTime()));
              chapterProgress.setProgress(part.fraction());
            }
            Frame f = latest.getAndSet(null);
            if (f != null) display(f);
            Path result = completed.getAndSet(null);
            if (result != null) {
              operation.setText("Render complete: " + result.getFileName());
              progress.setProgress(1);
            }
          }
        };
    timer.start();
  }

  /** Sets the cancellation action for the queue's current job. */
  public void onCancel(Runnable action) {
    cancel = action;
  }

  /** Clears previous job statistics before new production begins. */
  public void reset() {
    latest.set(null);
    chapter.set(null);
    chapterLabel.setText("");
    chapterProgress.setProgress(0);
    completed.set(null);
    filmstrip.getChildren().clear();
    progress.setProgress(0);
    view.setImage(null);
    stats.setText("Preparing production");
  }

  /**
   * Updates measured statistics and retains only reduced thumbnails beyond the latest displayed
   * frame.
   */
  private void display(Frame f) {
    view.setImage(SwingFXUtils.toFXImage(f.image, null));
    var measured = new ProductionProgress(f.done, f.total, f.elapsed);
    double fps = measured.fps(), eta = measured.remaining();
    progress.setProgress(measured.fraction());
    progressStatus.accept(
        String.format(
            java.util.Locale.ROOT,
            "Rendering frame %d/%d | %.1f%% | %.1f FPS | ETA %.1f s",
            f.done,
            f.total,
            100 * measured.fraction(),
            fps,
            eta));
    stats.setText(
        String.format(
            Locale.ROOT,
            "Frame %,d / %,d   ·   %.1f%%\n"
                + "Source %.3f s   ·   %.1f frames/s\n"
                + "Elapsed %.1f s   ·   ETA %.1f s",
            f.done,
            f.total,
            100.0 * f.done / f.total,
            f.time,
            fps,
            f.elapsed,
            eta));
    // Reduced thumbnails alone survive beyond the latest display frame.
    var thumb = new BufferedImage(128, 72, BufferedImage.TYPE_INT_RGB);
    var g = thumb.createGraphics();
    try {
      g.drawImage(f.image, 0, 0, 128, 72, null);
    } finally {
      g.dispose();
    }
    var fx = SwingFXUtils.toFXImage(thumb, null);
    var thumbnailView = new ImageView(fx);
    thumbnailView.setFitWidth(96);
    thumbnailView.setFitHeight(54);
    var b = new Button(Integer.toString(f.done), thumbnailView);
    b.setContentDisplay(ContentDisplay.TOP);
    int frameNumber = f.done;
    double frameTime = f.time;
    b.setOnAction(
        e -> {
          var a =
              new Alert(
                  Alert.AlertType.INFORMATION,
                  "Frame " + frameNumber + " · " + frameTime + " seconds (thumbnail)");
          a.setGraphic(new ImageView(fx));
          a.show();
        });
    filmstrip.getChildren().add(b);
    if (filmstrip.getChildren().size() > 12) filmstrip.getChildren().remove(0);
  }

  /** Records the latest actual stage. */
  public void stage(String value) {
    stage.set(value);
  }

  /** Replaces the frame mailbox, retaining only the most recent engine image. */
  public void frame(BufferedImage image, int done, int total, double time, double elapsed) {
    latest.set(new Frame(image, done, total, time, elapsed));
  }

  /** Retains only the most recent actual chapter counters for the next UI pulse. */
  public void part(PartProgress value) {
    chapter.set(value);
  }

  /** Records the actual cache result and measured speech duration. */
  public void speech(String id, boolean hit, double seconds) {
    message(
        hit ? "CACHE" : "SPEECH",
        String.format(Locale.ROOT, "%s · %s · %.2f s", id, hit ? "cached" : "generated", seconds));
  }

  /** Adds a bounded diagnostic without blocking production. */
  public void message(String category, String text) {
    notices.offer(new Notice(category, text));
  }

  /** Records completion after the final output has been published. */
  public void completed(Path path) {
    completed.set(path);
  }

  /** Stops UI sampling at application shutdown. */
  public void close() {
    timer.stop();
    latest.set(null);
    notices.clear();
    view.setImage(null);
    filmstrip.getChildren().clear();
  }
}
