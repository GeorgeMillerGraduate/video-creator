/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.ui;

import io.jengacode.eduvideo.app.ApplicationContext;
import io.jengacode.eduvideo.audio.AudioTimeline;
import io.jengacode.eduvideo.production.ProductionProject;
import io.jengacode.eduvideo.xml.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

/**
 * Chapter management edits the authoritative XML through the editor's undo history. No parallel
 * document is saved.
 */
public final class ProductionPartsPane extends BorderPane {
  private final ListView<ProductionProject.Part> list = new ListView<>();
  private final Label summary = new Label("Open or create a production manifest");
  private ProductionProject production;
  private final ApplicationContext context;
  private final Consumer<String> edit;
  private final ToolBar actions;

  /**
   * Wires chapter operations to the existing editor, preview, render dialog and background workers.
   */
  public ProductionPartsPane(
      ApplicationContext context,
      Consumer<String> edit,
      Consumer<Path> open,
      DoubleConsumer preview,
      Consumer<ProductionProject.Part> renderPart,
      Runnable renderAll,
      Runnable save) {
    this.context = context;
    this.edit = edit;
    setMinSize(280, 140);
    list.setCellFactory(
        v ->
            new ListCell<>() {
              /** Displays both chapter-local duration and its production start. */
              protected void updateItem(ProductionProject.Part p, boolean empty) {
                super.updateItem(p, empty);
                setText(
                    empty || p == null
                        ? null
                        : String.format(
                            Locale.ROOT,
                            "%02d   %s%n        %s duration  ·  starts %s",
                            getIndex() + 1,
                            p.title(),
                            time(p.project().duration()),
                            time(p.offset())));
              }
            });
    actions =
        new ToolBar(
            Ui.button("Add XML part", "Append independently valid lessons", this::add),
            Ui.button("Remove", "Remove the reference; keep the source XML", () -> change(-2)),
            Ui.button("Up", "Move chapter earlier", () -> change(-1)),
            Ui.button("Down", "Move chapter later", () -> change(1)),
            Ui.button("Save production", "Save the manifest", save),
            Ui.button("Render complete", "Render one continuous MP4", renderAll));
    var selected =
        new ToolBar(
            Ui.button(
                "Open selected",
                "Edit this part independently",
                () -> selected(p -> open.accept(p.source()))),
            Ui.button(
                "Preview selected",
                "Seek to this chapter on the complete production timeline",
                () -> selected(p -> preview.accept(p.offset()))),
            Ui.button(
                "Render selected", "Render this lesson independently", () -> selected(renderPart)),
            Ui.button(
                "Global music",
                "Choose a looping music bed for the entire production",
                this::music));
    setTop(new VBox(8, summary, actions));
    setCenter(list);
    setBottom(selected);
    getStyleClass().add("production-parts");
  }

  /**
   * Replaces display metadata after the XML has been validated; stale asynchronous edits are
   * discarded.
   */
  public void project(ProductionProject value) {
    int selected = list.getSelectionModel().getSelectedIndex();
    production = value;
    list.getItems().setAll(value == null ? List.of() : value.parts());
    actions.setDisable(value == null);
    getBottom().setDisable(value == null);
    if (value != null) {
      summary.setText(
          value.title()
              + "  ·  "
              + value.parts().size()
              + " parts  ·  Total "
              + time(value.duration()));
      list.getSelectionModel().select(Math.max(0, Math.min(selected, value.parts().size() - 1)));
    } else summary.setText("Open a manifest to manage its chapters");
  }

  /** Runs a chapter action only when the list has a valid selection. */
  private void selected(Consumer<ProductionProject.Part> action) {
    var p = list.getSelectionModel().getSelectedItem();
    if (p != null) action.accept(p);
  }

  /** Loads added lessons off-thread and discards stale asynchronous edits. */
  private void add() {
    if (production == null) return;
    var chooser = new FileChooser();
    chooser
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("EduVideo XML parts", "*.xml"));
    var files = chooser.showOpenMultipleDialog(getScene().getWindow());
    if (files == null) return;
    var snapshot = production;
    context.work(
        () -> {
          var next = new ArrayList<>(snapshot.parts());
          for (var f : files) {
            var p = new ProjectParser().parse(f.toPath());
            next.add(new ProductionProject.Part(f.toPath(), f.getName(), p, 0));
          }
          return rewritten(snapshot, next, snapshot.music());
        },
        xml -> {
          if (production == snapshot) edit.accept(xml);
        },
        e -> Ui.error("Cannot add XML part", e));
  }

  /** Changes references only; source lesson files remain untouched. */
  private void change(int direction) {
    if (production == null) return;
    int index = list.getSelectionModel().getSelectedIndex();
    if (index < 0) return;
    var next = new ArrayList<>(production.parts());
    if (direction == -2) {
      if (next.size() == 1) {
        Ui.error(
            "Keep one part",
            new IllegalArgumentException("A production needs at least one XML part."));
        return;
      }
      next.remove(index);
    } else {
      int to = index + direction;
      if (to < 0 || to >= next.size()) return;
      Collections.swap(next, index, to);
    }
    try {
      edit.accept(rewritten(production, next, production.music()));
    } catch (IllegalArgumentException e) {
      Ui.error("Cannot update chapters", e);
    }
  }

  /** Collects a local asset and gain for a whole-production looping bed. */
  private void music() {
    if (production == null) return;
    var chooser = new FileChooser();
    chooser
        .getExtensionFilters()
        .add(
            new FileChooser.ExtensionFilter("Audio", "*.wav", "*.mp3", "*.flac", "*.ogg", "*.m4a"));
    var file = chooser.showOpenDialog(getScene().getWindow());
    if (file == null) return;
    var d = new TextInputDialog("0.15");
    d.setHeaderText("Global music volume (0–4). Loops for the whole production.");
    Ui.theme(d);
    d.showAndWait()
        .ifPresent(
            value -> {
              try {
                var t =
                    new AudioTimeline.Track(
                        file.toPath(),
                        0,
                        production.duration(),
                        Double.parseDouble(value),
                        0,
                        0,
                        true,
                        true);
                edit.accept(rewritten(production, production.parts(), List.of(t)));
              } catch (Exception e) {
                Ui.error("Invalid global music", e);
              }
            });
  }

  /** Keeps full-length music aligned with a changed total while preserving partial regions. */
  private static String rewritten(
      ProductionProject p, List<ProductionProject.Part> parts, List<AudioTimeline.Track> music) {
    double total = parts.stream().mapToDouble(x -> x.project().duration()).sum();
    var tracks =
        music.stream()
            .map(
                t ->
                    Math.abs(t.end() - p.duration()) < 1e-8
                        ? new AudioTimeline.Track(
                            t.source(),
                            t.start(),
                            total,
                            t.volume(),
                            t.fadeIn(),
                            t.fadeOut(),
                            t.loop(),
                            t.music())
                        : t)
            .toList();
    return ProductionWriter.write(p.source(), p.title(), p.settings(), p.output(), parts, tracks);
  }

  /** Formats a source duration for chapter lists without losing hours. */
  public static String time(double seconds) {
    long s = (long) Math.floor(seconds);
    return s >= 3600
        ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        : String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
  }
}
