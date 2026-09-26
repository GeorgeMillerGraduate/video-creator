/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.export;

import io.jengacode.eduvideo.video.VideoRenderer;
import java.io.IOException;
import java.nio.file.*;
import javax.imageio.ImageIO;

/**
 * Writes one image at a time. Refuses stale frame sequences in the output directory.
 *
 * <p>Streams one frame at a time to six-digit numbered PNG files. It refuses directories with prior
 * frame files so FFmpeg cannot consume a stale mixed sequence.
 */
public final class FrameExporter {
  /**
   * Streams a numbered PNG sequence, refusing stale frame files.
   *
   * @param renderer equation strategy or configured video renderer
   * @param directory destination directory for numbered PNG frames
   * @throws IOException if file access or encoding fails
   */
  public void export(VideoRenderer renderer, Path directory) throws IOException {
    Files.createDirectories(directory);
    try (var entries = Files.list(directory)) {
      if (entries.anyMatch(p -> p.getFileName().toString().matches("frame_[0-9]+\\.png")))
        throw new IOException("Output directory already contains frames: " + directory);
    }
    for (int n = 0; n < renderer.frameCount(); n++) {
      Path path = directory.resolve(String.format(java.util.Locale.ROOT, "frame_%06d.png", n));
      Path staged = Files.createTempFile(directory, "frame-", ".tmp");
      try {
        if (!ImageIO.write(renderer.renderFrame(n), "png", staged.toFile()))
          throw new IOException("No PNG writer available");
        if (Files.size(staged) == 0) throw new IOException("PNG writer produced empty output");
        try {
          Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
          Files.move(staged, path);
        }
      } finally {
        Files.deleteIfExists(staged);
      }
    }
  }
}
