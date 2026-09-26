/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.math;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.objects.TextObject;
import io.jengacode.eduvideo.render.*;
import io.jengacode.eduvideo.util.FontManager;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;

/**
 * Unicode equation with an injectable typesetting backend.
 *
 * <p>Retains an injectable EquationRenderer strategy. Plain Unicode is backward compatible; opt-in
 * structured typesetting supports fractions, roots and scripts. Structured reveal fades the whole
 * equation to avoid cutting markup mid-command.
 */
public final class EquationObject extends TextObject {

  /**
   * Uses typeset metrics for fractions and scripts when the mathematical backend is selected.
   *
   * @param time scene-local sample time
   * @return baseline-relative expression bounds
   */
  @Override
  public java.awt.geom.Rectangle2D bounds(double time) {
    return renderer instanceof MathRenderer
        ? MathRenderer.measure(text, FontManager.get(font, Font.PLAIN, size), align)
        : super.bounds(time);
  }

  private EquationRenderer renderer = (g, s, a) -> TextRenderer.draw(g, s, 0, 0, a);

  /**
   * Creates a configured EquationObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param value initial or assigned value
   */
  public EquationObject(String id, String value) {
    super(id, value);
  }

  /**
   * Selects compact mathematical markup rendering or backward-compatible Unicode text.
   *
   * @param enabled whether this optional display mode is enabled
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public EquationObject typeset(boolean enabled) {
    if (enabled) MathRenderer.validate(text);
    renderer = enabled ? new MathRenderer() : (g, s, a) -> TextRenderer.draw(g, s, 0, 0, a);
    return this;
  }

  /**
   * Replaces the equation layout strategy while retaining the object timeline.
   *
   * @param renderer equation strategy or configured video renderer
   * @return this instance for fluent configuration
   */
  public EquationObject renderer(EquationRenderer renderer) {
    this.renderer = java.util.Objects.requireNonNull(renderer);
    return this;
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    g.setFont(FontManager.get(font, Font.PLAIN, size));
    if (value(Property.HIGHLIGHT, c.time()) > .5) g.setColor(c.theme().warning());
    if (renderer instanceof MathRenderer && value(Property.REVEAL, c.time()) < 1) {
      if (g.getComposite() instanceof AlphaComposite alpha)
        g.setComposite(
            alpha.derive(
                alpha.getAlpha()
                    * (float)
                        io.jengacode.eduvideo.util.Checks.clamp(value(Property.REVEAL, c.time()))));
      renderer.draw(g, text, align);
    } else renderer.draw(g, revealed(c.time()), align);
  }
}
