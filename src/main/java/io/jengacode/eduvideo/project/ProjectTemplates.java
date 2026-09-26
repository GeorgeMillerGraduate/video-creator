/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.project;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Resource-backed project templates in the parser's real edu-video vocabulary. */
public final class ProjectTemplates {
  /** Available template resource names; UI labels derive from these stable names. */
  public static final List<String> NAMES = List.of("Blank", "Mathematics", "Programming", "Matrix");

  /** Expands safe scalar placeholders without inventing another project format. */
  public String create(
      String template, String title, int width, int height, double fps, double duration)
      throws IOException {
    if (!NAMES.contains(template) || width < 320 || height < 180 || fps <= 0 || duration <= 0)
      throw new IllegalArgumentException("Invalid template settings");
    try (var in =
        getClass()
            .getResourceAsStream("/templates/" + template.toLowerCase(Locale.ROOT) + ".xml")) {
      if (in == null) throw new IOException("Template is missing: " + template);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8)
          .replace(
              "${title}", title.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;"))
          .replace("${width}", "" + width)
          .replace("${height}", "" + height)
          .replace("${fps}", "" + fps)
          .replace("${duration}", "" + duration);
    }
  }
}
