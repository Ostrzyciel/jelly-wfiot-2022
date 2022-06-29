package pl.ostrzyciel.superfast_jellyfish.convert

import org.apache.jena.JenaRuntime
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.datatypes.xsd.impl.RDFLangString
import org.apache.jena.graph.*
import org.apache.jena.sparql.core.Quad
import pl.ostrzyciel.superfast_jellyfish.proto.*

import scala.annotation.tailrec

/**
 * Stateful decoder of a protobuf RDF stream.
 */
class ProtobufDecoder:
  var streamOpt: Option[RDFStreamOptions] = None
  lazy val nameDecoder = new NameDecoder(streamOpt getOrElse RDFStreamOptions())
  lazy val dtLookup = new DecoderLookup[RDFDatatype](streamOpt.map(o => o.datatypeTableSize) getOrElse 20)

  private val lastSubject: LastNodeHolder = new LastNodeHolder()
  private val lastPredicate: LastNodeHolder = new LastNodeHolder()
  private val lastObject: LastNodeHolder = new LastNodeHolder()
  private val lastGraph: LastNodeHolder = new LastNodeHolder()

  private def convertTerm(term: RDF_Term): Node = term.term match
    case RDF_Term.Term.Iri(iri) =>
      NodeFactory.createURI(nameDecoder.decode(iri))
    case RDF_Term.Term.Bnode(bnode) =>
      NodeFactory.createBlankNode(bnode.label)
    case RDF_Term.Term.Literal(literal) =>
      literal.literalKind match
        case RDF_Literal.LiteralKind.Simple(_) =>
          NodeFactory.createLiteral(literal.lex)
        case RDF_Literal.LiteralKind.Langtag(lang) =>
          NodeFactory.createLiteral(literal.lex, lang)
        case RDF_Literal.LiteralKind.Datatype(dt) =>
          NodeFactory.createLiteral(literal.lex, dtLookup.get(dt.dtId))
        case RDF_Literal.LiteralKind.Empty =>
          throw new RDFProtobufDeserializationError("Literal kind not set.")
    case RDF_Term.Term.TripleTerm(triple) =>
      NodeFactory.createTripleNode(convertTriple(triple))
    case _: RDF_Term.Term.Repeat =>
      throw new RDFProtobufDeserializationError("Use convertedTermWrapped.")
    case RDF_Term.Term.Empty =>
      throw new RDFProtobufDeserializationError("Term kind is not set.")

  private def convertTermWrapped(term: Option[RDF_Term], lastNodeHolder: LastNodeHolder): Node = term match
    case Some(t) => t.term match
      case _: RDF_Term.Term.Repeat =>
        lastNodeHolder.node match
          case null =>
            throw new RDFProtobufDeserializationError("RDF_REPEAT without previous term")
          case n => n
      case _ =>
        val node = convertTerm(t)
        lastNodeHolder.node = node
        node
    case None => throw new RDFProtobufDeserializationError("Term not set.")

  private def convertTriple(triple: RDF_Triple): Triple =
    Triple.create(
      convertTermWrapped(triple.s, lastSubject),
      convertTermWrapped(triple.p, lastPredicate),
      convertTermWrapped(triple.o, lastObject),
    )

  private def convertQuad(quad: RDF_Quad): Quad =
    Quad.create(
      convertTermWrapped(quad.g, lastGraph),
      convertTermWrapped(quad.s, lastSubject),
      convertTermWrapped(quad.p, lastPredicate),
      convertTermWrapped(quad.o, lastObject),
    )

  def ingestRow(row: RDF_StreamRow): Option[TripleOrQuad] = row.row match
    case RDF_StreamRow.Row.Options(opts) =>
      streamOpt = Some(RDFStreamOptions(opts))
      None
    case RDF_StreamRow.Row.Name(nameRow) =>
      nameDecoder.updateNames(nameRow)
      None
    case RDF_StreamRow.Row.Prefix(prefixRow) =>
      nameDecoder.updatePrefixes(prefixRow)
      None
    case RDF_StreamRow.Row.Datatype(dtRow) =>
      dtLookup.update(dtRow.id, NodeFactory.getType(dtRow.value))
      None
    case RDF_StreamRow.Row.Triple(triple) =>
      Some(convertTriple(triple))
    case RDF_StreamRow.Row.Quad(quad) =>
      Some(convertQuad(quad))
    case RDF_StreamRow.Row.Empty =>
      throw new RDFProtobufDeserializationError("Row is not set.")
