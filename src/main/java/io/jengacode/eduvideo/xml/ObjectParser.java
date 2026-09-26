/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.xml;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.objects.*;
import io.jengacode.eduvideo.scene.*;
import io.jengacode.eduvideo.util.ColorParser;
import io.jengacode.eduvideo.video.Theme;
import java.nio.file.Path;
import java.util.*;

/**
 * Extensible XML object factory registry shared by the project parser.
 *
 * <p>Registers tag factories and their permitted attributes, applies shared properties, and
 * recursively builds groups. Factories receive the lesson directory and active theme; errors retain
 * XML source context.
 */
public final class ObjectParser {
  /**
   * Extension point for a named XML object tag. Construct and validate the object; ObjectParser
   * applies common properties and attaches group children afterward.
   */
  @FunctionalInterface
  public interface Factory {
    /**
     * Constructs a scene object from a located element, asset directory and palette.
     *
     * @param element located XML element
     * @param base absolute parent directory of the XML lesson for relative assets
     * @param theme active semantic palette
     * @return validated SceneObject model
     */
    SceneObject create(XmlElement element, Path base, Theme theme);
  }

  private final Map<String, Factory> factories = new HashMap<>();

  private final Map<String, Set<String>> extras = new HashMap<>();

  /** Creates a ObjectParser instance with its default configuration. */
  public ObjectParser() {
    register("group", (e, b, t) -> new GroupObject(e.required("id")));
    register(
        "rectangle",
        (e, b, t) -> new RectangleObject(e.required("id")).fill(e.bool("fill", true)),
        "fill");
    register(
        "circle",
        (e, b, t) -> new CircleObject(e.required("id")).fill(e.bool("fill", true)),
        "fill");
    register(
        "ellipse",
        (e, b, t) -> new CircleObject(e.required("id")).fill(e.bool("fill", true)),
        "fill");
    register("line", (e, b, t) -> new LineObject(e.required("id")));
    register("arrow", (e, b, t) -> new ArrowObject(e.required("id")));
    register(
        "text",
        (e, b, t) ->
            new TextObject(e.required("id"), e.get("text", e.text.toString().strip()))
                .style(e.get("font", t.font()), e.number("size", 40), e.get("align", "left"))
                .weight(e.bool("bold", false)),
        "text",
        "bold",
        "font",
        "size",
        "align");
    StandardObjects.install(this);
    ProductionEvents.install(this);
  }

  /**
   * Registers an object factory and the tag-specific permitted XML attributes.
   *
   * @param tag registered XML element name
   * @param factory factory that constructs the registered object
   * @param attributes allowed attribute names or immutable XML attribute map
   * @return this instance for fluent configuration
   */
  public ObjectParser register(String tag, Factory factory, String... attributes) {
    factories.put(tag, factory);
    extras.put(tag, Set.of(attributes));
    return this;
  }

  /**
   * Constructs and validates a scene object, common properties and nested children.
   *
   * @param e located XML element
   * @param base absolute parent directory of the XML lesson for relative assets
   * @param theme active semantic palette
   * @return validated SceneObject model
   * @throws ProjectFormatException if the lesson contains invalid or unsupported content
   */
  public SceneObject parse(XmlElement e, Path base, Theme theme) {
    try {
      Factory factory = factories.get(e.name);
      if (factory == null) throw e.error("Unknown object element");
      Set<String> allowed = new HashSet<>(Set.of("id", "z", "visible", "dashed"));
      for (var p : Property.values()) allowed.add(p.xml);
      allowed.addAll(extras.get(e.name));
      e.only(allowed.toArray(String[]::new));
      SceneObject object = factory.create(e, base, theme);
      object.color(theme.foreground());
      if (e.attributes.containsKey("dashed")) {
        if (!(object instanceof ShapeObject shape))
          throw e.error("dashed requires a shape or arrow");
        shape.dashed(e.bool("dashed", false));
      }
      for (var p : Property.values())
        if (e.attributes.containsKey(p.xml))
          object.set(
              p,
              p == Property.COLOR
                  ? ColorParser.parse(e.required(p.xml)).getRGB()
                  : e.number(p.xml, p.initial));
      object.z(e.integer("z", object.z())).visible(e.bool("visible", true));
      if (object instanceof GroupObject group) {
        for (var child : e.children) group.add(parse(child, base, theme));
      } else if (!e.children.isEmpty()) throw e.error("This object cannot contain child elements");
      return object;
    } catch (ProjectFormatException ex) {
      throw ex;
    } catch (IllegalArgumentException ex) {
      throw e.error(ex.getMessage());
    }
  }
}
