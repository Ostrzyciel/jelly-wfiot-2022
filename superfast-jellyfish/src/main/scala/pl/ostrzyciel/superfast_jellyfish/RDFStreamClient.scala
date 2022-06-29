package pl.ostrzyciel.superfast_jellyfish

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import com.typesafe.scalalogging.LazyLogging
import pl.ostrzyciel.superfast_jellyfish.stream.*
import pl.ostrzyciel.superfast_jellyfish.proto.{RDFStreamServiceClient, RDF_StreamSubscribe}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * Simple RDF stream client implementation.
 * It connects to the server specified in config and requests an RDF stream at a given topic.
 *
 * Arguments: [topic (optional)]
 */
object RDFStreamClient extends LazyLogging:

  def main(args: Array[String]): Unit =
    implicit val sys: ActorSystem[_] = ActorSystem(Behaviors.empty, "RDFClient")
    implicit val ec: ExecutionContext = sys.executionContext

    val client = RDFStreamServiceClient(GrpcClientSettings.fromConfig("jelly-rdf-client"))

    val topic = if args.length > 0 then args(0) else ""
    request(topic)

    def request(topic: String): Unit =
      logger.info(s"Performing request: $topic")
      val responseStream = client.streamRDF(RDF_StreamSubscribe(topic))
      val done = responseStream
        .via(DecoderFlow.toFlat)
        .runForeach(tq => {})
      done.onComplete {
        case Success(_) =>
          logger.info("Streaming done")
        case Failure(e) =>
          logger.error(s"Error: $e")
      }
