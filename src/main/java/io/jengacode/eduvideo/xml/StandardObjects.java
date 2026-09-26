/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.xml;

import io.jengacode.eduvideo.algorithm.*;
import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.math.*;
import io.jengacode.eduvideo.objects.*;
import io.jengacode.eduvideo.scene.*;
import java.awt.geom.*;
import java.util.*;

/**
 * Built-in XML vocabulary. Add application-specific tags through ObjectParser.register.
 *
 * <p>Installs the legacy mathematics, algorithm and primitive vocabulary and delegates new visual
 * components to VisualObjects. Registration keeps Java model construction separate from XML
 * traversal.
 */
final class StandardObjects {
  /**
   * Registers built-in factories with their strict allowed attribute sets.
   *
   * @param p registry receiving built-in object factories
   */
  static void install(ObjectParser p) {
    VisualObjects.install(p);
    p.register(
        "equation",
        (e, b, t) ->
            new EquationObject(e.required("id"), e.get("value", e.text.toString().strip()))
                .typeset(e.bool("typeset", false))
                .style(e.get("font", t.font()), e.number("size", 44), e.get("align", "left")),
        "value",
        "typeset",
        "font",
        "size",
        "align");
    p.register(
        "axes",
        (e, b, t) ->
            new CoordinatePlane(
                    e.required("id"),
                    e.number("xmin", -10),
                    e.number("xmax", 10),
                    e.number("ymin", -5),
                    e.number("ymax", 5),
                    e.bool("grid", true),
                    e.number("step", 1))
                .labels(e.get("xlabel", "x"), e.get("ylabel", "y"))
                .appearance(
                    e.number("gridOpacity", 1),
                    e.number("tickSize", 15),
                    e.attributes.containsKey("axisColor")
                        ? io.jengacode.eduvideo.util.ColorParser.parse(e.required("axisColor"))
                        : null),
        "xmin",
        "xmax",
        "gridOpacity",
        "tickSize",
        "axisColor",
        "ymin",
        "ymax",
        "grid",
        "step",
        "xlabel",
        "ylabel");
    p.register(
        "function",
        (e, b, t) -> new FunctionPlot(e.required("id"), e.required("expression")),
        "expression");
    p.register(
        "image",
        (e, b, t) -> {
          try {
            return new ImageObject(e.required("id"), b.resolve(e.required("src")));
          } catch (java.io.IOException ex) {
            throw e.error(ex.getMessage());
          }
        },
        "src");
    p.register(
        "number-line",
        (e, b, t) ->
            new NumberLine(
                e.required("id"), e.number("min", -5), e.number("max", 5), e.number("step", 1)),
        "min",
        "max",
        "step");
    p.register(
        "matrix",
        (e, b, t) ->
            new MatrixObject(
                    e.required("id"),
                    Arrays.stream(e.required("values").split(";", -1))
                        .map(row -> Arrays.stream(row.split(",", -1)).map(String::strip).toList())
                        .toList())
                .initiallyVisible(e.bool("initiallyVisible", true))
                .cells(e.number("cellWidth", 70), e.number("cellHeight", 55), e.number("size", 28)),
        "values",
        "initiallyVisible",
        "cellWidth",
        "cellHeight",
        "size");
    for (String tag : List.of("array", "list", "stack", "queue"))
      p.register(
          tag,
          (e, b, t) ->
              new ArrayObject(
                      e.required("id"),
                      Arrays.stream(e.required("values").split(",", -1))
                          .map(String::strip)
                          .toList())
                  .vertical(e.bool("vertical", e.name.equals("stack"))),
          "values",
          "vertical");
    p.register(
        "code",
        (e, b, t) -> {
          if (!e.get("language", "java").equals("java"))
            throw e.error("Built-in syntax language is java; add a highlighter for others");
          return new CodeBlockObject(e.required("id"), e.text.toString(), e.number("size", 24))
              .title(e.get("title", ""))
              .lineNumbers(e.bool("lineNumbers", true));
        },
        "language",
        "title",
        "lineNumbers",
        "size");
    p.register(
        "variable",
        (e, b, t) -> new VariableObject(e.required("id"), e.required("name"), e.required("value")),
        "name",
        "value");
    p.register("graph", (e, b, t) -> new GraphObject(e.required("id")));
    p.register("tree", (e, b, t) -> new TreeObject(e.required("id")));
    p.register(
        "node",
        (e, b, t) ->
            new NodeObject(e.required("id"), e.get("label", e.required("id")))
                .state(
                    NodeObject.State.valueOf(e.get("state", "default").toUpperCase(Locale.ROOT))),
        "label",
        "state");
    p.register(
        "edge",
        (e, b, t) ->
            new EdgeObject(
                e.required("id"),
                e.required("from"),
                e.required("to"),
                e.get("weight", e.get("label", "")),
                e.bool("directed", false)),
        "from",
        "to",
        "weight",
        "label",
        "directed");
    p.register("vector", (e, b, t) -> new ArrowObject(e.required("id")));
    p.register(
        "point",
        (e, b, t) ->
            new CircleObject(e.required("id")).set(Property.WIDTH, 8).set(Property.HEIGHT, 8));
    p.register("segment", (e, b, t) -> new LineObject(e.required("id")));
    p.register(
        "ray",
        (e, b, t) ->
            new GeometryObject(
                e.required("id"),
                new Line2D.Double(
                    0,
                    0,
                    100000 * Math.cos(Math.toRadians(e.number("angle", 0))),
                    100000 * Math.sin(Math.toRadians(e.number("angle", 0)))),
                false),
        "angle");
    p.register(
        "polygon",
        (e, b, t) ->
            new GeometryObject(
                e.required("id"),
                GeometryObject.polygon(points(e.required("points")), true),
                e.bool("fill", false)),
        "points",
        "fill");
    p.register(
        "path",
        (e, b, t) ->
            new GeometryObject(
                e.required("id"),
                GeometryObject.polygon(points(e.required("points")), e.bool("closed", false)),
                e.bool("fill", false)),
        "points",
        "closed",
        "fill");
    p.register(
        "arc",
        (e, b, t) ->
            new GeometryObject(
                e.required("id"),
                new Arc2D.Double(
                    -e.number("radius", 60),
                    -e.number("radius", 60),
                    2 * positive(e, "radius", 60),
                    2 * positive(e, "radius", 60),
                    e.number("startAngle", 0),
                    e.number("extent", 90),
                    Arc2D.OPEN),
                false),
        "radius",
        "startAngle",
        "extent");
    p.register(
        "angle",
        (e, b, t) -> {
          double r = positive(e, "radius", 60),
              a = e.number("startAngle", 0),
              extent = e.number("extent", 60);
          var shape = new Path2D.Double();
          shape.moveTo(r * Math.cos(Math.toRadians(a)), -r * Math.sin(Math.toRadians(a)));
          shape.lineTo(0, 0);
          shape.lineTo(
              r * Math.cos(Math.toRadians(a + extent)), -r * Math.sin(Math.toRadians(a + extent)));
          shape.append(new Arc2D.Double(-r / 2, -r / 2, r, r, a, extent, Arc2D.OPEN), false);
          return new GeometryObject(e.required("id"), shape, false);
        },
        "radius",
        "startAngle",
        "extent");
  }

  /**
   * Reads an XML number and rejects nonpositive values.
   *
   * @param e located XML element
   * @param key XML attribute name
   * @param fallback value used when the attribute is absent
   * @return reads an XML number and rejects nonpositive values
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  private static double positive(XmlElement e, String key, double fallback) {
    return io.jengacode.eduvideo.util.Checks.positive(e.number(key, fallback), key);
  }

  /**
   * Parses whitespace-separated x,y coordinate pairs and checks finite values.
   *
   * @param value initial or assigned value
   * @return parses whitespace-separated x,y coordinate pairs and checks finite values
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  static double[][] points(String value) {
    return Arrays.stream(value.strip().split("\\s+"))
        .map(
            s -> {
              String[] parts = s.split(",");
              if (parts.length != 2)
                throw new IllegalArgumentException("Points use x,y x,y syntax");
              return new double[] {
                io.jengacode.eduvideo.util.Checks.finite(Double.parseDouble(parts[0]), "point x"),
                io.jengacode.eduvideo.util.Checks.finite(Double.parseDouble(parts[1]), "point y")
              };
            })
        .toArray(double[][]::new);
  }
}
