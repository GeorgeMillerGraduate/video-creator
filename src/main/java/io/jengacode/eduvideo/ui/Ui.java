/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.ui;

import java.io.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Shared compact controls and expandable diagnostic dialogs for a consistent desktop interface. */
public final class Ui {
  /** Prevents construction of this static dialog/control utility. */
  private Ui() {}

  /** Creates a toolbar/action button with a tooltip. */
  public static Button button(String text, String tip, Runnable action) {
    var b = new Button(text);
    b.setTooltip(new Tooltip(tip));
    b.setOnAction(e -> action.run());
    return b;
  }

  /** Creates a named panel with external CSS styling. */
  public static VBox panel(String title, Node content) {
    var label = new Label(title);
    label.getStyleClass().add("panel-title");
    var box = new VBox(10, label, content);
    box.getStyleClass().add("panel");
    VBox.setVgrow(content, Priority.ALWAYS);
    return box;
  }

  /** Explains the failure first; stack traces remain behind the Details expander. */
  public static void error(String operation, Throwable error) {
    var a = new Alert(Alert.AlertType.ERROR);
    a.setTitle("EduVideo Studio");
    a.setHeaderText(operation);
    Throwable explanation = error;
    while (explanation.getCause() != null
        && (explanation instanceof java.util.concurrent.ExecutionException
            || explanation instanceof IllegalStateException)) explanation = explanation.getCause();
    String message =
        explanation.getMessage() == null
            ? explanation.getClass().getSimpleName()
            : explanation.getMessage();
    a.setContentText(
        message.length() > 600 ? message.substring(0, 600) + "… See details." : message);
    var writer = new StringWriter();
    error.printStackTrace(new PrintWriter(writer));
    var details = new TextArea(writer.toString());
    details.setEditable(false);
    details.setPrefRowCount(12);
    var copy =
        button(
            "Copy details",
            "Copy the diagnostic text",
            () -> {
              var clipboard = new javafx.scene.input.ClipboardContent();
              clipboard.putString(writer.toString());
              javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboard);
            });
    a.getDialogPane().setExpandableContent(new VBox(8, details, copy));
    theme(a);
    a.show();
  }

  /** Asks for confirmation before deleting or discarding user work. */
  public static boolean confirm(String message) {
    var a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
    a.setHeaderText("Jenga-Code EduVideo Studio");
    return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
  }

  /** Styles a dialog with the same theme as the main workspace. */
  public static void theme(Dialog<?> dialog) {
    dialog
        .getDialogPane()
        .getStylesheets()
        .add(Ui.class.getResource("/css/eduvideo-dark.css").toExternalForm());
  }
}
