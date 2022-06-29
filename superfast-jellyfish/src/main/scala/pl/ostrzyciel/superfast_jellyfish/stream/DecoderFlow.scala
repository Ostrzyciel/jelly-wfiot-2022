package pl.ostrzyciel.superfast_jellyfish.stream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import pl.ostrzyciel.superfast_jellyfish.convert.*
import pl.ostrzyciel.superfast_jellyfish.proto.{RDF_StreamFrame, RDF_StreamRow}

import scala.collection.immutable

/**
 * Akka Streams flows for decoding protobuf into RDF statements.
 */
object DecoderFlow:
  def toFlat: Flow[RDF_StreamFrame, TripleOrQuad, NotUsed] =
    val decoder = new ProtobufDecoder()
    Flow[RDF_StreamFrame]
      .mapConcat(frame => frame.row)
      .mapConcat(row => decoder.ingestRow(row))

  def toGrouped: Flow[RDF_StreamFrame, IterableOnce[TripleOrQuad], NotUsed] =
    val decoder = new ProtobufDecoder()
    Flow[RDF_StreamFrame]
      .map(frame => {
        frame.row.flatMap(decoder.ingestRow)
      })
