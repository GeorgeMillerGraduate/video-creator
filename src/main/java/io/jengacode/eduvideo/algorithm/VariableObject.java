/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.algorithm;

import io.jengacode.eduvideo.render.TextRenderer;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.util.*;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.util.*;

/**
 * Named value with deterministic step changes.
 *
 * <p>Stores assignments in a sorted map and samples the last assignment at or before the requested
 * time. Backward seeking reconstructs the correct label without undo logic.
 */
public final class VariableObject extends SceneObject {

  private final String name;

  private final NavigableMap<Double, String> values = new TreeMap<>();

  /**
   * Creates a configured VariableObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param name semantic name or diagnostic label
   * @param value initial or assigned value
   */
  public VariableObject(String id, String name, String value) {
    super(id);
    this.name = name;
    values.put(0d, value);
  }

  /**
   * Stores a named value change at a nonnegative scene-local time.
   *
   * @param time sample or event time in scene-local seconds
   * @param value initial or assigned value
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public VariableObject assign(double time, String value) {
    values.put(Checks.nonnegative(time, "assignment time"), value);
    return this;
  }

  /**
   * Returns the latest semantic event endpoint in scene-local seconds for scene validation.
   *
   * @return latest semantic event time in seconds, or zero when none exist
   */
  public double eventEnd() {
    return values.lastKey();
  }

  /**
   * Returns the latest assigned label at or before the requested time.
   *
   * @param t sample time in scene-local seconds
   * @return the latest assigned label at or before the requested time
   */
  public String valueAt(double t) {
    return values.floorEntry(Math.max(0, t)).getValue();
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    g.setFont(FontManager.get("Monospaced", Font.PLAIN, 28));
    TextRenderer.draw(g, name + " = " + valueAt(c.time()), 0, 0, "left");
  }
}
