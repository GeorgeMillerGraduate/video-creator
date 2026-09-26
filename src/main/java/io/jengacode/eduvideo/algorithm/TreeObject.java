/*
 * Copyright (c) 2026 George Miller.
 * All rights reserved.
 *
 * Part of the Jenga-Code EduVideo project.
 */
package io.jengacode.eduvideo.algorithm;

import java.util.*;

/**
 * Graph container requiring one connected, acyclic undirected topology.
 *
 * <p>Checks node/edge membership, n-1 edges and absence of cycles using union-find roots. Together
 * these enforce one connected undirected tree; rendering still uses ordinary graph children.
 */
public final class TreeObject extends GraphObject {
  /**
   * Creates a configured TreeObject instance.
   *
   * @param id nonblank identity, unique within the containing scene
   */
  public TreeObject(String id) {
    super(id);
  }

  /**
   * Checks connected acyclic tree topology using node count and union-find roots.
   *
   * @throws IllegalArgumentException if an input violates the constraints described above
   */
  public void validateTree() {
    var nodes = new HashSet<String>();
    var edges = new ArrayList<EdgeObject>();
    for (var child : children()) {
      if (child instanceof NodeObject) nodes.add(child.id());
      else if (child instanceof EdgeObject e) edges.add(e);
      else throw new IllegalArgumentException("Trees contain only nodes and edges");
    }
    if (nodes.isEmpty() || edges.size() != nodes.size() - 1)
      throw new IllegalArgumentException("Tree requires n-1 edges");
    Map<String, String> parent = new HashMap<>();
    for (String n : nodes) parent.put(n, n);
    for (var e : edges) {
      if (!nodes.contains(e.from()) || !nodes.contains(e.to()))
        throw new IllegalArgumentException("Unknown tree endpoint");
      String a = root(parent, e.from()), b = root(parent, e.to());
      if (a.equals(b)) throw new IllegalArgumentException("Tree contains a cycle");
      parent.put(a, b);
    }
  }

  /**
   * Finds the representative of a union-find component.
   *
   * @param map lookup map updated during traversal
   * @param n node whose union-find representative is requested
   * @return finds the representative of a union-find component
   */
  private static String root(Map<String, String> map, String n) {
    while (!map.get(n).equals(n)) n = map.get(n);
    return n;
  }
}
