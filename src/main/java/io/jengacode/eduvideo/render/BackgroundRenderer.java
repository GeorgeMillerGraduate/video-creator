/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.render;

import io.jengacode.eduvideo.video.Theme;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Configurable, deterministic procedural backdrop; decoded images are optional and never required.
 *
 * <p>Combines a linear base, radial illumination, optional image, perspective grid, curves, fixed
 * dots and vignette. Patterns are deterministic and screen-fixed. Images decode during
 * configuration rather than once per frame.
 */
public final class BackgroundRenderer {

  private boolean grid = true, curves = true, dots = true, vignette = true;

  private double intensity = .5;

  private BufferedImage image;

  /**
   * Configures deterministic backdrop layers and clamped effect intensity.
   *
   * @param grid whether grid lines are enabled
   * @param curves whether abstract background curves are enabled
   * @param dots whether the fixed dot pattern is enabled
   * @param vignette whether to darken the background edges
   * @param intensity effect strength, clamped to [0,1]
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public BackgroundRenderer options(
      boolean grid, boolean curves, boolean dots, boolean vignette, double intensity) {
    this.grid = grid;
    this.curves = curves;
    this.dots = dots;
    this.vignette = vignette;
    this.intensity =
        io.jengacode.eduvideo.util.Checks.clamp(
            io.jengacode.eduvideo.util.Checks.finite(intensity, "background intensity"));
    return this;
  }

  /**
   * Decodes an optional background raster once during configuration.
   *
   * @param path input file path
   * @return this instance for fluent configuration
   * @throws IOException if file access or encoding fails
   */
  public BackgroundRenderer image(Path path) throws IOException {
    image = ImageIO.read(path.toFile());
    if (image == null) throw new IOException("Unsupported background image: " + path);
    return this;
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param graphics caller graphics context, preserved where a child context is created
   * @param w canvas or shape width in pixels
   * @param h canvas or shape height in pixels
   * @param theme active semantic palette
   */
  public void draw(Graphics2D graphics, int w, int h, Theme theme) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setPaint(
          new GradientPaint(0, 0, theme.backgroundSecondary(), w * .7f, h, theme.background()));
      g.fillRect(0, 0, w, h);
      if (image != null) g.drawImage(image, 0, 0, w, h, null);
      g.setPaint(
          new RadialGradientPaint(
              new Point2D.Double(w * .85, h * .25),
              (float) Math.max(w, h),
              new float[] {0, 1},
              new Color[] {
                GlowRenderer.alpha(theme.primary(), .09 * intensity), new Color(0, 0, 0, 0)
              }));
      g.fillRect(0, 0, w, h);
      g.setStroke(new BasicStroke(1));
      if (grid) {
        double horizon = h * .74;
        g.setColor(GlowRenderer.alpha(theme.primary(), .16 * intensity));
        for (int i = -14; i <= 14; i++)
          g.draw(new Line2D.Double(w * .5 + i * w * .019, horizon, w * .5 + i * w * .13, h));
        for (int i = 0; i < 10; i++) {
          double y = horizon + (h - horizon) * Math.pow(i / 9d, 2);
          g.draw(new Line2D.Double(0, y, w, y));
        }
      }
      if (curves) {
        g.setColor(GlowRenderer.alpha(theme.secondary(), .12 * intensity));
        for (int i = 0; i < 5; i++)
          g.draw(
              new CubicCurve2D.Double(
                  -w * .1, h * (.2 + i * .04), w * .2, -h * .2, w * .05, h * .8, w * .35, h * 1.1));
        for (int i = 0; i < 4; i++)
          g.draw(new Ellipse2D.Double(w * .86 + i * 18, -h * .17 + i * 18, w * .36, h * .65));
      }
      if (dots) {
        // Fixed arithmetic pattern avoids random state and is stable under backward seeking.
        g.setColor(GlowRenderer.alpha(theme.primary(), .18 * intensity));
        for (int i = 0; i < 75; i++) {
          double x = ((i * 7919) % 997) / 997d * w, y = ((i * 3571) % 991) / 991d * h;
          g.fill(new Ellipse2D.Double(x, y, 2, 2));
        }
      }
      if (vignette) {
        g.setPaint(
            new RadialGradientPaint(
                new Point2D.Double(w * .5, h * .45),
                (float) (Math.hypot(w, h) * .65),
                new float[] {0, .55f, 1},
                new Color[] {
                  new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)
                }));
        g.fillRect(0, 0, w, h);
      }
    } finally {
      g.dispose();
    }
  }
}
