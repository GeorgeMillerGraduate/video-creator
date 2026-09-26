/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.xml;

import io.jengacode.eduvideo.animation.*;
import io.jengacode.eduvideo.scene.*;
import io.jengacode.eduvideo.util.ColorParser;
import java.util.*;

/**
 * Scene-local animation syntax.
 *
 * <p>Resolves event targets after all scene objects exist, then builds ordinary timeline tracks or
 * semantic sub-object events. Matrix indices and code selection bounds are validated by their
 * target objects.
 */
public class AnimationParser {
  /**
   * Reports whether an element name is a supported scene event.
   *
   * @param tag registered XML element name
   * @return true when the documented condition holds; false otherwise
   */
  public boolean recognizes(String tag) {
    return Set.of(
            "animate",
            "animation",
            "typewriter",
            "highlight",
            "swap",
            "follow-path",
            "set",
            "highlight-row",
            "highlight-column",
            "highlight-cell",
            "reveal-cell",
            "hide-cell",
            "select-code",
            "effect")
        .contains(tag);
  }

  /**
   * Resolves a target and appends validated scene-local tracks or semantic events.
   *
   * @param e located XML element
   * @param scene owning scene with its object namespace
   * @throws ProjectFormatException if the lesson contains invalid or unsupported content
   */
  public void parse(XmlElement e, Scene scene) {
    try {
      SceneObject target = scene.find(e.required("target"));
      switch (e.name) {
        case "highlight-row", "highlight-column", "highlight-cell", "reveal-cell", "hide-cell" -> {
          e.only("target", "row", "column", "start", "duration", "color");
          if (!(target instanceof io.jengacode.eduvideo.math.MatrixObject matrix))
            throw e.error("Matrix event requires a matrix target");
          double start = e.number("start", 0), duration = e.number("duration", 1);
          var color = ColorParser.parse(e.get("color", "#38BDF8"));
          if (!e.name.equals("highlight-column")) e.required("row");
          if (!e.name.equals("highlight-row")) e.required("column");
          int row = e.integer("row", 0), column = e.integer("column", 0);
          switch (e.name) {
            case "highlight-row" -> matrix.highlightRow(row, start, duration, color);
            case "highlight-column" -> matrix.highlightColumn(column, start, duration, color);
            case "highlight-cell" -> matrix.highlightCell(row, column, start, duration, color);
            case "reveal-cell" -> matrix.revealCell(row, column, start);
            case "hide-cell" -> matrix.hideCell(row, column, start);
          }
        }
        case "select-code" -> {
          e.only("target", "line", "from", "to", "start", "duration");
          if (!(target instanceof io.jengacode.eduvideo.algorithm.CodeBlockObject code))
            throw e.error("select-code requires code");
          e.required("line");
          e.required("from");
          e.required("to");
          code.select(
              e.integer("line", 1),
              e.integer("from", 0),
              e.integer("to", 1),
              e.number("start", 0),
              e.number("duration", 1));
        }
        case "effect" -> {
          e.only("target", "name", "start", "duration", "distance");
          double start = e.number("start", 0), duration = e.number("duration", 1);
          switch (e.required("name")) {
            case "fadeIn" -> Effects.fadeIn(target, start, duration);
            case "fadeOut" -> Effects.fadeOut(target, start, duration);
            case "drawOn" -> Effects.drawOn(target, start, duration);
            case "popIn" -> Effects.popIn(target, start, duration);
            case "slideIn" -> Effects.slideIn(target, start, duration, e.number("distance", 40));
            case "glowPulse" -> Effects.glowPulse(target, start, duration);
            default -> throw e.error("Unknown effect name");
          }
        }
        case "animate" -> {
          e.only("target", "property", "from", "to", "start", "duration", "easing");
          Property p = Property.parse(e.required("property"));
          target.animate(
              p,
              value(e.required("from"), p),
              value(e.required("to"), p),
              e.number("start", 0),
              e.number("duration", 1),
              Easing.parse(e.get("easing", "linear")));
        }
        case "animation" -> {
          e.only("target", "property");
          Property p = Property.parse(e.required("property"));
          List<Keyframe> keys = new ArrayList<>();
          for (var k : e.children) {
            if (!k.name.equals("keyframe")) throw k.error("Expected keyframe");
            k.only("time", "value", "easing");
            keys.add(
                new Keyframe(
                    k.number("time", 0),
                    value(k.required("value"), p),
                    Easing.parse(k.get("easing", "linear"))));
          }
          target.animate(new PropertyAnimation(p, keys));
        }
        case "typewriter" -> {
          e.only("target", "start", "duration", "mode");
          String mode = e.get("mode", "character");
          if (!Set.of("character", "word").contains(mode))
            throw e.error("mode must be character or word");
          if (target instanceof io.jengacode.eduvideo.objects.TextObject text)
            text.words(mode.equals("word"));
          else throw e.error("typewriter requires text or equation");
          target.animate(
              Property.REVEAL, 0, 1, e.number("start", 0), e.number("duration", 1), Easing.LINEAR);
        }
        case "highlight" -> {
          e.only("target", "line", "index", "start", "duration", "color");
          double start = e.number("start", 0), duration = e.number("duration", 1);
          if (target instanceof io.jengacode.eduvideo.algorithm.CodeBlockObject code) {
            e.required("line");
            code.highlight(e.integer("line", 1), start, duration);
          } else if (target instanceof io.jengacode.eduvideo.algorithm.ArrayObject array) {
            e.required("index");
            array.highlight(
                e.integer("index", 0),
                start,
                duration,
                ColorParser.parse(e.get("color", "#24607A")));
          } else target.animate(new PulseAnimation(Property.HIGHLIGHT, start, start + duration));
        }
        case "swap" -> {
          e.only("target", "a", "b", "start", "duration");
          if (!(target instanceof io.jengacode.eduvideo.algorithm.ArrayObject array))
            throw e.error("Swap requires an array/list/stack/queue");
          e.required("a");
          e.required("b");
          array.swap(
              e.integer("a", 0), e.integer("b", 1), e.number("start", 0), e.number("duration", 1));
        }
        case "set" -> {
          e.only("target", "time", "value");
          if (!(target instanceof io.jengacode.eduvideo.algorithm.VariableObject variable))
            throw e.error("set requires a variable");
          variable.assign(e.number("time", 0), e.required("value"));
        }
        case "follow-path" -> {
          e.only("target", "points", "start", "duration", "easing");
          var points = StandardObjects.points(e.required("points"));
          for (Property axis : List.of(Property.X, Property.Y))
            target.animate(
                new PathAnimation(
                    axis,
                    points,
                    e.number("start", 0),
                    e.number("duration", 1),
                    Easing.parse(e.get("easing", "linear"))));
        }
        default -> throw e.error("Unknown event");
      }
      if (!e.name.equals("animation") && !e.children.isEmpty())
        throw e.error("Event cannot have children");
    } catch (ProjectFormatException ex) {
      throw ex;
    } catch (IllegalArgumentException ex) {
      throw e.error(ex.getMessage());
    }
  }

  /**
   * Parses a numeric property value or encodes a colour as ARGB.
   *
   * @param s XML property value text
   * @param p property deciding between numeric and ARGB parsing
   * @return parses a numeric property value or encodes a colour as ARGB
   */
  protected static double value(String s, Property p) {
    return p == Property.COLOR ? ColorParser.parse(s).getRGB() : Double.parseDouble(s);
  }
}
