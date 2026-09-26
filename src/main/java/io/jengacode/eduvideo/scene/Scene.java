/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.scene;

import io.jengacode.eduvideo.util.Checks;
import java.awt.Color;
import java.util.*;

/**
 * A scene with its own clock, object namespace, and incoming transition.
 *
 * <p>Owns a root group, camera, optional theme and backdrop, and transition configuration.
 * Validation checks unique IDs, event bounds, plot parents, graph endpoints and tree topology
 * before rendering.
 */
public final class Scene {
  /**
   * Describes the incoming transition during the first transitionDuration seconds of a scene. The
   * prior scene is sampled at its last frame; scene durations are not shortened by transitions.
   */
  public enum Transition {
    /** Switch immediately to the next scene. */
    CUT,
    /** Fade through the project background colour. */
    FADE,
    /** Blend the preceding final frame with the incoming scene. */
    CROSSFADE,
    /** Translate the old frame left and bring the new frame in from the right. */
    SLIDE
  }

  private final String id;

  private final double duration;

  private final GroupObject root = new GroupObject("__root");

  private final Camera camera = new Camera();

  private Color background;

  private io.jengacode.eduvideo.video.Theme theme;

  private io.jengacode.eduvideo.render.BackgroundRenderer backdrop;

  /**
   * Configures theme for this component.
   *
   * @param theme active semantic palette
   * @return this instance for fluent configuration
   */
  public Scene theme(io.jengacode.eduvideo.video.Theme theme) {
    this.theme = java.util.Objects.requireNonNull(theme);
    return this;
  }

  /**
   * Returns theme for this configured component.
   *
   * @return theme for this configured component
   */
  public io.jengacode.eduvideo.video.Theme theme() {
    return theme;
  }

  /**
   * Configures backdrop for this component.
   *
   * @param backdrop backdrop used by this operation
   * @return this instance for fluent configuration
   */
  public Scene backdrop(io.jengacode.eduvideo.render.BackgroundRenderer backdrop) {
    this.backdrop = backdrop;
    return this;
  }

  /**
   * Returns backdrop for this configured component.
   *
   * @return backdrop for this configured component
   */
  public io.jengacode.eduvideo.render.BackgroundRenderer backdrop() {
    return backdrop;
  }

  private Transition transition = Transition.CUT;

  private double transitionDuration;

  /**
   * Creates a configured Scene instance.
   *
   * @param id nonblank identity, unique within the containing scene
   * @param duration positive interval length in seconds
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public Scene(String id, double duration) {
    this.id = Objects.requireNonNull(id);
    this.duration = Checks.positive(duration, "scene duration");
  }

  /**
   * Returns id for this configured component.
   *
   * @return id for this configured component
   */
  public String id() {
    return id;
  }

  /**
   * Returns duration for this configured component.
   *
   * @return time or duration in seconds
   */
  public double duration() {
    return duration;
  }

  /**
   * Returns root for this configured component.
   *
   * @return root for this configured component
   */
  public GroupObject root() {
    return root;
  }

  /**
   * Returns camera for this configured component.
   *
   * @return camera for this configured component
   */
  public Camera camera() {
    return camera;
  }

  /**
   * Attaches a top-level visual object to this scene root.
   *
   * @param object visual object to attach or position
   * @return this instance for fluent configuration
   */
  public Scene add(SceneObject object) {
    root.add(object);
    return this;
  }

  /**
   * Returns background for this configured component.
   *
   * @return background for this configured component
   */
  public Color background() {
    return background;
  }

  /**
   * Configures background for this component.
   *
   * @param c display colour
   * @return this instance for fluent configuration
   */
  public Scene background(Color c) {
    background = c;
    return this;
  }

  /**
   * Returns or configures the incoming transition; non-cut duration must fit the scene.
   *
   * @return or configures the incoming transition; non-cut duration must fit the scene
   */
  public Transition transition() {
    return transition;
  }

  /**
   * Returns transition duration for this configured component.
   *
   * @return time or duration in seconds
   */
  public double transitionDuration() {
    return transitionDuration;
  }

  /**
   * Returns or configures the incoming transition; non-cut duration must fit the scene.
   *
   * @param t incoming scene transition mode
   * @param d nonnegative transition duration; positive for non-cut transitions
   * @return this instance for fluent configuration
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public Scene transition(Transition t, double d) {
    Checks.nonnegative(d, "transition duration");
    if (d > duration || (t != Transition.CUT && d == 0))
      throw new IllegalArgumentException("Transition must fit scene and have positive duration");
    transition = t;
    transitionDuration = t == Transition.CUT ? 0 : d;
    return this;
  }

  /**
   * Builds an immutable scene-wide lookup including nested objects and the camera.
   *
   * @return unmodifiable snapshot of the configured contents
   */
  public Map<String, SceneObject> objects() {
    Map<String, SceneObject> map = new LinkedHashMap<>();
    map.put("camera", camera);
    collect(root, map);
    return Collections.unmodifiableMap(map);
  }

  /**
   * Recursively collects object identities while rejecting duplicate IDs.
   *
   * @param group group whose descendants are traversed
   * @param map lookup map updated during traversal
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  private void collect(GroupObject group, Map<String, SceneObject> map) {
    for (var child : group.children()) {
      if (map.putIfAbsent(child.id(), child) != null)
        throw new IllegalArgumentException("Duplicate object id: " + child.id());
      if (child instanceof GroupObject nested) collect(nested, map);
    }
  }

  /**
   * Resolves an object ID or rejects an unknown target.
   *
   * @param id nonblank identity, unique within the containing scene
   * @return resolves an object ID or rejects an unknown target
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public SceneObject find(String id) {
    var o = objects().get(id);
    if (o == null) throw new IllegalArgumentException("Unknown object '" + id + "'");
    return o;
  }

  /**
   * Checks IDs, event duration bounds, plot placement, edge endpoints and tree topology.
   *
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public void validate() {
    var objects = objects();
    for (var o : objects.values()) {
      double end = Math.max(o.timeline().end(), o.focusEnd());
      if (o instanceof io.jengacode.eduvideo.animation.ValueTransfer a)
        end = Math.max(end, a.end());
      if (o instanceof io.jengacode.eduvideo.math.TokenEquation a) end = Math.max(end, a.end());
      if (o instanceof io.jengacode.eduvideo.math.MatrixProductStep a) end = Math.max(end, a.end());
      if (o instanceof io.jengacode.eduvideo.math.CalculusObject
          && !(o.parent() instanceof io.jengacode.eduvideo.math.CoordinatePlane))
        throw new IllegalArgumentException("Calculus needs axes parent");
      if (o instanceof io.jengacode.eduvideo.math.MatrixObject a) end = Math.max(end, a.eventEnd());
      if (o instanceof io.jengacode.eduvideo.algorithm.ArrayObject a)
        end = Math.max(end, a.eventEnd());
      if (o instanceof io.jengacode.eduvideo.algorithm.CodeBlockObject a)
        end = Math.max(end, a.eventEnd());
      if (o instanceof io.jengacode.eduvideo.algorithm.VariableObject a)
        end = Math.max(end, a.eventEnd());
      if (end > duration + 1e-9)
        throw new IllegalArgumentException("Event exceeds scene duration: " + o.id());
      if ((o instanceof io.jengacode.eduvideo.math.PlotPoint
              || o instanceof io.jengacode.eduvideo.math.AreaPlot)
          && !(o.parent() instanceof io.jengacode.eduvideo.math.CoordinatePlane))
        throw new IllegalArgumentException("Plot annotation requires axes parent: " + o.id());
      if (o instanceof io.jengacode.eduvideo.math.FunctionPlot
          && !(o.parent() instanceof io.jengacode.eduvideo.math.CoordinatePlane))
        throw new IllegalArgumentException("Function must be a direct child of axes: " + o.id());
      if (o instanceof io.jengacode.eduvideo.algorithm.EdgeObject edge) {
        var a = objects.get(edge.from());
        var b = objects.get(edge.to());
        if (!(a instanceof io.jengacode.eduvideo.algorithm.NodeObject)
            || !(b instanceof io.jengacode.eduvideo.algorithm.NodeObject)
            || a.parent() != edge.parent()
            || b.parent() != edge.parent()
            || a == b)
          throw new IllegalArgumentException(
              "Edge requires distinct node endpoints in the same parent: " + o.id());
      }
      if (o instanceof io.jengacode.eduvideo.algorithm.TreeObject tree) tree.validateTree();
    }
  }
}
