/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.render;

import io.jengacode.eduvideo.video.Theme;
import java.awt.Color;
import java.util.List;

/**
 * Language strategy returning source-preserving token spans.
 *
 * <p>Strategies return ordered source-preserving token spans. CodeBlockObject controls line layout,
 * selections and timing independently of language syntax rules.
 */
@FunctionalInterface
public interface SyntaxHighlighter {
  /**
   * Immutable text span and its display colour. Concatenating spans must recover the original
   * source line exactly, including spaces.
   *
   * @param text Unicode display text
   * @param color display or highlight colour
   */
  record Token(String text, Color color) {}

  /**
   * Tokenizes one source line into ordered, source-preserving coloured spans.
   *
   * @param line complete source line, including whitespace
   * @param theme active semantic palette
   * @return tokenizes one source line into ordered, source-preserving coloured spans
   */
  List<Token> highlight(String line, Theme theme);
}
