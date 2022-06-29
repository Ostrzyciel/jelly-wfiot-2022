package pl.ostrzyciel.superfast_jellyfish.benchmark

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.stream.scaladsl.{Keep, Sink}
import com.typesafe.config.ConfigFactory
import pl.ostrzyciel.superfast_jellyfish.{RDFStreamServer, RDFStreamServiceFromFile}
import pl.ostrzyciel.superfast_jellyfish.proto.{RDFStreamServiceClient, RDF_StreamSubscribe}
import pl.ostrzyciel.superfast_jellyfish.stream.*

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

object FullStreamBench:
  val config = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.load())

  val t0client = ArrayBuffer[Long]()
  val t0server = ArrayBuffer[Long]()
  val t1client = ArrayBuffer[Long]()

  var modelSize: Long = 0

  case class StreamResult(t0client: Long, t0server: Long, t1client: Long):
    def time = t1client - t0server

  // Arguments: [source file for streaming]
  def main(args: Array[String]): Unit =
    implicit val serverSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "StreamServer", config)

    val service = new RDFStreamServiceFromFile(args(0), config) {
      override def streamRDF(in: RDF_StreamSubscribe) =
        t0server.append(System.nanoTime())
        super.streamRDF(in)
    }
    new RDFStreamServer(service)(serverSystem).run()
    runClient(args(0))


  def runClient(filename: String): Unit =
    implicit val clientSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "StreamClient", config)
    implicit val ec: ExecutionContext = clientSystem.executionContext
    val client = RDFStreamServiceClient(GrpcClientSettings.fromConfig("jelly-rdf-client"))

    for i <- 1 to REPEATS do
      println(s"Try: $i")
      Await.result(request(client), Duration.Inf)

    val times = t0client.lazyZip(t0server).lazyZip(t1client)
      .map((t0c, t0s, t1c) => StreamResult(t0c, t0s, t1c))
      .toSeq

    printSpeed(modelSize, times.map(_.time))
    saveRunInfo("full_stream_proto", config, Map(
      "times" -> times,
      "size" -> modelSize,
      "useGzip" -> config.getBoolean("jelly.server.enable-gzip"),
      "file" -> filename,
      "port" -> config.getInt("jelly.server.port"),
    ))
    sys.exit()

  def request(client: RDFStreamServiceClient)(implicit ec: ExecutionContext, system: ActorSystem[_]): Future[Unit] =
    println("Sleeping 5 seconds...")

    val waitFuture = Future {Thread.sleep(5000)}
    waitFuture flatMap { _ =>
      t0client.append(System.nanoTime())
      val responseStream = client.streamRDF(RDF_StreamSubscribe("dummy topic"))
      responseStream
        .via(DecoderFlow.toFlat)
        .runWith(Sink.fold(0.toLong)((c, _) => c + 1))
    } map { size =>
      t1client.append(System.nanoTime())
      modelSize = size

      println(s"Streaming done, triples: $modelSize")
    }

