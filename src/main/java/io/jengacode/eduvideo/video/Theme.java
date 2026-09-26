/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.video;

import io.jengacode.eduvideo.util.ColorParser;
import java.awt.Color;

/**
 * Palette and typography shared by objects.
 *
 * <p>Preserves the original seven-component palette constructor and adds semantic derived colours.
 * Named palettes coordinate accents rather than recolouring every item identically. Colours and
 * font must be non-null.
 *
 * @param background background retained by this immutable descriptor
 * @param foreground foreground retained by this immutable descriptor
 * @param primary primary retained by this immutable descriptor
 * @param secondary secondary retained by this immutable descriptor
 * @param success success retained by this immutable descriptor
 * @param warning warning retained by this immutable descriptor
 * @param font font family or measured Font, as required by the overload
 */
public record Theme(
    Color background,
    Color foreground,
    Color primary,
    Color secondary,
    Color success,
    Color warning,
    String font) {
  /**
   * Creates a configured Theme instance.
   *
   * @param background background used by this operation
   * @param foreground foreground used by this operation
   * @param primary primary used by this operation
   * @param secondary secondary used by this operation
   * @param success success used by this operation
   * @param warning warning used by this operation
   * @param font font family or measured Font, as required by the overload
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public Theme {
    java.util.Objects.requireNonNull(background);
    java.util.Objects.requireNonNull(foreground);
    java.util.Objects.requireNonNull(primary);
    java.util.Objects.requireNonNull(secondary);
    java.util.Objects.requireNonNull(success);
    java.util.Objects.requireNonNull(warning);
    if (font == null || font.isBlank())
      throw new IllegalArgumentException("Theme font is required");
  }

  /**
   * Resolves a supported named palette; unknown names are rejected.
   *
   * @param name semantic name or diagnostic label
   * @return resolves a supported named palette; unknown names are rejected
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static Theme named(String name) {
    return switch (name.toLowerCase(java.util.Locale.ROOT)) {
      case "dark" -> dark();
      case "midnight-blue" ->
          new Theme(
              c("#050D1A"),
              c("#F0F6FF"),
              c("#24D4FF"),
              c("#5990FF"),
              c("#5CF2B1"),
              c("#FFD278"),
              "SansSerif");
      case "emerald-green" ->
          new Theme(
              c("#031410"),
              c("#F0FFF8"),
              c("#35EBA8"),
              c("#45D9EC"),
              c("#87F5BD"),
              c("#FFDC78"),
              "SansSerif");
      case "deep-purple" ->
          new Theme(
              c("#0C081D"),
              c("#F8F2FF"),
              c("#CE86FF"),
              c("#70DEFF"),
              c("#80EDCC"),
              c("#FFE18E"),
              "SansSerif");
      default -> throw new IllegalArgumentException("Unknown theme: " + name);
    };
  }

  /**
   * Derives a softly tinted secondary background from the primary accent.
   *
   * @return derives a softly tinted secondary background from the primary accent
   */
  public Color backgroundSecondary() {
    return blend(background, primary, .11);
  }

  /**
   * Derives the coordinated lower-contrast panel outline colour.
   *
   * @return derives the coordinated lower-contrast panel outline colour
   */
  public Color panelBorder() {
    return blend(background, primary, .42);
  }

  /**
   * Returns the semantic high-importance colour.
   *
   * @return the semantic high-importance colour
   */
  public Color highlight() {
    return warning;
  }

  /**
   * Derives a readable axis colour from foreground and background.
   *
   * @return derives a readable axis colour from foreground and background
   */
  public Color axis() {
    return blend(background, foreground, .83);
  }

  /**
   * Returns the primary illumination colour.
   *
   * @return the primary illumination colour
   */
  public Color glow() {
    return primary;
  }

  /**
   * Returns the original default palette for legacy lessons.
   *
   * @return the original default palette for legacy lessons
   */
  public static Theme dark() {
    return new Theme(
        c("#07111F"),
        c("#EAF4FF"),
        c("#38BDF8"),
        c("#A78BFA"),
        c("#4ADE80"),
        c("#FBBF24"),
        "SansSerif");
  }

  /**
   * Background-adjacent panel color derived from this palette.
   *
   * <p>Derives a low-contrast panel fill from the palette.
   *
   * @return background-adjacent panel color derived from this palette
   */
  public Color panel() {
    return blend(background, foreground, .055);
  }

  /**
   * Lower-contrast foreground for indices, line numbers and comments.
   *
   * <p>Derives lower-contrast foreground for secondary text and indices.
   *
   * @return lower-contrast foreground for indices, line numbers and comments
   */
  public Color muted() {
    return blend(background, foreground, .5);
  }

  /**
   * Foreground-colored grid lines with explicit alpha.
   *
   * <p>Returns a translucent major or minor grid colour.
   *
   * @param minor true for minor rather than major grid lines
   * @return foreground-colored grid lines with explicit alpha
   */
  public Color grid(boolean minor) {
    return new Color(
        foreground.getRed(), foreground.getGreen(), foreground.getBlue(), minor ? 12 : 30);
  }

  /**
   * Translucent primary accent for highlighted code lines.
   *
   * <p>Returns a translucent primary accent for source-code selections.
   *
   * @return translucent primary accent for highlighted code lines
   */
  public Color selection() {
    return new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 45);
  }

  /**
   * Interpolates palette channels without modifying either colour.
   *
   * @param a first palette colour
   * @param b second palette colour
   * @param amount interpolation fraction
   * @return interpolates palette channels without modifying either colour
   */
  private static Color blend(Color a, Color b, double amount) {
    return new Color((int) (long) ColorParser.mix(a.getRGB(), b.getRGB(), amount), true);
  }

  /**
   * Parses a palette hexadecimal literal.
   *
   * @param s RGB or RGBA hexadecimal literal
   * @return parses a palette hexadecimal literal
   */
  private static Color c(String s) {
    return ColorParser.parse(s);
  }
}
