package pl.ostrzyciel.superfast_jellyfish

import org.apache.jena.graph.Triple
import org.apache.jena.sparql.core.Quad

package object convert:
  type TripleOrQuad = Quad | Triple

  final class RDFProtobufDeserializationError(msg: String) extends Error(msg)

  final class RDFProtobufSerializationError(msg: String) extends Error(msg)
