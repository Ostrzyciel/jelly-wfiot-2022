package pl.ostrzyciel.superfast_jellyfish.stream

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.Config
import pl.ostrzyciel.superfast_jellyfish.convert.*
import pl.ostrzyciel.superfast_jellyfish.proto.{RDF_StreamFrame, RDF_StreamRow}

import java.io.OutputStream
import scala.collection.immutable

/**
 * Akka Streams flows for encoding RDF statements to protobuf.
 */
object EncoderFlow:
  object Options:
    def apply(config: Config): Options =
      Options(
        config.getInt("jelly.target-message-size"),
        config.getBoolean("jelly.async-encode"),
      )

  /**
   * @param targetMessageSize after the message gets bigger than the target, it gets sent.
   * @param asyncEncoding whether to make this flow asynchronous
   */
  case class Options(targetMessageSize: Int, asyncEncoding: Boolean):
    def withAsyncEncoding(v: Boolean) = Options(targetMessageSize, v)

  def fromFlat(opt: Options, encoder: ProtobufEncoder):
  Flow[TripleOrQuad, RDF_StreamFrame, NotUsed] =
    val flow = Flow[TripleOrQuad]
      .mapConcat(e => encoder.toProtobufRows(e))
      .groupedWeighted(opt.targetMessageSize)(row => row.serializedSize)
      .map(rows => RDF_StreamFrame(rows))

    if opt.asyncEncoding then flow.async else flow

  def fromFlat(opt: Options, streamOpt: RDFStreamOptions):
  Flow[TripleOrQuad, RDF_StreamFrame, NotUsed] =
    fromFlat(opt, ProtobufEncoder(streamOpt))

  def fromGrouped(opt: Options, encoder: ProtobufEncoder):
  Flow[IterableOnce[TripleOrQuad], RDF_StreamFrame, NotUsed] =
    val flow = Flow[IterableOnce[TripleOrQuad]]
      .flatMapConcat { elems =>
        Source.fromIterator(() => elems.iterator)
          .via(fromFlat(opt.withAsyncEncoding(false), encoder))
      }
    if opt.asyncEncoding then flow.async else flow

  def fromGrouped(opt: Options, streamOpt: RDFStreamOptions):
  Flow[IterableOnce[TripleOrQuad], RDF_StreamFrame, NotUsed] =
    fromGrouped(opt, ProtobufEncoder(streamOpt))
