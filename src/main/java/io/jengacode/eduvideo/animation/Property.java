/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.animation;

/**
 * Properties use doubles; COLOR holds an ARGB integer represented exactly as a double.
 *
 * <p>Defines the XML name and initial value for each numeric property. Objects explicitly declare
 * which properties they support; timeline tracks override initial values.
 */
public enum Property {
  /** Horizontal translation in local pixels. */
  X("x", 0),
  /** Vertical translation in local pixels. */
  Y("y", 0),
  /** Width, or signed endpoint x for line-like objects. */
  WIDTH("width", 100),
  /** Height, or signed endpoint y for line-like objects. */
  HEIGHT("height", 100),
  /** Local clockwise rotation in degrees. */
  ROTATION("rotation", 0),
  /** Horizontal scale factor around the local origin. */
  SCALE_X("scaleX", 1),
  /** Vertical scale factor around the local origin. */
  SCALE_Y("scaleY", 1),
  /** Object opacity, clamped to [0,1] and multiplied by inherited alpha. */
  OPACITY("opacity", 1),
  /** ARGB colour encoded exactly as a double. */
  COLOR("color", 0xffeaf4ff),
  /** Nonnegative local stroke thickness in pixels. */
  STROKE_WIDTH("strokeWidth", 3),
  /** Visible fraction of an outlined path. */
  DRAW_PROGRESS("drawProgress", 1),
  /** Text reveal fraction or triangle angle visibility. */
  REVEAL("reveal", 1),
  /** Positive camera zoom factor. */
  ZOOM("zoom", 1),
  /** Transient emphasis; triangles use edge numbers 1–3, with 0 for none. */
  HIGHLIGHT("highlight", 0),
  /** Outline illumination intensity, clamped to [0,1] when drawn. */
  GLOW("glow", 0),
  /** Triangle angle selection 1–3, with 0 for none. */
  ANGLE_HIGHLIGHT("angleHighlight", 0),
  /** First expression parameter, initially 1. */
  PARAM_A("a", 1),
  /** Second expression parameter, initially 0. */
  PARAM_B("b", 0),
  /** Third expression parameter, initially 0. */
  PARAM_C("c", 0);

  /** Exact attribute/property spelling used in XML. */
  public final String xml;

  /** Default numeric value before configuration or animation. */
  public final double initial;

  /**
   * Creates a configured Property instance.
   *
   * @param xml exact XML property name
   * @param initial default numeric property value
   */
  Property(String xml, double initial) {
    this.xml = xml;
    this.initial = initial;
  }

  /**
   * Resolves a property by its exact XML name.
   *
   * @param s source notation to parse
   * @return the resolved or newly constructed value
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static Property parse(String s) {
    for (var p : values()) if (p.xml.equals(s)) return p;
    throw new IllegalArgumentException("Unsupported property: " + s);
  }
}
