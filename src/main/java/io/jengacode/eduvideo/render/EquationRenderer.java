/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.render;

import java.awt.Graphics2D;

/**
 * Replace this strategy to integrate LaTeX or component-aware equation layouts.
 *
 * <p>Draws an equation at the local baseline using caller font and colour. Alternate backends can
 * replace the built-in Unicode or compact markup renderer without changing scene and XML
 * architecture.
 */
@FunctionalInterface
public interface EquationRenderer {
  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param equation equation text in the selected renderer notation
   * @param alignment left, center or right horizontal baseline alignment
   */
  void draw(Graphics2D g, String equation, String alignment);
}
