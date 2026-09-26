/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.production;

import io.jengacode.eduvideo.app.*;
import io.jengacode.eduvideo.export.ProductionBuilder;
import io.jengacode.eduvideo.video.VideoProject;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.*;

/**
 * Sequential production queue; each item owns an immutable validated snapshot and distinct
 * cancellation token.
 */
public final class RenderQueue implements AutoCloseable {
  /** A queued configuration; changing the editor after submission cannot change this job. */
  public static final class Job {
    /** Whether the render dialog requested immediate sequential execution. */
    public boolean startImmediately;

    public final String name;
    public final VideoProject project;
    public final ProductionProject production;
    public final Path output;
    public final ProductionBuilder.Profile profile;
    public final boolean silent;
    public final double from, to;
    public final StudioSettings settings;
    public final StringProperty status = new SimpleStringProperty("Waiting");

    /** Captures all parameters genuinely honored by ProductionBuilder. */
    public Job(
        String name,
        VideoProject project,
        Path output,
        ProductionBuilder.Profile profile,
        boolean silent,
        double from,
        double to,
        StudioSettings settings) {
      this.name = name;
      this.project = project;
      this.production = null;
      this.output = output;
      this.profile = profile;
      this.silent = silent;
      this.from = from;
      this.to = to;
      this.settings = settings;
    }

    /** Captures a complete manifest snapshot with the same delivery policy as ordinary jobs. */
    public Job(
        String name,
        ProductionProject production,
        Path output,
        ProductionBuilder.Profile profile,
        boolean silent,
        double from,
        double to,
        StudioSettings settings) {
      this.name = name;
      this.project = null;
      this.production = production;
      this.output = output;
      this.profile = profile;
      this.silent = silent;
      this.from = from;
      this.to = to;
      this.settings = settings;
    }

    /** Displays the job and current state in the queue list. */
    public String toString() {
      return name + " · " + profile + " · " + status.get();
    }
  }

  public final ObservableList<Job> jobs =
      FXCollections.observableArrayList(j -> new javafx.beans.Observable[] {j.status});
  public final BooleanProperty busy = new SimpleBooleanProperty(false);
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "production-queue");
            t.setDaemon(true);
            return t;
          });
  private Cancellation current;
  private boolean stopped;
  private final ProductionListener listener;
  private final Consumer<Job> begin, complete;
  private final Consumer<Throwable> failure;

  /** Connects real engine events and JavaFX completion callbacks. */
  public RenderQueue(
      ProductionListener listener,
      Consumer<Job> begin,
      Consumer<Job> complete,
      Consumer<Throwable> failure) {
    this.listener = listener;
    this.begin = begin;
    this.complete = complete;
    this.failure = failure;
  }

  /** Starts the next waiting item. Must be called on JavaFX. */
  public void start() {
    if (busy.get() || stopped) return;
    Job job = jobs.stream().filter(j -> j.status.get().equals("Waiting")).findFirst().orElse(null);
    if (job == null) return;
    busy.set(true);
    current = new Cancellation();
    Cancellation token = current;
    job.status.set("Rendering");
    begin.accept(job);
    worker.submit(
        () -> {
          Throwable error = null;
          try {
            var builder = new ProductionBuilder(job.settings, token, listener);
            if (job.production != null)
              builder.build(
                  job.production,
                  job.output,
                  job.profile,
                  job.settings.cache(),
                  job.silent,
                  job.from,
                  job.to);
            else
              builder.build(
                  job.project,
                  job.output,
                  job.profile,
                  job.settings.cache(),
                  job.silent,
                  job.from,
                  job.to);
          } catch (Throwable e) {
            error = token.isCancelled() ? new CancellationException("Production cancelled") : e;
          } finally {
            token.finish();
          }
          Throwable result = error;
          Platform.runLater(
              () -> {
                busy.set(false);
                current = null;
                if (stopped) return;
                if (result == null) {
                  job.status.set("Complete");
                  complete.accept(job);
                } else {
                  job.status.set(result instanceof CancellationException ? "Cancelled" : "Failed");
                  if (!(result instanceof CancellationException)) failure.accept(result);
                }
                start();
              });
        });
  }

  /** Cancels the active item; waiting items remain queued and sequential processing continues. */
  public void cancel() {
    if (current != null) current.cancel();
  }

  /** Releases subprocesses and the queue worker during application shutdown. */
  public void close() {
    stopped = true;
    cancel();
    worker.shutdownNow();
  }
}
