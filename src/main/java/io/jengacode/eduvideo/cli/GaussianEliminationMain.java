/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.cli;

import io.jengacode.eduvideo.export.ProductionBuilder;
import io.jengacode.eduvideo.video.VideoRenderer;
import io.jengacode.eduvideo.xml.ProjectParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Standalone, editable Gaussian-elimination lesson for the EduVideo 2.0 project.
 *
 * <p>Run this class in NetBeans to build a narrated, music-backed 1080p60 MP4. OpenAI API settings
 * and FFmpeg must already be configured. The class writes its complete XML screenplay beside the
 * finished video so that every cue remains editable. It does not send the creative brief to an AI:
 * the storyboard below is executable choreography.
 *
 * <p>Arguments: {@code --preview} selects 960x540/30fps; {@code --no-speech} deliberately omits
 * OpenAI narration but keeps music; {@code --write-only} validates and writes the XML; {@code
 * --frame SECONDS} renders one full-HD still without contacting OpenAI or using FFmpeg. All outputs
 * go into a new run directory under the project's {@code output} folder.
 */
public final class GaussianEliminationMain {
  /** Plain-language creative brief, retained in each run folder for editing and review. */
  public static final String LESSON_PROMPT =
      """
      JENGA-CODE / LINEAR ALGEBRA
      Title: Gaussian elimination — turn a system into its solution.

      Audience: a student meeting elimination and augmented matrices for the first time.
      Style: restrained midnight blue, cyan coefficient information, purple source rows,
      emerald completed pivots, large readable type, and a quiet music bed under speech.
      Explain every operation. A row is an equation, and each elementary row operation
      preserves the solutions. Move copies of values into a working area before simplifying.
      Apply each operation to all four entries, including the right-hand side.

      Solve this exact system:
        x + y + z = 6
        2x + 3y + z = 11
        x - y + 2z = 5

      Opening, 0–12 seconds:
        Display the system and its augmented matrix. Identify the final column as the
        constants. State the aim: create zeros below the pivots, then solve upward.

      Step 1, 12–26 seconds: R2 <- R2 - 2R1.
        Show [2 - 2*1, 3 - 2*1, 1 - 2*1 | 11 - 2*6].
        Simplify to [0, 1, -1 | -1]. The first variable disappears from row two.
      Step 2, 26–40 seconds: R3 <- R3 - R1.
        Show [1 - 1, -1 - 1, 2 - 1 | 5 - 6].
        Simplify to [0, -2, 1 | -1]. The first variable disappears from row three.
      Step 3, 40–54 seconds: R3 <- R3 + 2R2.
        Show [0 + 2*0, -2 + 2*1, 1 + 2*(-1) | -1 + 2*(-1)].
        Simplify to [0, 0, -1 | -3]. This is row echelon form: -z = -3.
      Step 4, 54–68 seconds: R3 <- -R3.
        Normalise the final pivot to obtain [0, 0, 1 | 3], hence z = 3.
      Step 5, 68–82 seconds: R2 <- R2 + R3.
        Simplify to [0, 1, 0 | 2], hence y = 2. Explain this as solving upward.
      Step 6, 82–96 seconds: R1 <- R1 - R3.
        Simplify to [1, 1, 0 | 3]. Remove z from the first row.
      Step 7, 96–110 seconds: R1 <- R1 - R2.
        Simplify to [1, 0, 0 | 1], hence x = 1.

      Closing, 110–128 seconds:
        The coefficient block is the identity. Read x = 1, y = 2, z = 3.
        Check all three original equations: 1+2+3=6; 2+6+3=11; 1-2+6=5.
        Explain terminology accurately: the forward stage is Gaussian elimination;
        continuing to reduced row echelon form is the Gauss-Jordan extension.
        Conclude that the equations changed form but their common solution did not.

      Motion: highlight destination/source rows, fly operand copies into four aligned
      calculation slots, transform each expression into its numeric answer, and fly those
      four answers back into the changed matrix row. Keep unchanged rows stationary.
      Narration: British English, measured delivery, generous explicit timing windows.
      Audio: use the included Quiet Orbits WAV, loop quietly, fade at both ends and duck
      automatically during measured speech. Do not replace a narration failure with silence.
      """;

  private static final double DURATION = 128;
  private static final double[] SLOT_X = {390, 770, 1150, 1530};
  private static final double[][] ORIGINAL = {{1, 1, 1, 6}, {2, 3, 1, 11}, {1, -1, 2, 5}};

  /**
   * One elementary row operation, its explanatory text and its narration.
   *
   * @param target zero-based destination row
   * @param source source row, or -1 for multiplying the target row
   * @param factor source multiplier, or target multiplier for a scaling operation
   * @param operation visible row-operation notation
   * @param explanation short on-screen explanation
   * @param narration spoken script for the operation
   */
  private record Step(
      int target,
      int source,
      double factor,
      String operation,
      String explanation,
      String narration) {}

  private static final List<Step> STEPS =
      List.of(
          new Step(
              1,
              0,
              -2,
              "R₂ ← R₂ − 2R₁",
              "Remove x from the second equation.\nApply the operation to every entry.",
              "Subtract twice row one from row two. Apply this to every entry, including the"
                  + " constant. The new row is zero, one, minus one, minus one."),
          new Step(
              2,
              0,
              -1,
              "R₃ ← R₃ − R₁",
              "Remove x from the third equation.\nThe first column now has zeros below its pivot.",
              "Now subtract row one from row three. This removes x from the third equation. The"
                  + " first column now has zeros below its pivot."),
          new Step(
              2,
              1,
              2,
              "R₃ ← R₃ + 2R₂",
              "Remove y from the third equation.\nRow echelon form reveals −z = −3.",
              "Add twice row two to row three. The y terms cancel. We have reached row echelon"
                  + " form, and the final equation is minus z equals minus three."),
          new Step(
              2,
              -1,
              -1,
              "R₃ ← −R₃",
              "Make the final pivot equal to one.\nNow we can read z = 3.",
              "Multiply the final row by minus one. Its pivot becomes one, so z equals three. We"
                  + " can now work upward through the system."),
          new Step(
              1,
              2,
              1,
              "R₂ ← R₂ + R₃",
              "Remove z from the second equation.\nThe second row now says y = 2.",
              "Add row three to row two. Minus z and plus z cancel. The second equation now gives y"
                  + " equals two."),
          new Step(
              0,
              2,
              -1,
              "R₁ ← R₁ − R₃",
              "Remove z from the first equation.\nWhat remains is x + y = 3.",
              "Subtract row three from row one. This removes z from the first equation, leaving x"
                  + " plus y equals three."),
          new Step(
              0,
              1,
              -1,
              "R₁ ← R₁ − R₂",
              "Remove y from the first equation.\nThe first row now says x = 1.",
              "Finally subtract row two from row one. This removes y and leaves x equals one. Every"
                  + " variable now has its own pivot row."));

  /** Prevents construction of this runnable lesson utility. */
  private GaussianEliminationMain() {}

  /**
   * Creates and validates the lesson, then renders the requested output.
   *
   * @param args optional --preview, --no-speech, --write-only, or --frame SECONDS
   * @throws Exception if an asset, credential, timing, rendering or encoding check fails
   */
  public static void main(String[] args) throws Exception {
    boolean preview = false, noSpeech = false, writeOnly = false;
    Double frameTime = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--preview" -> preview = true;
        case "--no-speech" -> noSpeech = true;
        case "--write-only" -> writeOnly = true;
        case "--frame" -> {
          if (++i >= args.length) throw new IllegalArgumentException("--frame requires seconds");
          frameTime = Double.parseDouble(args[i]);
          if (!Double.isFinite(frameTime) || frameTime < 0 || frameTime >= DURATION)
            throw new IllegalArgumentException(
                "Frame time must be at least 0 and below 128 seconds");
        }
        default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
      }
    }
    Path projectRoot = findProjectRoot();
    Path music = projectRoot.resolve("assets/music/quiet-orbits.wav");
    if (!Files.isRegularFile(music))
      throw new IllegalArgumentException(
          "Missing music: "
              + music
              + ". Copy assets/music from the EduVideo ZIP into your project.");
    Path outputRoot = projectRoot.resolve("output");
    Files.createDirectories(outputRoot);
    String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    Path run = Files.createTempDirectory(outputRoot, "gaussian-" + stamp + "-");
    String musicReference = run.relativize(music).toString().replace('\\', '/');
    Path xml = run.resolve("gaussian-elimination.xml");
    Files.writeString(xml, screenplay(musicReference), StandardOpenOption.CREATE_NEW);
    Files.writeString(
        run.resolve("LESSON-PROMPT.txt"), LESSON_PROMPT, StandardOpenOption.CREATE_NEW);
    var project = new ProjectParser().parse(xml);
    System.out.println("Validated Gaussian elimination lesson: " + xml);
    if (writeOnly) return;
    if (frameTime != null) {
      Path still = run.resolve("gaussian-frame.png");
      if (!ImageIO.write(
          new VideoRenderer(project).renderFrame((int) Math.floor(frameTime * 60)),
          "png",
          still.toFile())) throw new IllegalStateException("PNG writer unavailable");
      System.out.println("Wrote " + still);
      return;
    }
    Path output = run.resolve("gaussian-elimination.mp4");
    Path cache = Path.of(System.getProperty("user.home"), ".jenga-code", "speech-cache");
    System.out.println(
        noSpeech
            ? "Explicit music-only preview; narration omitted."
            : "Generating OpenAI narration; all voice durations are checked"
                + " before rendering.");
    new ProductionBuilder()
        .build(
            project,
            output,
            preview ? ProductionBuilder.Profile.PREVIEW : ProductionBuilder.Profile.FINAL,
            cache,
            noSpeech,
            0,
            DURATION);
    System.out.println("Finished: " + output);
  }

  /**
   * Locates the project from NetBeans' working directory or a child directory.
   *
   * @return nearest ancestor containing pom.xml
   * @throws IllegalStateException if this process was launched outside the Maven project
   */
  private static Path findProjectRoot() {
    Path p = Path.of("").toAbsolutePath();
    for (int i = 0; i < 8 && p != null; i++, p = p.getParent())
      if (Files.isRegularFile(p.resolve("pom.xml"))) return p;
    throw new IllegalStateException(
        "Run this class from the MathsVideo project folder containing pom.xml.");
  }

  /**
   * Builds the complete timed XML, calculating every matrix state from the row operations.
   *
   * @param musicPath relative path from the generated XML to the music asset
   * @return executable XML screenplay, including text, narration, music and animations
   */
  public static String screenplay(String musicPath) {
    var xml =
        new StringBuilder(
            """
        <?xml version="1.0" encoding="UTF-8"?>
        <edu-video version="1.0" width="1920" height="1080" fps="60">
          <scene id="gaussian-elimination" duration="128" theme="midnight-blue">
            <background grid="false" curves="true" dots="true" intensity="0.16" />
            <text id="brand" x="96" y="75" text="JENGA–CODE / LINEAR ALGEBRA" size="18" color="#36D4FF" bold="true" />
            <text id="title" x="96" y="157" text="Gaussian elimination" size="66" color="#F3F6FC" bold="true" />
            <text id="subtitle" x="99" y="213" text="Change the equations. Preserve their common solution." size="27" color="#9DAFC9" />
            <line id="divider" x="96" y="251" width="1728" height="0" strokeWidth="1" color="#36D4FF" opacity="0.25" />
            <text id="matrixLabel" x="325" y="304" text="AUGMENTED MATRIX" size="19" color="#36D4FF" bold="true" />
            <text id="columnX" x="425" y="344" text="x" size="25" align="center" color="#9DAFC9" />
            <text id="columnY" x="555" y="344" text="y" size="25" align="center" color="#9DAFC9" />
            <text id="columnZ" x="685" y="344" text="z" size="25" align="center" color="#9DAFC9" />
            <text id="columnB" x="815" y="344" text="b" size="25" align="center" color="#9DAFC9" />
            <line id="augmentation" x="750" y="372" width="0" height="285" strokeWidth="2" color="#9DAFC9" opacity="0.7" />
            <panel id="workingPanel" x="170" y="720" width="1580" height="203" padding="0" opacity="0.7" />
            <text id="workLabel" x="210" y="758" text="ONE OPERATION / ALL FOUR ENTRIES" size="16" color="#9DAFC9" bold="true" />
            <text id="footer" x="96" y="1018" text="EDUVIDEO / MATHEMATICS IN MOTION" size="15" color="#9DAFC9" />
            <line id="progress" x="96" y="977" width="1728" height="0" strokeWidth="2" color="#4CF0B3" />
            <animate target="progress" property="drawProgress" from="0" to="1" start="0" duration="128" easing="linear" />
            <text id="intro" x="1000" y="386" text="THREE EQUATIONS / THREE UNKNOWNS" size="19" color="#C09DFF" bold="true" />
            <text id="system" x="1000" y="452" text="x + y + z = 6&#10;2x + 3y + z = 11&#10;x − y + 2z = 5" size="34" color="#F3F6FC" />
            <text id="introNote" x="1000" y="635" text="The final column contains the constants." size="24" color="#9DAFC9" />
            <text id="introWork" x="960" y="843" text="Elementary row operations preserve the solution set." align="center" size="31" color="#4CF0B3" />
        """);
    tag(
        xml,
        "music",
        "src",
        musicPath,
        "start",
        "0",
        "end",
        "128",
        "volume",
        "0.12",
        "loop",
        "true",
        "fade-in",
        "2",
        "fade-out",
        "3");
    for (String id : List.of("title", "subtitle", "intro", "system", "introNote", "introWork"))
      effect(xml, id, "fadeIn", 0, .7);
    for (String id : List.of("intro", "system", "introNote", "introWork"))
      effect(xml, id, "fadeOut", 11.6, .4);
    speak(
        xml,
        "opening",
        .8,
        11.4,
        "Three equations share one solution. We will use row operations to reveal it. The final"
            + " column of this augmented matrix contains the constants.");
    double[][] matrix = Arrays.stream(ORIGINAL).map(double[]::clone).toArray(double[][]::new);
    matrix(xml, "matrix0", matrix);
    for (int i = 0; i < STEPS.size(); i++) {
      Step step = STEPS.get(i);
      double start = 12 + i * 14, landing = start + 9.5;
      double[][] next = Arrays.stream(matrix).map(double[]::clone).toArray(double[][]::new);
      for (int c = 0; c < 4; c++)
        next[step.target][c] =
            step.source < 0
                ? step.factor * matrix[step.target][c]
                : matrix[step.target][c] + step.factor * matrix[step.source][c];
      String oldId = "matrix" + i, newId = "matrix" + (i + 1);
      matrix(xml, newId, next);
      effect(xml, newId, "fadeIn", landing, .18);
      effect(xml, oldId, "fadeOut", landing, .18);
      tag(
          xml,
          "highlight-row",
          "target",
          oldId,
          "row",
          step.target,
          "start",
          start,
          "duration",
          "9.5",
          "color",
          "#36D4FF");
      if (step.source >= 0)
        tag(
            xml,
            "highlight-row",
            "target",
            oldId,
            "row",
            step.source,
            "start",
            start,
            "duration",
            "9.5",
            "color",
            "#C09DFF");
      tag(
          xml,
          "highlight-row",
          "target",
          newId,
          "row",
          step.target,
          "start",
          landing,
          "duration",
          "3.8",
          "color",
          "#4CF0B3");
      text(
          xml,
          "stepTitle" + i,
          1000,
          365,
          "STEP " + (i + 1) + " / " + (i < 3 ? "FORWARD ELIMINATION" : "SOLVE UPWARD"),
          18,
          "#C09DFF");
      text(xml, "operation" + i, 1000, 436, step.operation, 43, "#F3F6FC");
      text(xml, "explanation" + i, 1000, 518, step.explanation, 24, "#9DAFC9");
      text(
          xml,
          "state" + i,
          1000,
          632,
          i == 2
              ? "Upper triangular: the last row is ready to solve."
              : i == 6
                  ? "Identity on the left. The solution on the right."
                  : "Same solutions. A simpler system.",
          22,
          "#4CF0B3");
      for (String stem : List.of("stepTitle", "operation", "explanation", "state")) {
        effect(xml, stem + i, "fadeIn", start, .35);
        effect(xml, stem + i, "fadeOut", start + 13.6, .35);
      }
      speak(xml, "step" + i, start + .3, start + 13.3, step.narration);
      for (int c = 0; c < 4; c++) {
        String slot = "slot" + i + "c" + c;
        // Invisible anchor objects need no renderer changes; their baseline is the origin.
        tag(xml, "text", "id", slot, "x", SLOT_X[c], "y", "835", "text", "", "opacity", "0");
        tag(
            xml,
            "text",
            "id",
            slot + "left",
            "x",
            SLOT_X[c] - 65,
            "y",
            "835",
            "text",
            "",
            "opacity",
            "0");
        tag(
            xml,
            "text",
            "id",
            slot + "right",
            "x",
            SLOT_X[c] + 65,
            "y",
            "835",
            "text",
            "",
            "opacity",
            "0");
        transfer(
            xml,
            oldId + "@cell:" + step.target + ":" + c,
            slot + "left@origin",
            number(matrix[step.target][c]),
            start + 1.6,
            1.5,
            "#36D4FF");
        if (step.source >= 0)
          transfer(
              xml,
              oldId + "@cell:" + step.source + ":" + c,
              slot + "right@origin",
              number(matrix[step.source][c]),
              start + 1.8,
              1.5,
              "#C09DFF");
        String expression = expression(matrix, step, c);
        tag(
            xml,
            "token-equation",
            "id",
            slot + "eq",
            "x",
            SLOT_X[c],
            "y",
            "849",
            "terms",
            "value=" + expression,
            "size",
            "30",
            "spacing",
            "100",
            "color",
            "#F3F6FC");
        effect(xml, slot + "eq", "fadeIn", start + 3.35, .3);
        tag(
            xml,
            "transform-equation",
            "target",
            slot + "eq",
            "terms",
            "value=" + number(next[step.target][c]),
            "start",
            start + 5.8,
            "duration",
            "0.9");
        effect(xml, slot + "eq", "fadeOut", start + 8, .25);
        tag(xml, "hide-cell", "target", oldId, "row", step.target, "column", c, "start", start + 8);
        transfer(
            xml,
            slot + "@origin",
            newId + "@cell:" + step.target + ":" + c,
            number(next[step.target][c]),
            start + 8,
            1.5,
            "#4CF0B3");
      }
      matrix = next;
    }
    verifySolution(matrix);
    text(xml, "solutionLabel", 1000, 365, "READ THE SOLUTION", 19, "#C09DFF");
    text(xml, "solution", 1000, 440, "x = 1     y = 2     z = 3", 39, "#4CF0B3");
    text(
        xml,
        "check",
        1000,
        515,
        "Check the original equations:\n1 + 2 + 3 = 6\n2 + 6 + 3 = 11\n1 − 2 + 6 = 5",
        25,
        "#F3F6FC");
    text(
        xml,
        "ending",
        350,
        831,
        "Gaussian elimination → echelon form → Gauss–Jordan → identity",
        29,
        "#4CF0B3");
    for (String id : List.of("solutionLabel", "solution", "check", "ending"))
      effect(xml, id, "fadeIn", 110, .7);
    speak(
        xml,
        "conclusion",
        110.6,
        127,
        "The solution is x equals one, y equals two, z equals three. All three original equations"
            + " check. Continuing from echelon form to the identity is the Gauss Jordan"
            + " extension.");
    return xml.append("  </scene>\n</edu-video>\n").toString();
  }

  /**
   * Verifies the final identity block and substitutes the solution into the original equations.
   *
   * @param matrix final augmented matrix after all authored elementary operations
   * @throws IllegalStateException if the lesson's arithmetic has been edited inconsistently
   */
  private static void verifySolution(double[][] matrix) {
    for (int r = 0; r < 3; r++)
      for (int c = 0; c < 3; c++)
        if (Math.abs(matrix[r][c] - (r == c ? 1 : 0)) > 1e-9)
          throw new IllegalStateException("Row operations did not produce the identity");
    for (int r = 0; r < 3; r++) {
      double result = 0;
      for (int c = 0; c < 3; c++) result += ORIGINAL[r][c] * matrix[c][3];
      if (Math.abs(result - ORIGINAL[r][3]) > 1e-9)
        throw new IllegalStateException("Solution check failed");
    }
    if (matrix[0][3] != 1 || matrix[1][3] != 2 || matrix[2][3] != 3)
      throw new IllegalStateException("Update the closing script to match the solution");
  }

  /**
   * Formats one explicitly calculated cell expression without hiding the source-row multiplier.
   *
   * @param values current augmented matrix
   * @param step row operation to explain
   * @param column zero-based augmented-matrix column
   * @return readable arithmetic expression for the working area
   */
  private static String expression(double[][] values, Step step, int column) {
    String a = operand(values[step.target][column]);
    if (step.source < 0) return "−(" + number(values[step.target][column]) + ")";
    String b = operand(values[step.source][column]);
    double f = Math.abs(step.factor);
    return a + (step.factor < 0 ? " − " : " + ") + (f == 1 ? "" : number(f) + "×") + b;
  }

  /**
   * Parenthesises negative operands for unambiguous arithmetic.
   *
   * @param value numeric operand
   * @return compact display notation
   */
  private static String operand(double value) {
    return value < 0 ? "(" + number(value) + ")" : number(value);
  }

  /**
   * Formats integral matrix values without decimal noise.
   *
   * @param value finite numeric matrix entry
   * @return locale-independent display number
   */
  private static String number(double value) {
    return Math.abs(value - Math.rint(value)) < 1e-9
        ? Long.toString(Math.round(value))
        : String.format(Locale.ROOT, "%.3f", value);
  }

  /**
   * Adds one immutable matrix state at the shared visual position.
   *
   * @param xml screenplay buffer
   * @param id unique matrix identity
   * @param values numeric augmented-matrix state
   */
  private static void matrix(StringBuilder xml, String id, double[][] values) {
    String cells =
        String.join(
            ";",
            Arrays.stream(values)
                .map(
                    row ->
                        String.join(
                            ",",
                            Arrays.stream(row).mapToObj(GaussianEliminationMain::number).toList()))
                .toList());
    tag(
        xml,
        "matrix",
        "id",
        id,
        "x",
        "360",
        "y",
        "365",
        "values",
        cells,
        "cellWidth",
        "130",
        "cellHeight",
        "100",
        "size",
        "42",
        "color",
        "#36D4FF");
  }

  /**
   * Appends a plain text label with explicit baseline position and colour.
   *
   * @param xml screenplay buffer
   * @param id unique label identity
   * @param x logical baseline x coordinate
   * @param y logical baseline y coordinate
   * @param value displayed text, optionally multiline
   * @param size font size in logical pixels
   * @param color hexadecimal display colour
   */
  private static void text(
      StringBuilder xml, String id, double x, double y, String value, int size, String color) {
    tag(xml, "text", "id", id, "x", x, "y", y, "text", value, "size", size, "color", color);
  }

  /**
   * Appends an ordinary fade effect without creating a separate animation clock.
   *
   * @param xml screenplay buffer
   * @param id target object identity
   * @param name fadeIn or fadeOut
   * @param start scene-local start time
   * @param duration positive effect duration
   */
  private static void effect(
      StringBuilder xml, String id, String name, double start, double duration) {
    tag(xml, "effect", "target", id, "name", name, "start", start, "duration", duration);
  }

  /**
   * Appends a curved travelling copy between two semantic anchors.
   *
   * @param xml screenplay buffer
   * @param from source object@anchor reference
   * @param to destination object@anchor reference
   * @param value copied display value
   * @param start scene-local departure time
   * @param duration flight duration in seconds
   * @param color colour identifying the operand or result
   */
  private static void transfer(
      StringBuilder xml,
      String from,
      String to,
      String value,
      double start,
      double duration,
      String color) {
    tag(
        xml,
        "transfer",
        "from",
        from,
        "to",
        to,
        "text",
        value,
        "start",
        start,
        "duration",
        duration,
        "bend",
        "-65",
        "size",
        "36",
        "color",
        color);
  }

  /**
   * Appends an OpenAI OpenAI narration request with a strict timing window.
   *
   * @param xml screenplay buffer
   * @param id unique narration identity
   * @param start scene-local speech start
   * @param end latest permitted measured completion time
   * @param script plain-language narration
   */
  private static void speak(StringBuilder xml, String id, double start, double end, String script) {
    tag(
        xml,
        "speak",
        "id",
        id,
        "start",
        start,
        "max-end",
        end,
        "language",
        "en-GB",
        "voice",
        "default",
        "rate",
        "0.95",
        "text",
        script);
  }

  /**
   * Writes a self-closing tag, escaping every attribute and preserving explicit line breaks.
   *
   * @param xml screenplay buffer
   * @param name supported XML element name
   * @param attributes alternating attribute names and values
   * @throws IllegalArgumentException if an attribute name has no corresponding value
   */
  private static void tag(StringBuilder xml, String name, Object... attributes) {
    if (attributes.length % 2 != 0) throw new IllegalArgumentException("Attribute pairs required");
    xml.append("    <").append(name);
    for (int i = 0; i < attributes.length; i += 2)
      xml.append(' ')
          .append(attributes[i])
          .append("=\"")
          .append(escape(String.valueOf(attributes[i + 1])))
          .append('"');
    xml.append(" />\n");
  }

  /**
   * Escapes XML attribute metacharacters before writing authored strings.
   *
   * @param value unescaped display string or relative file path
   * @return safe XML attribute content
   */
  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "&#10;");
  }
}
