/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.xml;

import io.jengacode.eduvideo.util.Checks;
import java.util.*;

/**
 * Located XML node used by pluggable object factories.
 *
 * <p>Stores an immutable attribute map alongside parse-time child/text buffers. Typed accessors
 * validate finite numbers and strict booleans; error() includes line, element and optional id.
 */
public final class XmlElement {
  /** XML tag name as read by the secure SAX loader. */
  public final String name;

  /** One-based source line where the element starts. */
  public final int line;

  /** Immutable copy of raw XML attribute strings. */
  public final Map<String, String> attributes;

  /** Children populated by SAX parsing, in document order. */
  public final List<XmlElement> children = new ArrayList<>();

  /** Text buffer assembled from SAX character spans. */
  public final StringBuilder text = new StringBuilder();

  /**
   * Creates a configured XmlElement instance.
   *
   * @param name XML element name
   * @param line 1-based source line
   * @param attributes raw attributes to copy defensively
   */
  XmlElement(String name, int line, Map<String, String> attributes) {
    this.name = name;
    this.line = line;
    this.attributes = Map.copyOf(attributes);
  }

  /**
   * Returns an attribute string or the supplied fallback.
   *
   * @param key XML attribute name
   * @param fallback value used when the attribute is absent
   * @return an attribute string or the supplied fallback
   */
  public String get(String key, String fallback) {
    return attributes.getOrDefault(key, fallback);
  }

  /**
   * Returns a nonblank attribute or throws a located validation error.
   *
   * @param key XML attribute name
   * @return a nonblank attribute or throws a located validation error
   */
  public String required(String key) {
    String s = get(key, "");
    if (s.isBlank()) throw error("Missing '" + key + "'");
    return s;
  }

  /**
   * Parses a finite numeric attribute, using its fallback when absent.
   *
   * @param key XML attribute name
   * @param fallback value used when the attribute is absent
   * @return parses a finite numeric attribute, using its fallback when absent
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public double number(String key, double fallback) {
    try {
      return Checks.finite(Double.parseDouble(get(key, Double.toString(fallback))), key);
    } catch (IllegalArgumentException ex) {
      throw error("Invalid number for '" + key + "': " + get(key, ""));
    }
  }

  /**
   * Parses an exactly integral in-range attribute value.
   *
   * @param key XML attribute name
   * @param fallback value used when the attribute is absent
   * @return parses an exactly integral in-range attribute value
   */
  public int integer(String key, int fallback) {
    double n = number(key, fallback);
    if (n != Math.rint(n) || n < Integer.MIN_VALUE || n > Integer.MAX_VALUE)
      throw error(key + " must be an integer");
    return (int) n;
  }

  /**
   * Parses a strict true/false attribute, using its fallback when absent.
   *
   * @param key XML attribute name
   * @param fallback value used when the attribute is absent
   * @return true when the documented condition holds; false otherwise
   */
  public boolean bool(String key, boolean fallback) {
    String s = get(key, Boolean.toString(fallback));
    if (!s.equals("true") && !s.equals("false")) throw error(key + " must be true or false");
    return Boolean.parseBoolean(s);
  }

  /**
   * Rejects attributes outside the explicitly permitted vocabulary.
   *
   * @param allowed permitted attribute names
   */
  public void only(String... allowed) {
    Set<String> set = new HashSet<>(Arrays.asList(allowed));
    for (String k : attributes.keySet())
      if (!set.contains(k)) throw error("Unknown attribute '" + k + "'");
  }

  /**
   * Creates an exception identifying source line, element name and optional ID.
   *
   * @param message human-readable failure explanation
   * @return creates an exception identifying source line, element name and optional ID
   */
  public ProjectFormatException error(String message) {
    return new ProjectFormatException(
        "Line "
            + line
            + ", <"
            + name
            + ">"
            + (attributes.containsKey("id") ? " id='" + attributes.get("id") + "'" : "")
            + ": "
            + message);
  }
}
