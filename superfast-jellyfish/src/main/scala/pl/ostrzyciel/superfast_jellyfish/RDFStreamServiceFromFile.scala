package pl.ostrzyciel.superfast_jellyfish

import scala.concurrent.Future
import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import com.google.protobuf.timestamp.Timestamp
import com.typesafe.config.Config
import org.apache.jena.graph.{NodeFactory, Node_URI, Triple}
import org.apache.jena.riot.RDFDataMgr
import pl.ostrzyciel.superfast_jellyfish.convert.*
import pl.ostrzyciel.superfast_jellyfish.stream.*
import pl.ostrzyciel.superfast_jellyfish.proto.{RDFStreamService, RDF_StreamFrame, RDF_StreamSubscribe}

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class RDFStreamServiceFromFile(sourcePath: String, config: Config)(implicit mat: Materializer)
  extends RDFStreamService:

  import mat.executionContext

  private val sourceModel = RDFDataMgr.loadModel(sourcePath)

  override def streamRDF(in: RDF_StreamSubscribe): Source[RDF_StreamFrame, NotUsed] =
    StreamConverters.fromJavaStream(() => sourceModel.getGraph.stream)
      .via(EncoderFlow.fromFlat(
        EncoderFlow.Options(config),
        RDFStreamOptions(config),
      ))
      .mapMaterializedValue(_ => NotUsed)
