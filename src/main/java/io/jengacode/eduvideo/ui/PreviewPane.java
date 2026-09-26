/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.ui;

import io.jengacode.eduvideo.production.*;
import io.jengacode.eduvideo.video.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javafx.animation.AnimationTimer;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Actual engine preview with a dedicated serial renderer, bounded LRU frames and coalesced seeks.
 */
public final class PreviewPane extends BorderPane implements AutoCloseable {
  private final ImageView image = new ImageView();
  private final Label clock = new Label("No project loaded");
  private final Slider seek = new Slider();
  private final StackPane viewport = new StackPane();
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "preview-renderer");
            t.setDaemon(true);
            return t;
          });
  private final AtomicReference<Request> wanted = new AtomicReference<>();
  private final AtomicReference<Rendered> ready = new AtomicReference<>();
  private final AtomicBoolean running = new AtomicBoolean();
  private final Consumer<Throwable> error;
  private final DoubleConsumer timeChanged;
  private FrameSequence project;
  private final ComboBox<String> zoom = new ComboBox<>();
  private final ScrollPane scroll = new ScrollPane(viewport);
  private long version;
  private volatile boolean closed;
  private boolean playing;
  private double time;
  private long previous;
  private boolean adjusting;
  private final AnimationTimer timer;
  private BufferedImage current;

  /** Immutable source snapshot and frame request passed to the preview worker. */
  private record Request(FrameSequence project, long version, int frame) {}

  /** Completed preview raster tagged with its document generation to reject stale work. */
  private record Rendered(long version, int frame, BufferedImage image) {}

  /**
   * Builds transport and zoom controls; work is coalesced rather than queued per mouse movement.
   */
  public PreviewPane(DoubleConsumer timeChanged, Consumer<Throwable> error) {
    this.timeChanged = timeChanged;
    this.error = error;
    image.setPreserveRatio(true);
    image.setSmooth(true);
    viewport.getStyleClass().add("preview-viewport");
    viewport.getChildren().add(image);
    viewport.setMinSize(0, 0);
    setMinSize(280, 160);
    scroll.setFitToWidth(true);
    scroll.setFitToHeight(true);
    setCenter(scroll);
    fit();
    zoom.getItems().addAll("Fit", "50%", "75%", "100%", "125%", "150%", "200%");
    zoom.setValue("Fit");
    zoom.setOnAction(e -> fit());
    var jump = new TextField();
    jump.setPromptText("Seconds");
    jump.setPrefWidth(85);
    jump.setOnAction(
        e -> {
          try {
            seek(Double.parseDouble(jump.getText()));
          } catch (NumberFormatException ex) {
            error.accept(ex);
          }
        });
    var controls =
        new FlowPane(
            6,
            6,
            Ui.button("|‹", "Beginning", () -> seek(0)),
            Ui.button("‹", "Previous frame", () -> step(-1)),
            Ui.button("Play / Pause", "Play visual preview", this::toggle),
            Ui.button(
                "Stop",
                "Stop and rewind",
                () -> {
                  playing = false;
                  seek(0);
                }),
            Ui.button("›", "Next frame", () -> step(1)),
            Ui.button(
                "›|",
                "Last frame",
                () -> {
                  if (project != null) seek(project.duration() - 1 / project.settings().fps());
                }),
            clock,
            new Region(),
            jump,
            zoom,
            Ui.button("Full", "Fullscreen preview", this::fullscreen));

    controls.getStyleClass().add("transport");
    seek.valueProperty()
        .addListener(
            (o, a, b) -> {
              if (!adjusting) seek(b.doubleValue());
            });
    setBottom(new VBox(seek, controls));
    timer =
        new AnimationTimer() {
          /**
           * Samples pending state on a JavaFX animation pulse without queuing every engine frame.
           */
          public void handle(long now) {
            if (playing && project != null && previous != 0) {
              seek(time + (now - previous) / 1e9);
              if (time >= project.duration() - 1 / project.settings().fps()) playing = false;
            }
            previous = now;
            Rendered r = ready.getAndSet(null);
            if (r != null && r.version == version) {
              current = r.image;
              image.setImage(SwingFXUtils.toFXImage(current, null));
            }
          }
        };
    timer.start();
  }

  /** Binds the preview aspect ratio to its available viewport, independent of physical DPI. */
  private void fit() {
    image.fitWidthProperty().unbind();
    image.fitHeightProperty().unbind();
    if (zoom.getValue() == null || zoom.getValue().equals("Fit")) {
      viewport.setMinSize(0, 0);
      viewport.setPrefSize(0, 0);
      image
          .fitWidthProperty()
          .bind(
              javafx.beans.binding.Bindings.createDoubleBinding(
                  () -> Math.max(1, scroll.getViewportBounds().getWidth() - 24),
                  scroll.viewportBoundsProperty()));
      image
          .fitHeightProperty()
          .bind(
              javafx.beans.binding.Bindings.createDoubleBinding(
                  () -> Math.max(1, scroll.getViewportBounds().getHeight() - 24),
                  scroll.viewportBoundsProperty()));
    } else {
      double scale = Double.parseDouble(zoom.getValue().replace("%", "")) / 100;
      double width = (project == null ? 1920 : project.settings().width()) * scale;
      double height = (project == null ? 1080 : project.settings().height()) * scale;
      image.setFitWidth(width);
      image.setFitHeight(height);
      viewport.setMinSize(width + 24, height + 24);
      viewport.setPrefSize(width + 24, height + 24);
    }
  }

  /** Replaces the preview snapshot; stale in-flight results are discarded by generation number. */
  public void project(VideoProject value) {
    sequence(value == null ? null : new FrameSequence(value, value.settings()));
  }

  /** Previews the complete manifest using its global clock and real chapter renderers. */
  public void production(ProductionProject value) {
    sequence(value == null ? null : new FrameSequence(value, value.settings()));
  }

  /** Replaces the preview generation and discards stale pending frame requests. */
  private void sequence(FrameSequence value) {
    project = value;
    wanted.set(null);
    fit();
    version++;
    playing = false;
    current = null;
    ready.set(null);
    image.setImage(null);
    seek.setMax(value == null ? 0 : value.duration());
    seek(0);
  }

  /** Requests a clamped source time; only the newest pending request is rendered. */
  public void seek(double seconds) {
    if (project == null || !Double.isFinite(seconds)) return;
    time = Math.max(0, Math.min(project.duration() - 1 / project.settings().fps(), seconds));
    adjusting = true;
    seek.setValue(time);
    adjusting = false;
    var chapter =
        project.progress(
            (int) Math.floor(time * project.settings().fps()), 0, project.frameCount());
    clock.setText(
        chapter.count() > 1
            ? String.format(
                Locale.ROOT,
                "%06.2f / %06.2f s · Part %d · Local %.2f s",
                time,
                project.duration(),
                chapter.index(),
                chapter.localTime())
            : String.format(Locale.ROOT, "%06.2f / %06.2f s", time, project.duration()));
    timeChanged.accept(time);
    int frame = (int) Math.floor(time * project.settings().fps());
    wanted.set(new Request(project, version, frame));
    pump();
  }

  /** Starts one serial worker if idle; repeated seek requests replace the pending mailbox entry. */
  private void pump() {
    if (closed || !running.compareAndSet(false, true)) return;
    worker.submit(
        () -> {
          try {
            Request r;
            while (!closed && (r = wanted.getAndSet(null)) != null) {
              render(r);
            }
          } catch (Throwable e) {
            javafx.application.Platform.runLater(() -> error.accept(e));
          } finally {
            if (closed) {
              cache.clear();
              renderer = null;
            }
            running.set(false);
            if (!closed && wanted.get() != null) pump();
          }
        });
  }

  private FrameSequence renderer;
  private long renderVersion = -1;
  private int lastFrame = -1;
  private final LinkedHashMap<Integer, BufferedImage> cache = new LinkedHashMap<>(16, .75f, true);

  /** Renders a requested snapshot through the shared engine and its bounded frame cache. */
  private void render(Request r) {
    if (renderVersion != r.version) {
      renderer =
          r.project.atOutput(
              new VideoSettings(
                  1280,
                  Math.max(
                      1,
                      (int)
                          Math.round(
                              1280.0
                                  * r.project.settings().height()
                                  / r.project.settings().width())),
                  r.project.settings().fps()));
      cache.clear();
      lastFrame = -1;
      renderVersion = r.version;
    }
    if (lastFrame == r.frame) return;
    var frame = cache.get(r.frame);
    if (frame == null) {
      frame = renderer.renderFrame(r.frame);
      cache.put(r.frame, frame);
      if (cache.size() > 8) cache.remove(cache.keySet().iterator().next());
    }
    lastFrame = r.frame;
    ready.set(new Rendered(r.version, r.frame, frame));
  }

  /** Toggles visual playback; final mixed audio is auditioned through completed video output. */
  public void toggle() {
    if (project != null) playing = !playing;
  }

  /** Moves exactly one source frame per direction unit. */
  public void step(int direction) {
    playing = false;
    if (project != null) seek(time + direction / project.settings().fps());
  }

  /** Shows the currently displayed actual image in a separate fullscreen stage. */
  public void fullscreen() {
    var view = new ImageView(image.getImage());
    view.imageProperty().bind(image.imageProperty());
    view.setPreserveRatio(true);
    var box = new StackPane(view);
    box.setStyle("-fx-background-color:black;");
    view.fitWidthProperty().bind(box.widthProperty());
    view.fitHeightProperty().bind(box.heightProperty());
    var stage = new Stage();
    stage.setScene(new Scene(box, 960, 540));
    stage.setFullScreen(true);
    stage.setOnHidden(e -> view.imageProperty().unbind());
    stage.show();
  }

  /** Returns the displayed raster for PNG export; null means no frame is ready. */
  public BufferedImage current() {
    return current;
  }

  /** Stops playback and releases background rendering resources. */
  public void close() {
    closed = true;
    timer.stop();
    wanted.set(null);
    ready.set(null);
    worker.shutdownNow();
    current = null;
    image.setImage(null);
  }
}
