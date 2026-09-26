/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.cli;

import io.jengacode.eduvideo.app.StudioSettings;
import io.jengacode.eduvideo.export.ProductionBuilder;
import io.jengacode.eduvideo.video.VideoRenderer;
import io.jengacode.eduvideo.xml.ProjectParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * A self-contained, narrated 78-second Sudoku lesson for EduVideo Studio 3.0. A real recursive
 * solver records placements and reversals; the existing XML engine animates that trace. The first
 * failed branch is explained slowly before the remaining successful search accelerates. No AI,
 * cloud credentials or hard-coded solution is used.
 *
 * <p>Run from the Maven project directory. Requires configured OpenAI speech and FFmpeg for video
 * production. Use --write-only to create the editable XML without either tool, or --frame 30 to
 * export a still. Default video quality is PREVIEW; --final selects FINAL.
 */
public final class SudokuBacktrackingMain {
  private static final double DURATION = 78;
  private static final int X = 130, Y = 270, CELL = 70;
  private static final String CYAN = "#38BDF8", GREEN = "#4CF0B3", RED = "#FB7185";
  private static final String PUZZLE =
      "534608902"
          + "070000048"
          + "198340567"
          + "850061423"
          + "020050790"
          + "713024856"
          + "961537204"
          + "280009605"
          + "040086179";

  /** One actual solver operation; removal restores the cell to its previous empty state. */
  private record Step(int cell, int value, boolean removal) {}

  /** This class is a runnable lesson, not an instantiable service. */
  private SudokuBacktrackingMain() {}

  /**
   * Generates XML and optionally a PNG or narrated MP4 in a new output/sudoku-* folder.
   *
   * @param args --final, --write-only, --no-speech, or --frame SECONDS
   * @throws Exception if validation, speech synthesis, file writing or encoding fails
   */
  public static void main(String[] args) throws Exception {
    boolean finalQuality = false, writeOnly = false, noSpeech = false;
    Double still = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--final" -> finalQuality = true;
        case "--write-only" -> writeOnly = true;
        case "--no-speech" -> noSpeech = true;
        case "--frame" -> {
          if (++i >= args.length) throw new IllegalArgumentException("--frame requires seconds");
          still = Double.parseDouble(args[i]);
          if (!Double.isFinite(still) || still < 0 || still >= DURATION)
            throw new IllegalArgumentException("Frame time must be in [0, 78)");
        }
        default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
      }
    }
    Path root = Path.of("").toAbsolutePath();
    while (root != null && !Files.isRegularFile(root.resolve("pom.xml"))) root = root.getParent();
    if (root == null)
      throw new IllegalStateException("Run from your EduVideo Maven project folder");
    Files.createDirectories(root.resolve("output"));
    Path run = Files.createTempDirectory(root.resolve("output"), "sudoku-");
    Path xml = run.resolve("sudoku-backtracking.xml");
    Path music = root.resolve("assets/music/quiet-orbits.wav");
    String musicRef =
        Files.isRegularFile(music) ? run.relativize(music).toString().replace('\\', '/') : null;
    Files.writeString(xml, screenplay(musicRef));
    var project = new ProjectParser().parse(xml);
    System.out.println("Editable lesson: " + xml);
    if (writeOnly) return;
    if (still != null) {
      Path png = run.resolve("sudoku-frame.png");
      ImageIO.write(
          new VideoRenderer(project).renderFrame((int) (still * 30)), "png", png.toFile());
      System.out.println("Frame: " + png);
      return;
    }
    Path mp4 = run.resolve("sudoku-backtracking.mp4");
    System.out.println(
        "Producing 78 seconds of animation with "
            + (noSpeech ? "speech disabled" : "OpenAI narration"));
    new ProductionBuilder()
        .build(
            project,
            mp4,
            finalQuality ? ProductionBuilder.Profile.FINAL : ProductionBuilder.Profile.PREVIEW,
            new StudioSettings().cache(),
            noSpeech,
            0,
            DURATION);
    System.out.println("Finished: " + mp4);
  }

  /**
   * Creates the complete editable screenplay from a verified recursive search trace.
   *
   * @param music optional relative or absolute music path; null omits the music bed
   * @return XML accepted by the Studio's shared ProjectParser
   */
  public static String screenplay(String music) {
    int[] original = PUZZLE.chars().map(c -> c - '0').toArray();
    int[] solved = original.clone();
    List<Step> trace = new ArrayList<>();
    if (!solve(solved, trace)) throw new IllegalStateException("Puzzle has no solution");
    // The narration describes this exact opening. Fail explicitly if the puzzle is edited.
    if (!trace
        .subList(0, 3)
        .equals(List.of(new Step(4, 1, false), new Step(4, 1, true), new Step(4, 7, false))))
      throw new IllegalStateException("Puzzle changed: update the first-branch narration too");
    StringBuilder x =
        new StringBuilder(
            """
        <?xml version="1.0" encoding="UTF-8"?>
        <edu-video version="1.0" width="1920" height="1080" fps="30">
        <scene id="sudoku" duration="78" theme="midnight-blue">
        <background grid="false" curves="true" dots="true" intensity="0.12"/>
        """);
    text(x, "brand", 100, 75, 20, CYAN, "JENGA-CODE / ALGORITHMS");
    text(x, "title", 100, 155, 64, "#F3F6FC", "Sudoku: learn to backtrack");
    text(
        x,
        "sub",
        103,
        211,
        27,
        "#9DAFC9",
        "Try a possibility. Follow it. Undo it when it cannot work.");
    box(x, "board", X, Y, 630, 630, "#111F34", true, 1);
    for (int i = 0; i <= 9; i++) {
      String color = i % 3 == 0 ? "#9DAFC9" : "#34465E";
      line(x, "v" + i, X + i * CELL, Y, 0, 630, color, i % 3 == 0 ? 3 : 1);
      line(x, "h" + i, X, Y + i * CELL, 630, 0, color, i % 3 == 0 ? 3 : 1);
      if (i < 9) {
        text(x, "col" + i, X + i * CELL + 29, Y - 17, 16, "#9DAFC9", "" + (i + 1));
        text(x, "row" + i, X - 28, Y + i * CELL + 42, 16, "#9DAFC9", "" + (i + 1));
      }
    }
    text(
        x,
        "legend",
        130,
        952,
        22,
        "#9DAFC9",
        "WHITE  given     CYAN  trial     RED  undo     GREEN  solution");
    text(
        x,
        "footer",
        100,
        1022,
        19,
        "#9DAFC9",
        "RECURSIVE DEPTH-FIRST SEARCH / ROWS, COLUMNS AND 3 × 3 BOXES");
    for (int i = 0; i < 81; i++)
      if (original[i] != 0) digit(x, "given" + i, i, original[i], "#E8EEF8");
    outline(x, "ruleRow", X, Y, 630, 70, CYAN, 9, 18);
    outline(x, "ruleCol", X + 4 * CELL, Y, 70, 630, "#C09DFF", 11, 18);
    outline(x, "ruleBox", X + 3 * CELL, Y, 210, 210, GREEN, 13, 18);
    outline(x, "failedNext", X + 7 * CELL, Y, 70, 70, RED, 27, 37);
    outline(x, "undoCell", X + 4 * CELL, Y, 70, 70, RED, 37, 46);
    String[] active = new String[81];
    for (int n = 0; n < trace.size(); n++) {
      Step step = trace.get(n);
      double t = n == 0 ? 18 : n == 1 ? 37 : n == 2 ? 46 : 55 + (n - 3) * 10.0 / (trace.size() - 3);
      if (step.removal) {
        effect(x, active[step.cell], "fadeOut", t, .35);
        active[step.cell] = null;
      } else {
        String id = "placement" + n;
        active[step.cell] = id;
        digit(x, id, step.cell, step.value, CYAN);
        effect(x, id, "popIn", t, .3);
        outline(
            x,
            "focus" + n,
            X + step.cell % 9 * CELL,
            Y + step.cell / 9 * CELL,
            CELL,
            CELL,
            CYAN,
            t,
            t + (n < 3 ? 8.5 : .35));
      }
    }
    for (int i = 0; i < 81; i++)
      if (original[i] == 0) {
        effect(x, active[i], "fadeOut", 66, .3);
        digit(x, "solved" + i, i, solved[i], GREEN);
        effect(x, "solved" + i, "fadeIn", 66, .5);
      }
    card(
        x,
        0,
        9,
        "01 / THE IDEA",
        "A search with a way back",
        "Fill an empty cell.\n"
            + "If a choice causes a dead end,\n"
            + "undo it and try the next possibility.",
        "Backtracking tries a choice, explores its consequences, and undoes choices that lead to a"
            + " dead end.");
    card(
        x,
        9,
        18,
        "02 / CHECK THE RULES",
        "Row. Column. Box.",
        "Every digit from 1 to 9 appears\n"
            + "once in each row, each column\n"
            + "and each three by three box.",
        "Check three rules: no repeated digit in a row, a column, or a three by three box.");
    card(
        x,
        18,
        27,
        "03 / MAKE A CHOICE",
        "Try 1 at row 1, column 5",
        "The legal candidates are 1 and 7.\n"
            + "Our solver tries the smaller one first.\n"
            + "Legal now does not mean solved later.",
        "This cell allows one or seven. Try one first. It is legal now, but might cause problems"
            + " later.");
    card(
        x,
        27,
        37,
        "04 / FIND THE DEAD END",
        "The next cell has no option",
        "Row 1, column 8 needs a 1:\n"
            + "its column already contains a 7.\n"
            + "But our trial 1 now blocks the row.",
        "The highlighted cell needs one: its column already contains seven. Our trial one blocks"
            + " the row. No candidate remains.");
    card(
        x,
        37,
        46,
        "05 / BACKTRACK",
        "Undo the last trial",
        "Erase the 1 we just placed.\n"
            + "Return to the earlier decision.\n"
            + "The original clues stay untouched.",
        "Backtrack: erase our trial one and return to the previous decision. Never change the"
            + " original clues.");
    card(
        x,
        46,
        55,
        "06 / TRY THE ALTERNATIVE",
        "Try 7 instead",
        "Row 1, column 5 now contains 7.\n"
            + "That leaves 1 available in column 8.\n"
            + "The search can move forward again.",
        "Try seven instead. One is now available for the next empty cell. This branch can"
            + " continue.");
    card(
        x,
        55,
        66,
        "07 / CONTINUE RECURSIVELY",
        "The same rule, at every cell",
        "Choose an empty cell.\nTry a legal digit and recurse.\nUndo it if that branch fails.",
        "The recursive solver continues through the remaining cells. Each placement is calculated."
            + " If another branch failed, it would backtrack again.");
    card(
        x,
        66,
        78,
        "08 / SOLVED",
        "A complete, valid board",
        "All rows, columns and boxes agree.\n"
            + "Try → explore → undo if needed.\n"
            + "That is the backtracking pattern.",
        "Solved. Every row, column, and box obeys the rules. Try, explore, and undo if needed. That"
            + " is backtracking.");
    if (music != null)
      x.append("<music src=\"")
          .append(escape(music))
          .append(
              "\" start=\"0\" end=\"78\" volume=\"0.09\" fade-in=\"2\" fade-out=\"3\""
                  + " loop=\"true\"/>\n");
    return x.append("</scene></edu-video>\n").toString();
  }

  /** Records depth-first placements and actual rollbacks in row-major, ascending-digit order. */
  private static boolean solve(int[] board, List<Step> steps) {
    int cell = 0;
    while (cell < 81 && board[cell] != 0) cell++;
    if (cell == 81) return true;
    for (int value = 1; value <= 9; value++)
      if (legal(board, cell, value)) {
        board[cell] = value;
        steps.add(new Step(cell, value, false));
        if (solve(board, steps)) return true;
        board[cell] = 0;
        steps.add(new Step(cell, value, true));
      }
    return false;
  }

  /** Checks all three Sudoku constraints before a candidate is placed. */
  private static boolean legal(int[] board, int cell, int value) {
    int r = cell / 9, c = cell % 9;
    for (int k = 0; k < 9; k++)
      if (board[r * 9 + k] == value
          || board[k * 9 + c] == value
          || board[(r / 3 * 3 + k / 3) * 9 + c / 3 * 3 + k % 3] == value) return false;
    return true;
  }

  /** Adds one timed explanation with its matching, non-overlapping speech cue. */
  private static void card(
      StringBuilder x,
      double start,
      double end,
      String label,
      String title,
      String body,
      String speech) {
    String stem = "card" + (int) start;
    text(x, stem + "label", 920, 340, 22, CYAN, label);
    text(x, stem + "title", 920, 415, 37, "#F3F6FC", title);
    text(x, stem + "body", 920, 505, 29, "#B9C8DC", body);
    for (String suffix : List.of("label", "title", "body"))
      window(x, stem + suffix, start, end - .1);
    x.append(
        String.format(
                Locale.ROOT,
                "<speak id=\"%s\" start=\"%.2f\" max-end=\"%.2f\">%s</speak>%%n",
                stem,
                start + .4,
                end - .4,
                escape(speech))
            .replace("%n", "\n"));
  }

  /** Places a digit on the fixed board with consistent baseline alignment. */
  private static void digit(StringBuilder x, String id, int cell, int value, String color) {
    text(x, id, X + cell % 9 * CELL + 24, Y + cell / 9 * CELL + 48, 39, color, "" + value);
  }

  /** Emits safely escaped XML text; line breaks are retained as XML character references. */
  private static void text(
      StringBuilder x, String id, int px, int py, int size, String color, String text) {
    x.append(
        String.format(
            Locale.ROOT,
            "<text id=\"%s\" x=\"%d\" y=\"%d\" size=\"%d\" color=\"%s\" text=\"%s\"/>%n",
            id,
            px,
            py,
            size,
            color,
            escape(text).replace("\n", "&#10;")));
  }

  /** Draws the board backing or an unfilled selection boundary. */
  private static void box(
      StringBuilder x,
      String id,
      int px,
      int py,
      int w,
      int h,
      String color,
      boolean fill,
      int stroke) {
    x.append(
        String.format(
            Locale.ROOT,
            "<rectangle id=\"%s\" x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" color=\"%s\""
                + " fill=\"%s\" strokeWidth=\"%d\"/>%n",
            id,
            px,
            py,
            w,
            h,
            color,
            fill,
            stroke));
  }

  /** Adds a grid divider; every third divider is heavier to identify Sudoku boxes. */
  private static void line(
      StringBuilder x, String id, int px, int py, int w, int h, String color, int stroke) {
    x.append(
        String.format(
            Locale.ROOT,
            "<line id=\"%s\" x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" color=\"%s\""
                + " strokeWidth=\"%d\"/>%n",
            id,
            px,
            py,
            w,
            h,
            color,
            stroke));
  }

  /** Highlights a constraint or active cell only during its associated explanation. */
  private static void outline(
      StringBuilder x,
      String id,
      int px,
      int py,
      int w,
      int h,
      String color,
      double start,
      double end) {
    box(x, id, px + 2, py + 2, w - 4, h - 4, color, false, 4);
    window(x, id, start, end);
  }

  /** Gives an object a short entrance and exit without retaining it outside the interval. */
  private static void window(StringBuilder x, String id, double start, double end) {
    effect(x, id, "fadeIn", start, .12);
    effect(x, id, "fadeOut", end - .12, .12);
  }

  /** Uses engine-native effects, so scrubbing and production sample the same animation. */
  private static void effect(
      StringBuilder x, String id, String name, double start, double duration) {
    x.append(
        String.format(
            Locale.ROOT,
            "<effect target=\"%s\" name=\"%s\" start=\"%.4f\" duration=\"%.4f\"/>%n",
            id,
            name,
            start,
            duration));
  }

  /** Escapes both attribute and element content before inserting it into the screenplay. */
  private static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
