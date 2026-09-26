/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.xml;

import io.jengacode.eduvideo.audio.AudioTimeline;
import io.jengacode.eduvideo.production.*;
import io.jengacode.eduvideo.video.VideoSettings;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Secure manifest frontend. Lessons remain independently parsed by the existing ProjectParser. */
public final class ProductionParser {
  /** Identifies a manifest using the hardened XML loader, not filename heuristics. */
  public boolean isProduction(String xml) throws IOException {
    return root(xml).name.equals("edu-production");
  }

  /** Reads a manifest and resolves all paths relative to its directory. */
  public ProductionProject parse(Path source) throws IOException {
    return parse(Files.readString(source), source);
  }

  /** Validates an unsaved manifest snapshot and loads independent chapter snapshots. */
  public ProductionProject parse(String xml, Path source) throws IOException {
    var r = root(xml);
    if (!r.name.equals("edu-production")) throw r.error("Expected edu-production root");
    r.only("version", "title", "width", "height", "fps", "output");
    if (!r.required("version").equals("1.0")) throw r.error("Supported production version is 1.0");
    Path base = source.toAbsolutePath().getParent();
    var settings =
        new VideoSettings(r.integer("width", 1920), r.integer("height", 1080), r.number("fps", 30));
    var parts = new ArrayList<ProductionProject.Part>();
    XmlElement audio = null, partList = null;
    for (var e : r.children) {
      if (e.name.equals("parts") && partList == null) partList = e;
      else if (e.name.equals("audio") && audio == null) audio = e;
      else throw e.error("Expected one parts element and optional audio element");
    }
    if (partList == null) throw r.error("Missing parts");
    partList.only();
    double offset = 0;
    for (var e : partList.children) {
      if (!e.name.equals("part")) throw e.error("Expected part");
      e.only("src", "title");
      if (!e.children.isEmpty()) throw e.error("Part cannot contain children");
      Path path = base.resolve(e.required("src")).normalize();
      try {
        var lesson = new ProjectParser().parse(path);
        parts.add(
            new ProductionProject.Part(
                path, e.get("title", path.getFileName().toString()), lesson, offset));
        offset += lesson.duration();
      } catch (Exception ex) {
        throw e.error(
            "Part " + (parts.size() + 1) + ": " + path.getFileName() + ": " + ex.getMessage());
      }
    }
    var music = new ArrayList<AudioTimeline.Track>();
    if (audio != null) {
      audio.only();
      for (var e : audio.children) {
        if (!e.name.equals("music")) throw e.error("Production audio accepts music elements");
        e.only("src", "start", "end", "volume", "loop", "fade-in", "fade-out");
        if (!e.children.isEmpty()) throw e.error("Music cannot contain children");
        try {
          music.add(
              new AudioTimeline.Track(
                  base.resolve(e.required("src")).normalize(),
                  e.number("start", 0),
                  e.number("end", offset),
                  e.number("volume", .15),
                  e.number("fade-in", 0),
                  e.number("fade-out", 0),
                  e.bool("loop", false),
                  true));
        } catch (IllegalArgumentException ex) {
          throw e.error(ex.getMessage());
        }
      }
    }
    try {
      var result =
          new ProductionProject(
              source,
              r.get("title", source.getFileName().toString()),
              settings,
              base.resolve(r.get("output", "output/production.mp4")).normalize(),
              parts,
              music);
      result.audioTimeline();
      return result;
    } catch (IllegalArgumentException ex) {
      throw r.error(ex.getMessage());
    }
  }

  /** Uses the existing entity-denying SAX loader for manifest detection and editor text. */
  private static XmlElement root(String xml) throws IOException {
    return LocatedXml.read(new org.xml.sax.InputSource(new StringReader(xml)));
  }
}
