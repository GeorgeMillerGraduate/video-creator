/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.ui;

import io.jengacode.eduvideo.app.*;
import io.jengacode.eduvideo.export.ProductionBuilder;
import io.jengacode.eduvideo.production.RenderQueue;
import io.jengacode.eduvideo.project.*;
import io.jengacode.eduvideo.video.VideoProject;
import java.nio.file.*;
import java.util.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

/** Focused project and delivery dialogs that expose only supported engine parameters. */
public final class ProjectDialogs {
  /** Prevents construction of this static dialog/control utility. */
  private ProjectDialogs() {}

  /** Input returned by the wizard; actual I/O and parsing run on the caller's worker. */
  public record NewProject(
      Path path,
      String template,
      String title,
      int width,
      int height,
      double fps,
      double duration) {}

  /** Collects a template and logical authoring dimensions, validating scalars before closing. */
  public static Optional<NewProject> create(Window owner) {
    var d = new Dialog<NewProject>();
    d.initOwner(owner);
    d.setTitle("New educational video");
    d.setHeaderText("CREATE AN XML VIDEO PROJECT");
    var ok = new ButtonType("Create project", ButtonBar.ButtonData.OK_DONE);
    d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
    var name = new TextField("Untitled lesson");
    var file = new TextField(Path.of("projects", "untitled.xml").toAbsolutePath().toString());
    var template = new ComboBox<String>();
    template.getItems().addAll(ProjectTemplates.NAMES);
    template.setValue("Mathematics");
    var width = new TextField("1920");
    var height = new TextField("1080");
    var fps = new TextField("60");
    var duration = new TextField("20");
    var grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(12);
    grid.addRow(0, new Label("Title"), name);
    grid.addRow(1, new Label("Template"), template);
    grid.addRow(
        2,
        new Label("XML location"),
        file,
        Ui.button(
            "Browse",
            "Choose XML destination",
            () -> {
              var c = new FileChooser();
              c.setInitialFileName("lesson.xml");
              var f = c.showSaveDialog(owner);
              if (f != null) file.setText(f.toString());
            }));
    grid.addRow(3, new Label("Logical resolution"), new HBox(8, width, new Label("×"), height));
    grid.addRow(4, new Label("Frame rate"), fps);
    grid.addRow(5, new Label("Duration (seconds)"), duration);
    d.getDialogPane().setContent(grid);
    Ui.theme(d);
    d.setResultConverter(
        b ->
            b == ok
                ? new NewProject(
                    Path.of(file.getText()),
                    template.getValue(),
                    name.getText(),
                    Integer.parseInt(width.getText()),
                    Integer.parseInt(height.getText()),
                    Double.parseDouble(fps.getText()),
                    Double.parseDouble(duration.getText()))
                : null);
    d.getDialogPane()
        .lookupButton(ok)
        .addEventFilter(
            javafx.event.ActionEvent.ACTION,
            e -> {
              try {
                if (Files.exists(Path.of(file.getText())))
                  throw new IllegalArgumentException("Choose a new XML filename");
                if (Integer.parseInt(width.getText()) < 320
                    || Integer.parseInt(height.getText()) < 180
                    || Double.parseDouble(fps.getText()) <= 0
                    || Double.parseDouble(duration.getText()) <= 0)
                  throw new IllegalArgumentException(
                      "Dimensions, FPS and duration must be positive");
              } catch (Exception ex) {
                e.consume();
                Ui.error("Check project settings", ex);
              }
            });
    return d.showAndWait();
  }

  /**
   * Configures a real output profile and source range; existing files remain protected by the
   * engine.
   */
  public static Optional<RenderQueue.Job> render(
      Window owner, Path source, VideoProject project, ApplicationContext context) {
    return render(owner, source, project, null, context);
  }

  /** Configures a multipart job through the same profile, voice, range and queue controls. */
  public static Optional<RenderQueue.Job> renderProduction(
      Window owner,
      io.jengacode.eduvideo.production.ProductionProject production,
      ApplicationContext context) {
    return render(owner, production.source(), null, production, context);
  }

  /** Shares supported delivery controls between single lessons and manifest snapshots. */
  private static Optional<RenderQueue.Job> render(
      Window owner,
      Path source,
      VideoProject project,
      io.jengacode.eduvideo.production.ProductionProject production,
      ApplicationContext context) {
    double duration = production == null ? project.duration() : production.duration();
    var d = new Dialog<RenderQueue.Job>();
    d.initOwner(owner);
    StudioSettings settings = context.settings;
    d.setTitle("Render video");
    d.setHeaderText("PRODUCTION SETTINGS");
    var start = new ButtonType("Start render", ButtonBar.ButtonData.OK_DONE);
    d.getDialogPane().getButtonTypes().addAll(start, ButtonType.CANCEL);
    var profile = new ComboBox<ProductionBuilder.Profile>();
    profile.getItems().addAll(ProductionBuilder.Profile.values());
    profile.setValue(ProductionBuilder.Profile.FINAL);
    var info = new Label();
    Runnable describe =
        () -> {
          var p = profile.getValue().settings();
          info.setText(
              p.width()
                  + " × "
                  + p.height()
                  + " · "
                  + p.fps()
                  + " FPS\nH.264 · AAC stereo · 48 kHz");
        };
    profile.setOnAction(e -> describe.run());
    describe.run();
    var output =
        new TextField(
            source
                .toAbsolutePath()
                .getParent()
                .resolve("output")
                .resolve(
                    source.getFileName().toString().replaceFirst("\\.xml$", "")
                        + "-"
                        + System.currentTimeMillis()
                        + ".mp4")
                .toString());
    if (production != null) output.setText(production.output().toString());
    var speech = new CheckBox("Generate OpenAI narration (API usage billed)");
    speech.setSelected(true);
    var from = new TextField("0");
    var to = new TextField(Double.toString(duration));
    var grid = new GridPane();
    grid.setHgap(12);
    grid.setVgap(12);
    grid.addRow(0, new Label("Render profile"), profile);
    grid.addRow(1, new Label("Delivery"), info);
    grid.addRow(
        2,
        new Label("MP4 output"),
        output,
        Ui.button(
            "Browse",
            "Choose output file",
            () -> {
              var c = new FileChooser();
              c.setInitialFileName("video.mp4");
              var f = c.showSaveDialog(owner);
              if (f != null) output.setText(f.toString());
            }));
    grid.addRow(3, new Label("Audio"), speech);
    var voices = new ComboBox<io.jengacode.eduvideo.audio.SpeechVoice>();
    voices.setPromptText("Project / saved default");
    voices
        .getItems()
        .setAll(io.jengacode.eduvideo.audio.OpenAIVoices.voices(settings.speechModel()));
    var voiceStatus = new Label("OpenAI · " + settings.speechModel());
    grid.addRow(4, new Label("Narration voice"), new VBox(6, voices, voiceStatus));
    var queued = new CheckBox("Queue only; start later from Render queue");
    grid.addRow(7, new Label("Queue"), queued);
    grid.addRow(5, new Label("Start / end seconds"), new HBox(8, from, to));
    grid.addRow(
        6,
        new Label("Narration timing"),
        new Label(
            "Explicit XML voices/rates remain authoritative.\n"
                + "Choose defaults in the Narration panel."));
    d.getDialogPane().setContent(grid);
    Ui.theme(d);
    d.setResultConverter(
        b -> {
          if (b != start) return null;
          var jobSettings = settings.copy();
          if (voices.getValue() != null) jobSettings.set("openai.voice", voices.getValue().id());
          var job =
              production != null
                  ? new RenderQueue.Job(
                      production.title(),
                      production,
                      Path.of(output.getText()),
                      profile.getValue(),
                      !speech.isSelected(),
                      Double.parseDouble(from.getText()),
                      Double.parseDouble(to.getText()),
                      jobSettings)
                  : new RenderQueue.Job(
                      source.getFileName().toString(),
                      project,
                      Path.of(output.getText()),
                      profile.getValue(),
                      !speech.isSelected(),
                      Double.parseDouble(from.getText()),
                      Double.parseDouble(to.getText()),
                      jobSettings);
          job.startImmediately = !queued.isSelected();
          return job;
        });
    d.getDialogPane()
        .lookupButton(start)
        .addEventFilter(
            javafx.event.ActionEvent.ACTION,
            e -> {
              try {
                double a = Double.parseDouble(from.getText()), b = Double.parseDouble(to.getText());
                if (!Double.isFinite(a + b) || a < 0 || b <= a || b > duration)
                  throw new IllegalArgumentException("Choose a range within the lesson");
                if (Files.exists(Path.of(output.getText())))
                  throw new IllegalArgumentException(
                      "Output already exists; choose a new filename");
              } catch (Exception ex) {
                e.consume();
                Ui.error("Check render settings", ex);
              }
            });
    return d.showAndWait();
  }
}
