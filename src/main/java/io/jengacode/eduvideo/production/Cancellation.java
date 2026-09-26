/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.production;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Per-job cancellation token and subprocess owner. No render shares another render's processes. */
public final class Cancellation {
  private volatile boolean cancelled;
  private final Set<Process> processes = ConcurrentHashMap.newKeySet();

  /** Reports whether this job was explicitly cancelled. */
  public boolean isCancelled() {
    return cancelled;
  }

  /** Releases all owned subprocesses after a failed or finished job. */
  public void finish() {
    for (Process process : List.copyOf(processes)) terminateAndWait(process);
  }

  /** Rejects work after cancellation or interruption. */
  public void check() {
    if (cancelled || Thread.currentThread().isInterrupted())
      throw new CancellationException("Production cancelled");
  }

  /** Stops owned processes, including descendants, so a blocked encoder pipe is released. */
  public void cancel() {
    cancelled = true;
    processes.forEach(Cancellation::stop);
  }

  /** Terminates owned child and descendant processes to release blocked I/O. */
  private static void stop(Process p) {
    p.descendants().forEach(h -> h.destroyForcibly());
    p.destroyForcibly();
  }

  /** Registers a newly started child and closes the start/cancel race. */
  public Process own(Process p) {
    processes.add(p);
    if (cancelled) stop(p);
    check();
    return p;
  }

  /** Releases an exited process reference. */
  public void release(Process p) {
    processes.remove(p);
  }

  /** Terminates and reaps an owned process on the worker before its files may be removed. */
  public void terminateAndWait(Process p) {
    try {
      if (p.isAlive()) stop(p);
      awaitExit(p);
    } finally {
      closeQuietly(p.getOutputStream());
      closeQuietly(p.getInputStream());
      closeQuietly(p.getErrorStream());
      release(p);
    }
  }

  /** Runs a command without a shell, capturing bounded diagnostics and enforcing a timeout. */
  public String run(List<String> command, long seconds) throws IOException, InterruptedException {
    check();
    Path log = Files.createTempFile("eduvideo-process-", ".log");
    Process p = null;
    try {
      // Keep the reference even when own() detects cancellation immediately.
      p =
          new ProcessBuilder(command)
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .start();
      own(p);
      p.getOutputStream().close();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
      while (!p.waitFor(100, TimeUnit.MILLISECONDS)) {
        check();
        if (System.nanoTime() > deadline)
          throw new IOException("Process timed out: " + command.get(0));
      }
      check();
      String result;
      try (var in = Files.newInputStream(log)) {
        result = new String(in.readNBytes(65536), java.nio.charset.StandardCharsets.UTF_8);
      }
      if (p.exitValue() != 0)
        throw new IOException(command.get(0) + " failed (" + p.exitValue() + "): " + result);
      return result;
    } finally {
      if (p != null) {
        try {
          if (p.isAlive()) stop(p);
          awaitExit(p);
        } finally {
          closeQuietly(p.getOutputStream());
          closeQuietly(p.getInputStream());
          closeQuietly(p.getErrorStream());
          release(p);
          deleteLogBestEffort(log);
        }
      } else {
        deleteLogBestEffort(log);
      }
    }
  }

  /**
   * Allows a forcibly terminated process to release its native handles before cleanup. Waits at
   * most two seconds and preserves the caller's interruption status. This method is used on the
   * worker, never by the UI cancellation action.
   */
  private static void awaitExit(Process p) {
    boolean interrupted = Thread.interrupted();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    try {
      while (p.isAlive()) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) break;
        try {
          if (p.waitFor(remaining, TimeUnit.NANOSECONDS)) break;
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  /** Closes a process stream without masking the command's result or original exception. */
  private static void closeQuietly(Closeable stream) {
    try {
      stream.close();
    } catch (IOException e) {
      System.err.println("EduVideo: could not close process stream: " + e.getMessage());
    }
  }

  /**
   * Treats temporary-log deletion as housekeeping, not production failure. Windows may retain a
   * file lock briefly even after the child has exited. Leave such a file for a best-effort JVM-exit
   * retry, preserving the real command error.
   */
  private static void deleteLogBestEffort(Path log) {
    try {
      Files.deleteIfExists(log);
    } catch (IOException e) {
      log.toFile().deleteOnExit();
      System.err.println(
          "EduVideo: temporary log retained for cleanup at exit: "
              + log
              + " ("
              + e.getMessage()
              + ")");
    }
  }
}
