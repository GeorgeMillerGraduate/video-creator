/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.ui;

import io.jengacode.eduvideo.app.*;
import io.jengacode.eduvideo.audio.*;
import io.jengacode.eduvideo.production.Cancellation;
import java.io.*;
import java.net.URI;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javax.sound.sampled.*;

/**
 * Shared OpenAI narration form for the sidebar and Preferences, with cancellable background
 * previews.
 */
public final class VoicePane extends VBox implements AutoCloseable {
  private final ApplicationContext context;
  private final PasswordField secret = new PasswordField();
  private final TextField visibleSecret = new TextField();
  private final CheckBox show = new CheckBox("Show key");
  private final CheckBox remember = new CheckBox("Remember on this computer (not encrypted)");
  private final ComboBox<String> models = new ComboBox<>();
  private final ComboBox<SpeechVoice> voices = new ComboBox<>();
  private final Slider rate = new Slider(.25, 4, 1);
  private final TextArea sample = new TextArea("Welcome to Jenga-Code EduVideo Studio.");
  private final Label status = new Label("OpenAI narration");
  private final Button preview, connection;
  private Cancellation operation;
  private Clip clip;
  private boolean closed;

  /** Creates the persistent workspace narration panel with its own save action. */
  public VoicePane(ApplicationContext context) {
    this(context, true);
  }

  /** Creates a narration form whose changes can instead be committed by a containing dialog. */
  public VoicePane(ApplicationContext context, boolean showSave) {
    this.context = context;
    setSpacing(9);
    getStyleClass().add("panel");
    setMinWidth(0);
    remember.setMaxWidth(Double.MAX_VALUE);
    remember.setMinWidth(0);
    remember.setWrapText(true);
    status.setWrapText(true);
    var title = new Label("OPENAI / NARRATION");
    title.getStyleClass().add("panel-title");
    secret.setPromptText("Paste API key, or use OPENAI_API_KEY");
    visibleSecret.textProperty().bindBidirectional(secret.textProperty());
    visibleSecret.visibleProperty().bind(show.selectedProperty());
    visibleSecret.managedProperty().bind(visibleSecret.visibleProperty());
    secret.visibleProperty().bind(show.selectedProperty().not());
    secret.managedProperty().bind(secret.visibleProperty());
    var keyBox = new StackPane(secret, visibleSecret);
    var link = new Hyperlink("Create / manage OpenAI API key");
    link.setOnAction(
        e ->
            context.work(
                () -> {
                  if (!java.awt.Desktop.isDesktopSupported())
                    throw new IOException(
                        "Open https://platform.openai.com/api-keys in your browser.");
                  java.awt.Desktop.getDesktop()
                      .browse(URI.create("https://platform.openai.com/api-keys"));
                  return true;
                },
                ok -> {},
                error -> Ui.error("Open API key page", error)));
    models.getItems().setAll(OpenAIVoices.MODELS);
    models.setMaxWidth(Double.MAX_VALUE);
    voices.setMaxWidth(Double.MAX_VALUE);
    models.setOnAction(e -> populateVoices());
    rate.setShowTickLabels(true);
    rate.setMajorTickUnit(1);
    rate.setBlockIncrement(.05);
    sample.setWrapText(true);
    sample.setPrefRowCount(3);
    preview =
        Ui.button(
            "Play preview",
            "Generate and play speech using OpenAI; API usage is billed",
            () -> test(false));
    connection =
        Ui.button(
            "Test connection",
            "Verify access to the selected model without generating audio",
            () -> test(true));
    var note =
        new Label(
            "Narration is AI-generated. Voice previews and uncached renders use paid OpenAI API"
                + " credits. Internet access required.");
    note.setWrapText(true);
    note.getStyleClass().add("narration-note");
    getChildren()
        .addAll(
            title,
            new Label("Provider: OpenAI"),
            new Label("API key"),
            keyBox,
            show,
            remember,
            link,
            new Label("Speech model"),
            models,
            new Label("Voice"),
            voices,
            new Label("Speed"),
            rate,
            new Label("Preview text"),
            sample,
            new FlowPane(8, 8, connection, preview),
            status,
            note);
    if (showSave)
      getChildren()
          .add(
              Ui.button(
                  "Save narration settings",
                  "Use these settings for new renders",
                  () -> {
                    try {
                      save();
                      status.setText("OpenAI narration settings saved");
                    } catch (Exception e) {
                      Ui.error("Cannot save narration settings", e);
                    }
                  }));
    refresh();
  }

  /** Populates voices immediately; local installations and discovery buttons are unnecessary. */
  private void populateVoices() {
    String selected =
        voices.getValue() == null ? context.settings.speechVoice() : voices.getValue().id();
    voices.getItems().setAll(OpenAIVoices.voices(models.getValue()));
    voices.setValue(
        voices.getItems().stream()
            .filter(v -> v.id().equals(selected))
            .findFirst()
            .orElse(voices.getItems().get(0)));
  }

  /** Reloads saved/session settings when Preferences changes the active configuration. */
  public void refresh() {
    secret.setText(context.settings.configuredApiKey());
    remember.setSelected(context.settings.keyRemembered());
    String model = context.settings.speechModel();
    models.setValue(OpenAIVoices.MODELS.contains(model) ? model : OpenAIVoices.DEFAULT_MODEL);
    populateVoices();
    voices.getItems().stream()
        .filter(v -> v.id().equals(context.settings.speechVoice()))
        .findFirst()
        .ifPresent(voices::setValue);
    try {
      rate.setValue(Double.parseDouble(context.settings.get("rate", "1")));
    } catch (NumberFormatException e) {
      rate.setValue(1);
    }
    status.setText(
        context.settings.apiKey().isBlank()
            ? "Add an API key to generate narration"
            : "OpenAI configured · " + models.getValue());
  }

  /** Captures unsaved form values for a preview; no key is written to the settings file. */
  private StudioSettings draft() {
    StudioSettings settings = context.settings.copy();
    settings.setSessionApiKey(secret.getText());
    settings.set("openai.model", models.getValue());
    settings.set("openai.voice", voices.getValue().id());
    settings.set("rate", Double.toString(Math.round(rate.getValue() * 100) / 100.0));
    return settings;
  }

  /** Commits the form; credential persistence occurs only when Remember was explicitly selected. */
  public void save() throws IOException {
    StudioSettings draft = draft();
    context.settings.set("openai.model", draft.speechModel());
    context.settings.set("openai.voice", draft.speechVoice());
    context.settings.set("rate", draft.get("rate", "1"));
    context.settings.configureApiKey(secret.getText(), remember.isSelected());
    context.settings.save();
  }

  /** Calls the shared HTTP provider on a worker and plays only validated WAV data. */
  private void test(boolean connectionOnly) {
    if (operation != null || closed) return;
    StudioSettings settings = draft();
    String text = sample.getText();
    Cancellation token = new Cancellation();
    operation = token;
    preview.setDisable(true);
    connection.setDisable(true);
    status.setText(connectionOnly ? "Testing model access…" : "Generating preview…");
    context.work(
        () -> {
          var provider = new OpenAISpeechProvider(settings, token);
          if (connectionOnly) {
            provider.testConnection();
            return (Clip) null;
          }
          byte[] wav =
              provider.synthesize(
                  text,
                  new VoiceSettings(
                      "en-GB",
                      settings.speechVoice(),
                      Double.parseDouble(settings.get("rate", "1")),
                      0,
                      false));
          try (var audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            Clip result = AudioSystem.getClip();
            try {
              result.open(audio);
              return result;
            } catch (Exception error) {
              result.close();
              throw error;
            }
          }
        },
        result -> {
          operation = null;
          preview.setDisable(false);
          connection.setDisable(false);
          if (closed) {
            if (result != null) result.close();
            return;
          }
          if (clip != null) clip.close();
          clip = result;
          if (clip != null) clip.start();
          status.setText(
              connectionOnly
                  ? "Connected · model accessible (speech billing not tested)"
                  : "Playing preview…");
        },
        error -> {
          operation = null;
          preview.setDisable(false);
          connection.setDisable(false);
          if (!closed) {
            status.setText("OpenAI request failed");
            Ui.error(connectionOnly ? "Connection failed" : "Cannot preview voice", error);
          }
        });
  }

  /** Cancels an in-flight HTTP preview and closes the local playback device. */
  public void close() {
    closed = true;
    if (operation != null) operation.cancel();
    if (clip != null) clip.close();
  }
}
