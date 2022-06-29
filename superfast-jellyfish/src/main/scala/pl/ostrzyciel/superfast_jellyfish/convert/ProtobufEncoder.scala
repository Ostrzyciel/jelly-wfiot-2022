package pl.ostrzyciel.superfast_jellyfish.convert

import org.apache.jena.JenaRuntime
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.datatypes.xsd.impl.RDFLangString
import org.apache.jena.graph.*
import org.apache.jena.sparql.core.Quad
import pl.ostrzyciel.superfast_jellyfish.proto.*

import java.math.BigInteger
import scala.collection.mutable.ListBuffer

/**
 * Stateful encoder of a protobuf RDF stream.
 * @param options options for this stream
 */
class ProtobufEncoder(val options: RDFStreamOptions):
  private val extraRowsBuffer = new ListBuffer[RDF_StreamRow]()
  private val iriEncoder = new NameEncoder(options, extraRowsBuffer)
  private var emittedCompressionOptions = false

  private val lastSubject: LastNodeHolder = new LastNodeHolder()
  private val lastPredicate: LastNodeHolder = new LastNodeHolder()
  private val lastObject: LastNodeHolder = new LastNodeHolder()
  private val lastGraph: LastNodeHolder = new LastNodeHolder()

  private val termRepeat = Some(RDF_Term(RDF_Term.Term.Repeat(RDF_REPEAT())))

  /**
   * Turn an RDF node into a protobuf value
   * @param node node
   * @return
   */
  private def nodeToProtobuf(node: Node): Option[RDF_Term] = node match
    case _: Node_URI =>
      val iri = iriEncoder.encodeIri(node.getURI)
      Some(RDF_Term(RDF_Term.Term.Iri(iri)))

    case _: Node_Blank =>
      Some(RDF_Term(RDF_Term.Term.Bnode(RDF_BNode(node.getBlankNodeLabel))))

    case _: Node_Literal =>
      val lex = node.getLiteralLexicalForm
      var dt = node.getLiteralDatatypeURI
      val lang = node.getLiteralLanguage match
        case l if l.isEmpty => null
        case l => l

      // Jena: "General encoding." (??)
      if (JenaRuntime.isRDF11)
        if (node.getLiteralDatatype == XSDDatatype.XSDstring
          || node.getLiteralDatatype == RDFLangString.rdfLangString)
          dt = null

      Some(RDF_Term(RDF_Term.Term.Literal(
        RDF_Literal(
          lex,
          (dt, lang) match
            case (null, null) => RDF_Literal.LiteralKind.Simple(true)
            case (_, null) => iriEncoder.encodeDatatype(dt)
            case _ => RDF_Literal.LiteralKind.Langtag(lang)
        )
      )))

    // RDF-star node
    case _: Node_Triple =>
      Some(RDF_Term(RDF_Term.Term.TripleTerm(tripleToProtobuf(node.getTriple))))

    case _ => None

  private def nodeToProtobufWrapped(node: Node, lastNodeHolder: LastNodeHolder): Option[RDF_Term] =
    if options.useRepeat then
      lastNodeHolder.node match
        case oldNode if node == oldNode => termRepeat
        case _ =>
          lastNodeHolder.node = node
          nodeToProtobuf(node)
    else
      nodeToProtobuf(node)

  private def tripleToProtobuf(triple: Triple): RDF_Triple =
    RDF_Triple(
      s = nodeToProtobufWrapped(triple.getSubject, lastSubject),
      p = nodeToProtobufWrapped(triple.getPredicate, lastPredicate),
      o = nodeToProtobufWrapped(triple.getObject, lastObject),
    )

  private def quadToProtobuf(quad: Quad): RDF_Quad =
    RDF_Quad(
      s = nodeToProtobufWrapped(quad.getSubject, lastSubject),
      p = nodeToProtobufWrapped(quad.getPredicate, lastPredicate),
      o = nodeToProtobufWrapped(quad.getObject, lastObject),
      g = nodeToProtobufWrapped(quad.getGraph, lastGraph),
    )

  /**
   * Convert an RDF statement into a list of stream rows that represent it.
   * @param elem Jena triple or quad
   * @return iterable of stream rows
   */
  def toProtobufRows(elem: TripleOrQuad): Iterable[RDF_StreamRow] =
    extraRowsBuffer.clear()
    if !emittedCompressionOptions then
      emittedCompressionOptions = true
      extraRowsBuffer.append(
        RDF_StreamRow(RDF_StreamRow.Row.Options(options.toRDF))
      )

    val mainRow = elem match
      case triple: Triple => RDF_StreamRow(RDF_StreamRow.Row.Triple(
        tripleToProtobuf(triple)
      ))
      case quad: Quad => RDF_StreamRow(RDF_StreamRow.Row.Quad(
        quadToProtobuf(quad)
      ))

    extraRowsBuffer.append(mainRow)
