/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.util;

import java.awt.Font;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared cache of immutable fonts.
 *
 * <p>Caches immutable Font objects by family, style and fractional size. Java logical families
 * supply platform fallback; appearance can vary if installed physical fonts differ.
 */
public final class FontManager {
  /** Prevents instantiation of this static utility. */
  private FontManager() {}

  private static final ConcurrentHashMap<String, Font> CACHE = new ConcurrentHashMap<>();

  /**
   * Returns a cached immutable font for family, style and positive fractional size.
   *
   * @param family logical or installed font family name
   * @param style Java Font style constant such as PLAIN, BOLD or ITALIC
   * @param size positive font size in local pixels
   * @return a cached immutable font for family, style and positive fractional size
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public static Font get(String family, int style, float size) {
    Checks.positive(size, "font size");
    String key = family + ":" + style + ":" + size;
    return CACHE.computeIfAbsent(key, k -> new Font(family, style, 1).deriveFont(size));
  }
}
