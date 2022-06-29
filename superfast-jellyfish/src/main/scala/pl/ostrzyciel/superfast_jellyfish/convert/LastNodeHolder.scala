package pl.ostrzyciel.superfast_jellyfish.convert

import org.apache.jena.graph.Node

/**
 * Tiny mutable holder for the last node that occurred as S, P, O, or G.
 */
private[convert] class LastNodeHolder:
  // This null is ugly... but it reduces the heap pressure significantly as compared with Option[Node]
  var node: Node = null
