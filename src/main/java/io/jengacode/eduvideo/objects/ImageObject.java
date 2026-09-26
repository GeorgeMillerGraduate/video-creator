/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.objects;

import io.jengacode.eduvideo.animation.Property;
import io.jengacode.eduvideo.scene.SceneObject;
import io.jengacode.eduvideo.video.RenderContext;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Image decoded once at construction; caller must not mutate the source raster.
 *
 * <p>Decodes supported ImageIO formats once from a local path and initializes dimensions to native
 * size. Rendering can scale the raster through animated width/height and the normal object
 * transform.
 */
public final class ImageObject extends SceneObject {

  private final BufferedImage image;

  /**
   * Creates a configured ImageObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param path input file path
   * @throws IOException if file access or encoding fails
   */
  public ImageObject(String id, Path path) throws IOException {
    super(id);
    image = ImageIO.read(path.toFile());
    if (image == null) throw new IOException("Unsupported image: " + path);
    set(Property.WIDTH, image.getWidth());
    set(Property.HEIGHT, image.getHeight());
  }

  /**
   * Draws the visual content in the supplied local coordinate system.
   *
   * @param g graphics context positioned in local coordinates
   * @param c frame context with scene-local time and active theme
   */
  protected void draw(Graphics2D g, RenderContext c) {
    g.drawImage(
        image,
        0,
        0,
        (int) value(Property.WIDTH, c.time()),
        (int) value(Property.HEIGHT, c.time()),
        null);
  }
}
