/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.app;

import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;

/**
 * Owns background workers for one Studio window and marshals only completion callbacks to JavaFX.
 */
public final class ApplicationContext implements AutoCloseable {
  public final StudioSettings settings = new StudioSettings();
  public final ExecutorService workers =
      Executors.newFixedThreadPool(
          3,
          r -> {
            var t = new Thread(r, "studio-worker");
            t.setDaemon(true);
            return t;
          });

  /** Runs blocking work outside JavaFX and returns its result on the UI thread. */
  public <T> void work(Callable<T> task, Consumer<T> success, Consumer<Throwable> failure) {
    workers.submit(
        () -> {
          try {
            T result = task.call();
            Platform.runLater(() -> success.accept(result));
          } catch (Throwable e) {
            Platform.runLater(() -> failure.accept(e));
          }
        });
  }

  /** Interrupts pending tasks when the owning application closes. */
  public void close() {
    workers.shutdownNow();
  }
}
