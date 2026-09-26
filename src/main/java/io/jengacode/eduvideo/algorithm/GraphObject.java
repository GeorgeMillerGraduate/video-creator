/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.algorithm;

import io.jengacode.eduvideo.scene.GroupObject;

/**
 * Graph-theory container; coordinates are explicitly supplied for reproducible layouts.
 *
 * <p>Provides a normal transformed group for nodes and edges. Layout is explicit; Scene validation
 * resolves endpoint IDs and checks they share the edge parent.
 */
public class GraphObject extends GroupObject {
  /**
   * Creates a configured GraphObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   */
  public GraphObject(String id) {
    super(id);
  }
}
