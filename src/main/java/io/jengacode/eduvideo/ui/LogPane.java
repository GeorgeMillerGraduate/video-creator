/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.ui;

import java.nio.file.*;
import java.time.*;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

/** Bounded categorized production log. All methods run on the JavaFX thread. */
public final class LogPane extends BorderPane {
  private final ObservableList<String> rows = FXCollections.observableArrayList();

  /** Creates filter, copy/save and clear controls with a 2,000-entry retention policy. */
  public LogPane() {
    var filter =
        new ComboBox<String>(
            FXCollections.observableArrayList(
                "ALL", "INFO", "WARNING", "ERROR", "RENDER", "SPEECH", "AUDIO", "CACHE"));
    filter.setValue("ALL");
    var filtered = new FilteredList<>(rows);
    var list = new ListView<>(filtered);
    list.getStyleClass().add("log-list");
    filter
        .valueProperty()
        .addListener(
            (o, a, b) ->
                filtered.setPredicate(s -> b.equals("ALL") || s.contains("  " + b + "  ")));
    setCenter(list);
    setTop(
        new HBox(
            8,
            filter,
            Ui.button(
                "Copy",
                "Copy retained log",
                () -> {
                  var c = new ClipboardContent();
                  c.putString(String.join("\n", rows));
                  Clipboard.getSystemClipboard().setContent(c);
                }),
            Ui.button(
                "Save",
                "Save retained log",
                () -> {
                  var chooser = new FileChooser();
                  chooser.setInitialFileName("eduvideo.log");
                  var f = chooser.showSaveDialog(getScene().getWindow());
                  if (f != null)
                    try {
                      Files.writeString(f.toPath(), String.join("\n", rows));
                    } catch (Exception e) {
                      Ui.error("Cannot save log", e);
                    }
                }),
            Ui.button("Clear", "Clear retained log", rows::clear)));
  }

  /** Appends a real event and discards the oldest entries at the retention limit. */
  public void add(String category, String text) {
    rows.add(LocalTime.now().withNano(0) + "  " + category + "  " + text);
    if (rows.size() > 2000) rows.remove(0, rows.size() - 2000);
  }
}
