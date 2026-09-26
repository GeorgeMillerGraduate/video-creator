/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.app;

import io.jengacode.eduvideo.video.VideoProject;
import java.nio.file.Path;
import javafx.beans.property.*;

/**
 * Observable document state. The XML text is authoritative; the parsed model is a validated
 * snapshot.
 */
public final class ProjectState {
  public final ObjectProperty<Path> path = new SimpleObjectProperty<>();
  public final ObjectProperty<VideoProject> project = new SimpleObjectProperty<>();
  public final BooleanProperty dirty = new SimpleBooleanProperty(false);
  public final StringProperty status = new SimpleStringProperty("Ready");
  public final DoubleProperty time = new SimpleDoubleProperty(0);
}
