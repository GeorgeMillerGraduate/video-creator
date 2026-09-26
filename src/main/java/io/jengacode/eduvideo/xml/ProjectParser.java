/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.xml;

import io.jengacode.eduvideo.scene.*;
import io.jengacode.eduvideo.util.ColorParser;
import io.jengacode.eduvideo.video.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Versioned XML frontend which builds the public Java model.
 *
 * <p>Parses version 1.0 XML securely, resolves relative assets, selects project/scene themes and
 * validates the completed shared model. Object registration remains injectable for
 * application-specific vocabulary.
 */
public final class ProjectParser {

  private final ObjectParser objects;

  private final AnimationParser animations;

  /** Creates a ProjectParser instance with its default configuration. */
  public ProjectParser() {
    this(new ObjectParser(), new AnimationParser());
  }

  /**
   * Creates a configured ProjectParser instance.
   *
   * @param objects object parser or objects to position, according to the overload
   * @param animations scene event parser
   */
  public ProjectParser(ObjectParser objects, AnimationParser animations) {
    this.objects = objects;
    this.animations = animations;
  }

  /**
   * Parses and validates version 1.0 XML, resolving asset paths relative to its directory.
   *
   * <p>Loads and validates a version 1.0 lesson, resolving assets relative to its file.
   *
   * @param path input file path
   * @return validated VideoProject model
   * @throws IOException if file access or encoding fails
   * @throws ProjectFormatException if the lesson contains invalid or unsupported content
   */
  public VideoProject parse(Path path) throws IOException {
    return parseRoot(LocatedXml.read(path), path);
  }

  /** Parses an editor snapshot while resolving assets relative to its intended XML file. */
  public VideoProject parse(String xml, Path path) throws IOException {
    return parseRoot(
        LocatedXml.read(new org.xml.sax.InputSource(new java.io.StringReader(xml))), path);
  }

  /** Shares semantic validation between disk and unsaved editor inputs. */
  private VideoProject parseRoot(XmlElement root, Path path) throws IOException {
    try {
      if (!root.name.equals("edu-video")) throw root.error("Expected edu-video root");
      root.only("version", "width", "height", "fps");
      if (!root.required("version").equals("1.0")) throw root.error("Supported version is 1.0");
      var project =
          new VideoProject(
              new VideoSettings(
                  root.integer("width", 1920),
                  root.integer("height", 1080),
                  root.number("fps", 60)));
      Path base = path.toAbsolutePath().getParent();
      boolean hasTheme = false;
      for (var e : root.children)
        if (e.name.equals("theme")) {
          if (hasTheme) throw e.error("Only one theme allowed");
          hasTheme = true;
          e.only(
              "name",
              "background",
              "foreground",
              "primary",
              "secondary",
              "success",
              "warning",
              "font");
          Theme t =
              e.attributes.containsKey("name") ? Theme.named(e.required("name")) : project.theme();
          project.theme(
              new Theme(
                  color(e, "background", t.background()),
                  color(e, "foreground", t.foreground()),
                  color(e, "primary", t.primary()),
                  color(e, "secondary", t.secondary()),
                  color(e, "success", t.success()),
                  color(e, "warning", t.warning()),
                  e.get("font", t.font())));
        }
      for (var e : root.children) {
        if (e.name.equals("theme")) continue;
        if (e.name.equals("audio")) {
          e.only("src", "start", "volume");
          project.audio(
              new AudioCue(
                  base.resolve(e.required("src")), e.number("start", 0), e.number("volume", 1)));
          continue;
        }
        if (!e.name.equals("scene")) throw e.error("Expected scene, theme or audio");
        e = ProductionEvents.markers(e);
        try {
          e.only("id", "duration", "background", "theme", "transition", "transitionDuration");
          e.required("duration");
          var scene = new Scene(e.required("id"), e.number("duration", 1));
          Theme activeTheme =
              e.attributes.containsKey("theme")
                  ? Theme.named(e.required("theme"))
                  : project.theme();
          scene.theme(activeTheme);
          if (e.attributes.containsKey("theme"))
            scene.backdrop(new io.jengacode.eduvideo.render.BackgroundRenderer());
          boolean hasBackground = false;
          for (var child : e.children)
            if (child.name.equals("background")) {
              if (hasBackground) throw child.error("Only one background allowed");
              hasBackground = true;
              child.only("style", "src", "grid", "curves", "dots", "vignette", "intensity");
              if (!child.children.isEmpty())
                throw child.error("Background cannot contain children");
              if (child.attributes.containsKey("style")) {
                activeTheme = Theme.named(child.required("style"));
                scene.theme(activeTheme);
              }
              var backdrop =
                  new io.jengacode.eduvideo.render.BackgroundRenderer()
                      .options(
                          child.bool("grid", true),
                          child.bool("curves", true),
                          child.bool("dots", true),
                          child.bool("vignette", true),
                          child.number("intensity", .5));
              if (child.attributes.containsKey("src"))
                try {
                  backdrop.image(base.resolve(child.required("src")));
                } catch (IOException ex) {
                  throw child.error(ex.getMessage());
                }
              scene.backdrop(backdrop);
            }
          if (e.attributes.containsKey("background"))
            scene.background(ColorParser.parse(e.required("background")));
          scene.transition(
              Scene.Transition.valueOf(e.get("transition", "cut").toUpperCase(Locale.ROOT)),
              e.number("transitionDuration", 0));
          for (var child : e.children)
            if (!animations.recognizes(child.name)
                && !ProductionEvents.recognizes(child.name)
                && !child.name.equals("background")) {
              scene.add(objects.parse(child, base, activeTheme));
              try {
                scene.objects();
              } catch (IllegalArgumentException ex) {
                throw child.error(ex.getMessage());
              }
            }
          for (var child : e.children)
            if (animations.recognizes(child.name)) animations.parse(child, scene);
          for (var child : e.children)
            if (ProductionEvents.recognizes(child.name))
              ProductionEvents.parse(child, scene, project, base, project.duration());
          scene.validate();
          project.add(scene);
        } catch (ProjectFormatException ex) {
          throw ex;
        } catch (IllegalArgumentException ex) {
          throw e.error(ex.getMessage());
        }
      }
      for (var cue : project.audio())
        project
            .audioTimeline()
            .add(
                new io.jengacode.eduvideo.audio.AudioTimeline.Track(
                    cue.source(),
                    cue.start(),
                    project.duration(),
                    cue.volume(),
                    0,
                    0,
                    false,
                    false));
      project.validate();
      return project;
    } catch (ProjectFormatException ex) {
      throw ex;
    } catch (IllegalArgumentException ex) {
      throw root.error(ex.getMessage());
    }
  }

  /**
   * Parses a theme override or preserves the inherited colour.
   *
   * @param e located XML element
   * @param key XML attribute name
   * @param fallback value used when the attribute is absent
   * @return parses a theme override or preserves the inherited colour
   */
  private static java.awt.Color color(XmlElement e, String key, java.awt.Color fallback) {
    return e.attributes.containsKey(key) ? ColorParser.parse(e.required(key)) : fallback;
  }
}
