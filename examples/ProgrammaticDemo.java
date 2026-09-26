/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
import io.jengacode.eduvideo.animation.*;
import io.jengacode.eduvideo.objects.*;
import io.jengacode.eduvideo.scene.*;
import io.jengacode.eduvideo.video.*;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Run: java --class-path dist/EduVideo.jar examples/ProgrammaticDemo.java
 *
 * <p>Creates a minimal lesson through the same public model used by XML. Run from the project
 * directory with the packaged JAR on the classpath; it writes one PNG.
 */
public class ProgrammaticDemo {
  /**
   * Builds a minimal scene and writes a demonstration PNG from the Java API.
   *
   * @param args command-line arguments as documented by the entry point
   * @throws Exception if parsing, rendering or output processing fails
   */
  public static void main(String[] args) throws Exception {
    VideoProject project = new VideoProject(new VideoSettings(1280, 720, 30));
    Scene scene = new Scene("intro", 4);
    TextObject title = new TextObject("title", "Your first Java-generated lesson");
    title.style("SansSerif", 48, "center").at(640, 320);
    title.animate(Property.OPACITY, 0, 1, 0, 1, Easing.EASE_OUT);
    scene.add(title);
    project.add(scene);
    ImageIO.write(
        new VideoRenderer(project).renderFrame(45), "png", Path.of("java-demo.png").toFile());
  }
}
