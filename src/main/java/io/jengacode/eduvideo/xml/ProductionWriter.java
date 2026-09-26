/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.xml;

import io.jengacode.eduvideo.audio.AudioTimeline;
import io.jengacode.eduvideo.production.ProductionProject;
import io.jengacode.eduvideo.video.VideoSettings;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import javax.xml.stream.*;

/**
 * Serializes the small manifest vocabulary; paths are relative to the destination where possible.
 */
public final class ProductionWriter {
  /** Prevents construction of the manifest serialization utility. */
  private ProductionWriter() {}

  /** Writes a manifest from ordered parts while retaining global music and output settings. */
  public static String write(
      Path destination,
      String title,
      VideoSettings settings,
      Path output,
      List<ProductionProject.Part> parts,
      List<AudioTimeline.Track> music) {
    try {
      var text = new StringWriter();
      var w = XMLOutputFactory.newFactory().createXMLStreamWriter(text);
      w.writeStartDocument("UTF-8", "1.0");
      w.writeCharacters("\n");
      w.writeStartElement("edu-production");
      w.writeAttribute("version", "1.0");
      w.writeAttribute("title", title);
      w.writeAttribute("width", Integer.toString(settings.width()));
      w.writeAttribute("height", Integer.toString(settings.height()));
      w.writeAttribute("fps", Double.toString(settings.fps()));
      w.writeAttribute("output", relative(destination, output));
      if (!music.isEmpty()) {
        w.writeCharacters("\n  ");
        w.writeStartElement("audio");
        for (var t : music) {
          w.writeCharacters("\n    ");
          w.writeEmptyElement("music");
          w.writeAttribute("src", relative(destination, t.source()));
          w.writeAttribute("volume", Double.toString(t.volume()));
          w.writeAttribute("loop", Boolean.toString(t.loop()));
          w.writeAttribute("start", Double.toString(t.start()));
          // A full-length global bed follows the new total after parts are added/reordered.
          w.writeAttribute("fade-in", Double.toString(t.fadeIn()));
          w.writeAttribute("fade-out", Double.toString(t.fadeOut()));
          w.writeAttribute("end", Double.toString(t.end()));
        }
        w.writeCharacters("\n  ");
        w.writeEndElement();
      }
      w.writeCharacters("\n  ");
      w.writeStartElement("parts");
      for (var p : parts) {
        w.writeCharacters("\n    ");
        w.writeEmptyElement("part");
        w.writeAttribute("src", relative(destination, p.source()));
        w.writeAttribute("title", p.title());
      }
      w.writeCharacters("\n  ");
      w.writeEndElement();
      w.writeCharacters("\n");
      w.writeEndElement();
      w.writeCharacters("\n");
      w.close();
      return text.toString();
    } catch (XMLStreamException e) {
      throw new IllegalStateException("Could not write production XML", e);
    }
  }

  /** Rebases paths for Save As; separate Windows drives retain absolute paths. */
  private static String relative(Path source, Path target) {
    try {
      return source
          .toAbsolutePath()
          .getParent()
          .relativize(target.toAbsolutePath())
          .toString()
          .replace('\\', '/');
    } catch (IllegalArgumentException e) {
      return target.toAbsolutePath().toString();
    }
  }
}
