/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.xml;

import io.jengacode.eduvideo.math.*;
import io.jengacode.eduvideo.objects.*;
import io.jengacode.eduvideo.util.ColorParser;
import io.jengacode.eduvideo.video.Theme;
import java.awt.Color;
import java.util.Locale;

/**
 * Registers semantic presentation components without changing the legacy object vocabulary.
 *
 * <p>Registers panels, steps, results, plot annotations, curved arrows and triangles with strict
 * attribute sets. These construct normal scene objects; no alternate renderer is introduced.
 */
final class VisualObjects {
  /** Prevents instantiation of this static utility. */
  private VisualObjects() {}

  /**
   * Registers built-in factories with their strict allowed attribute sets.
   *
   * @param p registry receiving built-in object factories
   */
  static void install(ObjectParser p) {
    String[] panelAttrs = {
      "padding", "radius", "fillColor", "bottomColor", "borderColor", "borderWidth", "shadow"
    };
    p.register("panel", (e, b, t) -> panel(new PanelObject(e.required("id")), e, t), panelAttrs);
    p.register(
        "step",
        (e, b, t) ->
            panel(
                new StepObject(e.required("id"), e.integer("number", 1), e.required("title")),
                e,
                t),
        "padding",
        "radius",
        "fillColor",
        "bottomColor",
        "borderColor",
        "borderWidth",
        "shadow",
        "number",
        "title");
    p.register(
        "result",
        (e, b, t) ->
            new ResultObject(
                e.required("id"),
                e.get("text", e.text.toString().strip()),
                ResultObject.Style.valueOf(e.get("style", "answer").toUpperCase(Locale.ROOT)),
                e.number("size", 36)),
        "text",
        "style",
        "size");
    p.register(
        "plot-point",
        (e, b, t) ->
            new PlotPoint(
                    e.required("id"),
                    e.number("px", 0),
                    e.number("py", 0),
                    e.get("label", ""),
                    e.bool("guides", false))
                .labelOffset(e.number("labelDx", 0), e.number("labelDy", -22)),
        "px",
        "py",
        "label",
        "guides",
        "labelDx",
        "labelDy");
    p.register(
        "area",
        (e, b, t) ->
            new AreaPlot(
                e.required("id"),
                e.required("expression"),
                e.number("from", 0),
                e.number("to", 1),
                e.integer("rectangles", 0)),
        "expression",
        "from",
        "to",
        "rectangles");
    p.register(
        "curved-arrow",
        (e, b, t) ->
            new CurvedArrowObject(
                e.required("id"),
                e.number("controlX", 50),
                e.number("controlY", 0),
                e.bool("openHead", false)),
        "controlX",
        "controlY",
        "openHead");
    p.register(
        "triangle",
        (e, b, t) ->
            new TriangleObject(
                    e.required("id"),
                    StandardObjects.points(e.required("points")),
                    e.get("vertices", "A,B,C").split(",", -1),
                    e.get("sides", ",,").split(",", -1))
                .emphasis(e.integer("sideMask", 0), e.integer("angleMask", 0)),
        "points",
        "vertices",
        "sides",
        "sideMask",
        "angleMask");
  }

  /**
   * Applies validated surface attributes and content padding to a panel.
   *
   * @param panel panel used by this operation
   * @param e located XML element
   * @param t non-null active palette
   * @return applies validated surface attributes and content padding to a panel
   */
  private static PanelObject panel(PanelObject panel, XmlElement e, Theme t) {
    return panel
        .padding(e.number("padding", 24))
        .surface(
            color(e, "fillColor", t.panel()),
            color(e, "bottomColor", t.backgroundSecondary()),
            color(e, "borderColor", t.panelBorder()),
            e.number("radius", 22),
            e.number("borderWidth", 1.3),
            e.bool("shadow", true));
  }

  /**
   * Parses an optional explicit colour, otherwise returning the palette fallback.
   *
   * @param e located XML element
   * @param name semantic name or diagnostic label
   * @param fallback value used when the attribute is absent
   * @return parses an optional explicit colour, otherwise returning the palette fallback
   */
  private static Color color(XmlElement e, String name, Color fallback) {
    return e.attributes.containsKey(name) ? ColorParser.parse(e.required(name)) : fallback;
  }
}
