/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.preview;

import io.jengacode.eduvideo.video.*;
import io.jengacode.eduvideo.xml.ProjectParser;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.swing.*;

/**
 * Swing shell around the same renderer; expensive work happens outside the EDT.
 *
 * <p>Owns Swing controls on the event dispatch thread and delegates parsing/rendering to workers.
 * Generation tokens discard stale results after a reload, and pending requests coalesce during
 * scrubbing.
 */
public final class PreviewApp {

  private final JFrame window = new JFrame("EduVideo preview");

  private final Canvas canvas = new Canvas();

  private final JSlider slider = new JSlider();

  private final JLabel status = new JLabel("Load an XML lesson");

  private final JButton play = new JButton("Play");

  private final JTextField jump = new JTextField("0", 5);

  private VideoRenderer renderer;

  private Path file;

  private long generation;

  private boolean busy;

  private boolean pending;

  private final Timer timer =
      new Timer(
          33,
          e -> {
            if (renderer != null) {
              int next =
                  Math.min(
                      slider.getMaximum(),
                      slider.getValue()
                          + Math.max(
                              1, (int) Math.round(renderer.project().settings().fps() / 30)));
              slider.setValue(next);
              if (next == slider.getMaximum()) stop();
            }
          });

  /** Prevents instantiation of this static utility. */
  private PreviewApp() {
    window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    window.addWindowListener(
        new java.awt.event.WindowAdapter() {
          /**
           * Stops the playback timer when the preview window closes.
           *
           * @param e source parser or window event
           */
          public void windowClosed(java.awt.event.WindowEvent e) {
            timer.stop();
          }
        });
    var controls = new JPanel();
    JButton open = new JButton("Open XML"),
        reload = new JButton("Reload"),
        go = new JButton("Jump (seconds)");
    controls.add(open);
    controls.add(reload);
    controls.add(play);
    controls.add(jump);
    controls.add(go);
    controls.add(status);
    var bottom = new JPanel(new BorderLayout());
    bottom.add(slider, BorderLayout.CENTER);
    bottom.add(controls, BorderLayout.SOUTH);
    window.add(canvas, BorderLayout.CENTER);
    window.add(bottom, BorderLayout.SOUTH);
    window.setSize(1100, 760);
    window.setLocationByPlatform(true);
    open.addActionListener(
        e -> {
          var chooser = new JFileChooser();
          if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION)
            load(chooser.getSelectedFile().toPath());
        });
    reload.addActionListener(
        e -> {
          if (file != null) load(file);
        });
    play.addActionListener(
        e -> {
          if (timer.isRunning()) stop();
          else if (renderer != null) {
            timer.start();
            play.setText("Pause");
          }
        });
    go.addActionListener(
        e -> {
          try {
            double seconds = Double.parseDouble(jump.getText());
            if (renderer != null && Double.isFinite(seconds))
              slider.setValue(
                  (int)
                      Math.max(
                          0,
                          Math.min(
                              slider.getMaximum(), seconds * renderer.project().settings().fps())));
          } catch (NumberFormatException ex) {
            status.setText("Enter a numeric time");
          }
        });
    slider.addChangeListener(e -> requestFrame());
    window.setVisible(true);
  }

  /** Stops preview playback and restores the play-button label. */
  private void stop() {
    timer.stop();
    play.setText("Play");
  }

  /**
   * Loads and validates a lesson in a background worker, discarding stale reload results.
   *
   * @param path input file path
   */
  private void load(Path path) {
    stop();
    long token = ++generation;
    status.setText("Loading…");
    new SwingWorker<VideoRenderer, Void>() {
      /**
       * Performs parsing or rendering away from the Swing event dispatch thread.
       *
       * @return performs parsing or rendering away from the Swing event dispatch thread
       * @throws Exception if parsing, rendering or output processing fails
       */
      protected VideoRenderer doInBackground() throws Exception {
        return new VideoRenderer(new ProjectParser().parse(path));
      }

      /** Publishes the completed worker result only when its generation is still current. */
      protected void done() {
        if (token != generation) return;
        try {
          renderer = get();
          file = path;
          slider.setMaximum(renderer.project().frameCount() - 1);
          slider.setValue(0);
          requestFrame();
        } catch (Exception ex) {
          status.setText("Load failed");
          JOptionPane.showMessageDialog(
              window,
              ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage(),
              "Invalid lesson",
              JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  /** Coalesces preview sampling requests while a render worker is busy. */
  private void requestFrame() {
    if (renderer == null) return;
    if (busy) {
      pending = true;
      return;
    }
    busy = true;
    pending = false;
    int frame = slider.getValue();
    long token = generation;
    VideoRenderer current = renderer;
    new SwingWorker<BufferedImage, Void>() {
      /**
       * Performs parsing or rendering away from the Swing event dispatch thread.
       *
       * @return newly rendered ARGB image
       */
      protected BufferedImage doInBackground() {
        return current.renderFrame(frame);
      }

      /** Publishes the completed worker result only when its generation is still current. */
      protected void done() {
        busy = false;
        try {
          if (token == generation) {
            canvas.image = get();
            canvas.repaint();
            status.setText(
                String.format(
                    java.util.Locale.ROOT,
                    "%.2f s · frame %d",
                    frame / current.project().settings().fps(),
                    frame));
          }
        } catch (Exception ex) {
          stop();
          status.setText("Render failed: " + ex.getMessage());
        }
        if (pending) requestFrame();
      }
    }.execute();
  }

  /**
   * Opens the desktop preview on the Swing event dispatch thread.
   *
   * @param initial optional initial lesson path, or null to open an empty preview
   */
  public static void open(Path initial) {
    if (GraphicsEnvironment.isHeadless())
      throw new IllegalStateException("Preview requires a desktop graphics environment");
    SwingUtilities.invokeLater(
        () -> {
          var app = new PreviewApp();
          if (initial != null) app.load(initial);
        });
  }

  /**
   * Displays the most recently completed frame with aspect ratio preserved. Repainting never
   * samples or changes the lesson timeline.
   */
  private static final class Canvas extends JPanel {

    private BufferedImage image;

    /** Creates a Canvas instance with its default configuration. */
    Canvas() {
      setBackground(new Color(7, 17, 31));
    }

    /**
     * Fits the latest preview frame to the panel while preserving aspect ratio.
     *
     * @param graphics caller graphics context, preserved where a child context is created
     */
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      if (image == null) return;
      double s =
          Math.min(
              (double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
      int w = (int) (image.getWidth() * s), h = (int) (image.getHeight() * s);
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        VideoRenderer.quality(g);
        g.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
      } finally {
        g.dispose();
      }
    }
  }
}
