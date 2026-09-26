/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.ui;

import io.jengacode.eduvideo.app.*;
import io.jengacode.eduvideo.production.Cancellation;
import java.util.List;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

/** Studio preferences with one OpenAI narration form and the existing local video tools. */
public final class PreferencesDialog {
  /** Static dialog utility. */
  private PreferencesDialog() {}

  /** Opens a draft settings dialog; previews use draft values and Save commits them. */
  public static void show(ApplicationContext context) {
    var dialog = new Dialog<ButtonType>();
    dialog.setTitle("Studio preferences");
    dialog.setHeaderText("JENGA-CODE / PREFERENCES");
    var save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
    var narration = new VoicePane(context, false);
    var scroll = new ScrollPane(narration);
    scroll.setFitToWidth(true);
    var ffmpeg = new TextField(context.settings.ffmpeg());
    var projects = new TextField(context.settings.get("projects", "examples"));
    var status = new Label("Test the encoder before production");
    status.setWrapText(true);
    var tools = new GridPane();
    tools.setHgap(12);
    tools.setVgap(12);
    tools.getStyleClass().add("panel");
    tools.addRow(
        0,
        new Label("FFmpeg executable"),
        ffmpeg,
        Ui.button(
            "Browse",
            "Locate FFmpeg",
            () -> {
              var file =
                  new FileChooser().showOpenDialog(dialog.getDialogPane().getScene().getWindow());
              if (file != null) ffmpeg.setText(file.getAbsolutePath());
            }));
    tools.addRow(
        1,
        new Label("XML library folder"),
        projects,
        Ui.button(
            "Browse",
            "Locate project library",
            () -> {
              var folder =
                  new DirectoryChooser().showDialog(dialog.getDialogPane().getScene().getWindow());
              if (folder != null) projects.setText(folder.getAbsolutePath());
            }));
    tools.addRow(2, new Label("Encoder status"), status);
    tools.add(
        Ui.button(
            "Test FFmpeg",
            "Check the configured executable",
            () -> {
              String executable = ffmpeg.getText();
              status.setText("Testing…");
              context.work(
                  () -> new Cancellation().run(List.of(executable, "-version"), 15),
                  result -> status.setText(result.lines().findFirst().orElse("Ready")),
                  error -> status.setText(error.getMessage()));
            }),
        1,
        3);
    var tabs =
        new TabPane(
            new Tab("Narration", scroll), new Tab("Projects & Rendering", new ScrollPane(tools)));
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    dialog.getDialogPane().setContent(tabs);
    var screen = Screen.getPrimary().getVisualBounds();
    dialog
        .getDialogPane()
        .setPrefSize(
            Math.min(690, screen.getWidth() - 80), Math.min(750, screen.getHeight() - 100));
    dialog.setResizable(true);
    Ui.theme(dialog);
    dialog
        .getDialogPane()
        .lookupButton(save)
        .addEventFilter(
            javafx.event.ActionEvent.ACTION,
            e -> {
              try {
                context.settings.set("ffmpeg", ffmpeg.getText().trim());
                context.settings.set("projects", projects.getText().trim());
                narration.save();
              } catch (Exception error) {
                e.consume();
                Ui.error("Cannot save preferences", error);
              }
            });
    dialog.setOnHidden(e -> narration.close());
    dialog.showAndWait();
  }
}
