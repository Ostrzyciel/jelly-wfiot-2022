package pl.ostrzyciel.superfast_jellyfish

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.ostrzyciel.superfast_jellyfish.proto.RDFStreamServiceHandler

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

/**
 * Simple RDF stream server implementation.
 * It serves the given RDF file as a stream to all subscribers, on all topics.
 *
 * Arguments: [source file path]
 */
object SimpleRDFStreamServer:

  def main(args: Array[String]): Unit =
    // important to enable HTTP/2 in ActorSystem's config
    val conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "RDFStreamServer", conf)
    new RDFStreamServer(new RDFStreamServiceFromFile(args(0), conf)).run()
