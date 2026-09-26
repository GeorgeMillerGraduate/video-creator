/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.util;

import java.awt.Color;

/**
 * Parses unambiguous RGB and RGBA hexadecimal literals.
 *
 * <p>Accepts only six-digit RGB or eight-digit RGBA hexadecimal literals. Interpolation operates on
 * individual channels and stores ARGB exactly as a double for timeline compatibility.
 */
public final class ColorParser {
  /** Prevents instantiation of this static utility. */
  private ColorParser() {}

  /**
   * Parses strict #RRGGBB or #RRGGBBAA colour notation.
   *
   * @param s source notation to parse
   * @return validated Color model
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static Color parse(String s) {
    if (s == null || !s.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
      throw new IllegalArgumentException("Invalid color '" + s + "'; use #RRGGBB or #RRGGBBAA");
    long n = Long.parseLong(s.substring(1), 16);
    return s.length() == 7
        ? new Color((int) n)
        : new Color(
            (int) (n >> 24) & 255, (int) (n >> 16) & 255, (int) (n >> 8) & 255, (int) n & 255);
  }

  /**
   * Interpolates red, green, blue and alpha channels independently.
   *
   * @param a first ARGB colour encoded as a double, or channel value in channel()
   * @param b second ARGB colour encoded as a double, or channel value in channel()
   * @param t normalized interpolation progress
   * @return interpolates red, green, blue and alpha channels independently
   */
  public static double mix(double a, double b, double t) {
    Color x = new Color((int) (long) a, true), y = new Color((int) (long) b, true);
    return new Color(
            channel(x.getRed(), y.getRed(), t),
            channel(x.getGreen(), y.getGreen(), t),
            channel(x.getBlue(), y.getBlue(), t),
            channel(x.getAlpha(), y.getAlpha(), t))
        .getRGB();
  }

  /**
   * Interpolates one colour channel with clamped progress.
   *
   * @param a first ARGB colour encoded as a double, or channel value in channel()
   * @param b second ARGB colour encoded as a double, or channel value in channel()
   * @param t normalized interpolation progress
   * @return interpolates one colour channel with clamped progress
   */
  private static int channel(int a, int b, double t) {
    return (int) Math.round(a + (b - a) * Checks.clamp(t));
  }
}
