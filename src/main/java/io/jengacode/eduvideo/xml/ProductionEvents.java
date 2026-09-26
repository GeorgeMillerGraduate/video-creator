/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.xml;

import io.jengacode.eduvideo.animation.*;
import io.jengacode.eduvideo.audio.*;
import io.jengacode.eduvideo.math.*;
import io.jengacode.eduvideo.scene.*;
import io.jengacode.eduvideo.util.ColorParser;
import io.jengacode.eduvideo.video.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Strict screenplay extensions compiled into the existing scene model. Marker references are
 * resolved before normal parsing, so ordinary animation tags can share the same timing markers.
 */
final class ProductionEvents {
  /** Prevents utility construction. */
  private ProductionEvents() {}

  /**
   * * Recognises production instructions that do not construct ordinary XML objects.
   *
   * @param tag XML tag name to recognise
   * @return true when the tag is a supported production instruction
   */
  static boolean recognizes(String tag) {
    return Set.of(
            "marker",
            "speak",
            "music",
            "sound",
            "matrix-product-step",
            "transfer",
            "transform-equation",
            "camera-focus",
            "camera-reset",
            "focus",
            "assign",
            "move-to",
            "replacement-transform",
            "camera-follow",
            "camera-frame")
        .contains(tag);
  }

  /**
   * * Copies a scene subtree and resolves @marker or @marker+seconds time expressions.
   *
   * @param scene owning scene with a validated object namespace
   * @return copied scene subtree with numeric timing references
   */
  static XmlElement markers(XmlElement scene) {
    var markers = new HashMap<String, Double>();
    for (var e : scene.children)
      if (e.name.equals("marker")) {
        e.only("id", "time");
        double t = e.number("time", 0);
        if (t < 0 || t > scene.number("duration", 0) || markers.put(e.required("id"), t) != null)
          throw e.error("Invalid or duplicate marker");
      }
    return resolve(scene, markers);
  }

  /**
   * * Recursively substitutes only timing attributes, leaving anchor references untouched.
   *
   * @param e located XML element being compiled
   * @param markers validated scene-local timing-marker map
   * @return validated semantic reference or measured cache result, as described above
   */
  private static XmlElement resolve(XmlElement e, Map<String, Double> markers) {
    var attrs = new HashMap<>(e.attributes);
    for (var key : List.of("start", "max-end", "end", "time")) {
      String v = attrs.get(key);
      if (v != null && v.startsWith("@")) {
        var p = v.substring(1).split("\\+", 2);
        Double t = markers.get(p[0]);
        if (t == null) throw e.error("Unknown marker " + p[0]);
        attrs.put(key, Double.toString(t + (p.length == 2 ? Double.parseDouble(p[1]) : 0)));
      }
    }
    var copy = new XmlElement(e.name, e.line, attrs);
    copy.text.append(e.text);
    for (var c : e.children) copy.children.add(resolve(c, markers));
    return copy;
  }

  /**
   * Installs factory tags whose animation parameters use the ordinary timeline.
   *
   * @param p object factory registry receiving the production tags
   */
  static void install(ObjectParser p) {
    p.register(
        "vectors",
        (e, b, t) ->
            new VectorDiagram(
                e.required("id"),
                e.get("mode", "addition"),
                e.number("ax", 2),
                e.number("ay", 1),
                e.number("bx", 1),
                e.number("by", 2),
                e.number("m00", 1),
                e.number("m01", 0),
                e.number("m10", 0),
                e.number("m11", 1),
                e.number("unit", 70)),
        "mode",
        "ax",
        "ay",
        "bx",
        "by",
        "m00",
        "m01",
        "m10",
        "m11",
        "unit");
    p.register(
        "statistics",
        (e, b, t) ->
            new StatisticsPlot(
                e.required("id"),
                e.get("mode", "bars"),
                Arrays.stream(e.required("values").split(","))
                    .mapToDouble(Double::parseDouble)
                    .toArray()),
        "mode",
        "values");
    p.register(
        "venn",
        (e, b, t) ->
            new VennDiagram(
                e.required("id"),
                e.get("operation", "intersection"),
                e.get("left", "A"),
                e.get("right", "B")),
        "operation",
        "left",
        "right");
    p.register(
        "token-equation",
        (e, b, t) ->
            new TokenEquation(
                e.required("id"),
                TokenEquation.parse(e.required("terms")),
                e.number("size", 42),
                e.number("spacing", 90)),
        "terms",
        "size",
        "spacing");
    p.register(
        "calculus",
        (e, b, t) ->
            new CalculusObject(
                e.required("id"),
                e.required("expression"),
                e.get("mode", "secant"),
                e.number("point", 1),
                e.number("from", 0),
                e.number("to", 4)),
        "expression",
        "mode",
        "point",
        "from",
        "to");
  }

  /**
   * Validates and compiles one scene instruction, retaining source-line errors.
   *
   * @param e located XML element being compiled
   * @param scene owning scene with a validated object namespace
   * @param project validated lesson model, kept unchanged during rendering
   * @param base absolute directory used to resolve relative lesson assets
   * @param offset global start time of the scene
   */
  static void parse(XmlElement e, Scene scene, VideoProject project, Path base, double offset) {
    try {
      double start = e.number("start", 0), duration = e.number("duration", 1);
      if (start < 0 || start > scene.duration() || !Double.isFinite(duration) || duration <= 0)
        throw e.error("Invalid event time");
      String id = "event-" + e.line + "-" + scene.objects().size();
      if (!e.children.isEmpty())
        throw e.error("Production instructions cannot contain child elements; escape SSML as text");
      switch (e.name) {
        case "marker" -> {
          return;
        }
        case "speak" -> {
          e.only(
              "id",
              "start",
              "max-end",
              "maxEnd",
              "text",
              "language",
              "voice",
              "rate",
              "pitch",
              "ssml");
          if (!e.attributes.containsKey("max-end") && !e.attributes.containsKey("maxEnd"))
            throw e.error("Missing max-end or maxEnd");
          double end = e.number(e.attributes.containsKey("max-end") ? "max-end" : "maxEnd", 0);
          if (end > scene.duration()) throw e.error("Narration exceeds scene");
          project
              .audioTimeline()
              .speak(
                  new NarrationCue(
                      scene.id() + "/" + e.get("id", id),
                      offset + start,
                      offset + end,
                      e.get("text", e.text.toString().strip()),
                      new VoiceSettings(
                          e.get("language", "en-GB"),
                          e.get("voice", "default"),
                          e.number(
                              "rate",
                              Double.parseDouble(
                                  new io.jengacode.eduvideo.app.StudioSettings().get("rate", "1"))),
                          e.number("pitch", 0),
                          e.bool("ssml", false))));
          return;
        }
        case "music", "sound" -> {
          e.only("src", "start", "end", "volume", "fade-in", "fade-out", "loop");
          double end = e.number("end", scene.duration());
          if (end > scene.duration()) throw e.error("Audio exceeds scene");
          project
              .audioTimeline()
              .add(
                  new AudioTimeline.Track(
                      base.resolve(e.required("src")),
                      offset + start,
                      offset + end,
                      e.number("volume", e.name.equals("music") ? .1 : 1),
                      e.number("fade-in", 0),
                      e.number("fade-out", 0),
                      e.bool("loop", false),
                      e.name.equals("music")));
          return;
        }
        case "matrix-product-step" -> {
          e.only(
              "left", "right", "result", "row", "column", "start", "duration", "work-x", "work-y");
          scene.add(
              new MatrixProductStep(
                  id,
                  (MatrixObject) scene.find(e.required("left")),
                  (MatrixObject) scene.find(e.required("right")),
                  (MatrixObject) scene.find(e.required("result")),
                  e.integer("row", 0),
                  e.integer("column", 0),
                  start,
                  duration,
                  e.number("work-x", project.settings().width() / 2.),
                  e.number("work-y", project.settings().height() * .75)));
        }
        case "transfer" -> {
          e.only("from", "to", "text", "start", "duration", "bend", "size", "color");
          scene.add(
              new ValueTransfer(
                      id,
                      e.required("text"),
                      Anchor.resolve(scene, e.required("from")),
                      Anchor.resolve(scene, e.required("to")),
                      start,
                      duration,
                      e.number("bend", -100),
                      e.number("size", 36))
                  .color(ColorParser.parse(e.get("color", "#4CF0B3"))));
        }
        case "transform-equation" -> {
          e.only("target", "terms", "start", "duration");
          ((TokenEquation) scene.find(e.required("target")))
              .transform(TokenEquation.parse(e.required("terms")), start, duration);
        }
        case "move-to" -> {
          e.only("target", "anchor", "to", "start", "duration");
          Transforms.moveTo(
              scene.find(e.required("target")),
              e.get("anchor", "center"),
              Anchor.resolve(scene, e.required("to")),
              start,
              duration);
        }
        case "replacement-transform" -> {
          e.only("source", "target", "start", "duration");
          Transforms.replace(
              scene.find(e.required("source")), scene.find(e.required("target")), start, duration);
        }
        case "assign" -> {
          e.only("target", "value", "start");
          ((io.jengacode.eduvideo.algorithm.VariableObject) scene.find(e.required("target")))
              .assign(start, e.required("value"));
          return;
        }
        case "focus" -> {
          e.only("targets", "start", "duration", "dim");
          var selected =
              new HashSet<>(
                  Arrays.stream(e.required("targets").split(",")).map(String::strip).toList());
          for (String name : selected) scene.find(name.strip());
          for (var o : scene.root().children())
            if (!selected.contains(o.id())) o.dim(start, start + duration, e.number("dim", .25));
        }
        case "camera-follow" -> {
          e.only("target", "anchor", "start", "duration");
          var anchor = new Anchor(scene.find(e.required("target")), e.get("anchor", "center"));
          scene
              .camera()
              .animate(
                  new CameraFollow(
                      Property.X,
                      anchor,
                      project.settings().width() / 2.,
                      start,
                      start + duration));
          scene
              .camera()
              .animate(
                  new CameraFollow(
                      Property.Y,
                      anchor,
                      project.settings().height() / 2.,
                      start,
                      start + duration));
        }
        case "camera-frame" -> {
          e.only("x", "y", "width", "height", "padding", "start", "duration");
          double w = e.number("width", 0),
              h = e.number("height", 0),
              padding = e.number("padding", .1);
          if (w <= 0 || h <= 0 || padding < 0 || padding >= .5)
            throw e.error("Invalid camera region");
          var camera = scene.camera();
          double x = e.number("x", 0) + w / 2 - project.settings().width() / 2.,
              y = e.number("y", 0) + h / 2 - project.settings().height() / 2.,
              zoom =
                  Math.min(project.settings().width() / w, project.settings().height() / h)
                      * (1 - 2 * padding);
          camera.animate(
              Property.X, camera.value(Property.X, start), x, start, duration, Easing.SMOOTH_STEP);
          camera.animate(
              Property.Y, camera.value(Property.Y, start), y, start, duration, Easing.SMOOTH_STEP);
          camera.animate(
              Property.ZOOM,
              camera.value(Property.ZOOM, start),
              zoom,
              start,
              duration,
              Easing.SMOOTH_STEP);
        }
        case "camera-focus", "camera-reset" -> {
          e.only("target", "anchor", "zoom", "start", "duration");
          var camera = scene.camera();
          double x = 0, y = 0, z = 1;
          if (e.name.equals("camera-focus")) {
            var anchor =
                new Anchor(scene.find(e.required("target")), e.get("anchor", "center")).at(start);
            x = anchor.getX() - project.settings().width() / 2.;
            y = anchor.getY() - project.settings().height() / 2.;
            z = e.number("zoom", 1.15);
            if (z <= 0) throw e.error("Zoom must be positive");
          }
          camera.animate(
              Property.X, camera.value(Property.X, start), x, start, duration, Easing.SMOOTH_STEP);
          camera.animate(
              Property.Y, camera.value(Property.Y, start), y, start, duration, Easing.SMOOTH_STEP);
          camera.animate(
              Property.ZOOM,
              camera.value(Property.ZOOM, start),
              z,
              start,
              duration,
              Easing.SMOOTH_STEP);
        }
        default -> throw e.error("Unknown production event");
      }
      if (start + duration > scene.duration() + 1e-9) throw e.error("Event exceeds scene");
    } catch (ClassCastException ex) {
      throw e.error("Target object has the wrong type for " + e.name);
    } catch (IllegalArgumentException ex) {
      throw e.error(ex.getMessage());
    }
  }
}
