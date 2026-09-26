/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.project;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Filesystem discovery of ordinary XML lessons. Never follows directory symlinks or modifies
 * projects.
 */
public final class ProjectLibrary {
  /** Returns sorted XML files below the selected root, omitting generated build/cache trees. */
  public List<Path> discover(Path root) throws IOException {
    if (!Files.isDirectory(root)) return List.of();
    var result = new ArrayList<Path>();
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<Path>() {
          /** Skips generated output and cache directories during project discovery. */
          public FileVisitResult preVisitDirectory(
              Path dir, java.nio.file.attribute.BasicFileAttributes attr) {
            String name = dir.getFileName().toString();
            return !dir.equals(root)
                    && Set.of("target", "output", ".git", "speech-cache").contains(name)
                ? FileVisitResult.SKIP_SUBTREE
                : FileVisitResult.CONTINUE;
          }

          /**
           * Collects XML candidates without attempting a potentially expensive parse during
           * traversal.
           */
          public FileVisitResult visitFile(
              Path file, java.nio.file.attribute.BasicFileAttributes attr) {
            if (file.toString().endsWith(".xml")) result.add(file.toAbsolutePath());
            return FileVisitResult.CONTINUE;
          }
        });
    result.sort(Comparator.comparing(Path::toString));
    return List.copyOf(result);
  }

  /** Saves an editor snapshot atomically to avoid truncation on failure. */
  public void save(Path path, String xml) throws IOException {
    Path parent = path.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path tmp = Files.createTempFile(parent, "eduvideo-", ".tmp");
    try {
      Files.writeString(tmp, xml);
      try {
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(tmp);
    }
  }
}
