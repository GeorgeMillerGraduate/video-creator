/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.render;

import io.jengacode.eduvideo.video.Theme;
import java.util.*;
import java.util.regex.*;

/**
 * Basic Java keywords, strings, numeric literals and line comments.
 *
 * <p>Recognizes Java keywords, numeric literals, quoted strings/chars and line comments. It
 * intentionally does not implement a complete Java lexer or multi-line lexical state.
 */
public final class JavaHighlighter implements SyntaxHighlighter {

  private static final Pattern TOKENS =
      Pattern.compile(
          "//.*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_$][A-Za-z0-9_$]*\\b");

  private static final Set<String> KEYWORDS =
      Set.of(
          "public",
          "private",
          "protected",
          "static",
          "final",
          "class",
          "interface",
          "record",
          "void",
          "int",
          "double",
          "float",
          "long",
          "boolean",
          "char",
          "new",
          "return",
          "for",
          "while",
          "if",
          "else",
          "switch",
          "case",
          "break",
          "continue",
          "true",
          "false",
          "null",
          "var",
          "try",
          "catch",
          "throw",
          "throws",
          "import",
          "package",
          "extends",
          "implements");

  /**
   * Recognizes Java tokens while preserving intervening text and whitespace.
   *
   * @param line complete source line, including whitespace
   * @param theme active semantic palette
   * @return recognizes Java tokens while preserving intervening text and whitespace
   */
  public List<Token> highlight(String line, Theme theme) {
    List<Token> result = new ArrayList<>();
    Matcher m = TOKENS.matcher(line);
    int end = 0;
    while (m.find()) {
      if (m.start() > end)
        result.add(new Token(line.substring(end, m.start()), theme.foreground()));
      String s = m.group();
      var color =
          s.startsWith("//")
              ? theme.muted()
              : s.startsWith("\"") || s.startsWith("'")
                  ? theme.success()
                  : KEYWORDS.contains(s)
                      ? theme.secondary()
                      : Character.isDigit(s.charAt(0)) ? theme.warning() : theme.foreground();
      result.add(new Token(s, color));
      end = m.end();
    }
    if (end < line.length()) result.add(new Token(line.substring(end), theme.foreground()));
    return result;
  }
}
