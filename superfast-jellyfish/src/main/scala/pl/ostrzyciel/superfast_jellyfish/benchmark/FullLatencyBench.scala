package pl.ostrzyciel.superfast_jellyfish.benchmark

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.jena.graph.Triple
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.system.AsyncParser
import pl.ostrzyciel.superfast_jellyfish.RDFStreamServer
import pl.ostrzyciel.superfast_jellyfish.benchmark.FullStreamBench.{config, t0server}
import pl.ostrzyciel.superfast_jellyfish.convert.RDFStreamOptions
import pl.ostrzyciel.superfast_jellyfish.proto.{RDFStreamService, RDFStreamServiceClient, RDF_StreamFrame, RDF_StreamSubscribe}
import pl.ostrzyciel.superfast_jellyfish.stream.{DecoderFlow, EncoderFlow}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

object FullLatencyBench:
  val config = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.load())

  val serverSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "StreamServer", config)
  val clientSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "StreamClient", config)

  class LatencyStreamService(sourceData: Seq[Triple], interval: FiniteDuration, nMessages: Int,
                             messageSize: Int, timestamps: ArrayBuffer[Long])(implicit mat: Materializer)
    extends RDFStreamService:
    import mat.executionContext

    val sourceMessages = sourceData
      .grouped(messageSize)
      .toSeq

    override def streamRDF(in: RDF_StreamSubscribe): Source[RDF_StreamFrame, NotUsed] =
      Source.tick(2.seconds, interval, 0)
        .take(nMessages)
        .zip(Source(sourceMessages))
        .map((_, triples) => {
          timestamps.append(System.nanoTime)
          triples
        })
        .via(EncoderFlow.fromGrouped(
          EncoderFlow.Options(config),
          RDFStreamOptions(config),
        ))
        .mapMaterializedValue(_ => NotUsed)

  // Arguments: [source file for streaming]
  def main(args: Array[String]): Unit =
    println("Loading data...")
    val sourceData = AsyncParser.asyncParseTriples(args(0)).asScala
      .take(LatencyUtil.mSizes.max * LatencyUtil.messageCount)
      .toSeq

    val resultMap = scala.collection.mutable.Map[String, Any]()

    LatencyUtil.run(
      (i, ms) => runOne(sourceData, i, ms),
      resultMap,
    )
    saveRunInfo("grpc_latency", config, Map(
      "times" -> resultMap,
      "useGzip" -> config.getBoolean("jelly.server.enable-gzip"),
      "file" -> args(0),
      "port" -> config.getInt("jelly.server.port"),
    ))
    sys.exit()


  def runOne(sourceData: Seq[Triple], interval: FiniteDuration, messageSize: Int) =
    val tsServer = new ArrayBuffer[Long]()
    val tsClient = new ArrayBuffer[Long]()

    val server = {
      implicit val sys: ActorSystem[_] = serverSystem
      val service = new LatencyStreamService(sourceData, interval, LatencyUtil.messageCount, messageSize, tsServer)
      val s = new RDFStreamServer(service)(serverSystem)
      s.run()
      s
    }

    val clientFut = {
      implicit val sys: ActorSystem[_] = clientSystem
      val client = RDFStreamServiceClient(GrpcClientSettings.fromConfig("jelly-rdf-client"))
      client.streamRDF(RDF_StreamSubscribe("dummy topic"))
        .via(DecoderFlow.toGrouped)
        .map(_.iterator.toSeq)
        .runForeach(_ => tsClient.append(System.nanoTime))
    }

    Await.result(clientFut, Duration.Inf)
    Await.result(server.terminate(), Duration.Inf)

    if tsServer.size != tsClient.size then
      throw new Error("Message count mismatch")

    tsServer.zip(tsClient).toSeq
