/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.app;

import io.jengacode.eduvideo.ui.MainWindow;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Primary JavaFX desktop entry point. The CLI remains independently runnable without starting
 * JavaFX.
 */
public final class EduVideoStudio extends Application {
  private MainWindow window;

  /** Creates a responsive production workspace with external CSS and remembered window size. */
  @Override
  public void start(Stage stage) {
    var context = new ApplicationContext();
    window = new MainWindow(stage, context);
    var bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
    var scene =
        new Scene(
            window.root(),
            Math.min(number(context, "window.width", 1440), bounds.getWidth() * .9),
            Math.min(number(context, "window.height", 900), bounds.getHeight() * .9));
    scene.getStylesheets().add(getClass().getResource("/css/eduvideo-dark.css").toExternalForm());
    stage.setScene(scene);
    var iconCanvas = new javafx.scene.canvas.Canvas(64, 64);
    var g = iconCanvas.getGraphicsContext2D();
    g.setFill(javafx.scene.paint.Color.web("#112131"));
    g.fillRoundRect(0, 0, 64, 64, 12, 12);
    g.setFill(javafx.scene.paint.Color.web("#40bcec"));
    g.fillPolygon(new double[] {17, 17, 50}, new double[] {12, 52, 32}, 3);
    g.setFill(javafx.scene.paint.Color.web("#4cf0b3"));
    g.fillRect(12, 12, 5, 40);
    stage.getIcons().add(iconCanvas.snapshot(null, null));
    stage.setMinWidth(960);
    stage.setMinHeight(640);
    window.shortcuts(scene);
    stage.setMaximized(true);
    stage.show();
    if (!getParameters().getRaw().isEmpty()) window.open(Path.of(getParameters().getRaw().get(0)));
  }

  /** Reads a remembered window dimension and falls back safely for malformed values. */
  private static double number(ApplicationContext context, String key, double fallback) {
    try {
      return Math.max(700, Double.parseDouble(context.settings.get(key, "" + fallback)));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /** Launches the desktop application from Maven or a JavaFX-enabled runtime. */
  public static void main(String[] args) {
    launch(args);
  }
}
