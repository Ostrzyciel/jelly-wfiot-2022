package pl.ostrzyciel.superfast_jellyfish

import akka.Done
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.ostrzyciel.superfast_jellyfish.proto.*

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

class RDFStreamServer(streamService: RDFStreamService)(implicit system: ActorSystem[_]) extends LazyLogging:
  implicit val ec: ExecutionContext = system.executionContext

  private var binding: Option[ServerBinding] = _

  def run(): Future[ServerBinding] =
    val config = ConfigFactory.load()
    val service: HttpRequest => Future[HttpResponse] =
      RDFStreamServiceHandler(streamService)

    val handler: HttpRequest => Future[HttpResponse] = if config.getBoolean("jelly.server.enable-gzip") then
      service
    else
      { request =>
        // 1000 kudos to this guy: https://github.com/akka/akka-grpc/issues/1265
        val withoutEncoding = request.withHeaders(request.headers.filterNot(_.lowercaseName == "grpc-accept-encoding"))
        service(withoutEncoding)
      }

    val bound: Future[ServerBinding] = Http()
      .newServerAt(
        config.getString("jelly.server.host"),
        config.getInt("jelly.server.port"),
      )
      .bind(handler)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))

    bound.onComplete {
      case Success(b) =>
        binding = Some(b)
        val address = b.localAddress
        logger.warn("gRPC server bound to {}:{}", address.getHostString, address.getPort)
      case Failure(ex) =>
        logger.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }

    bound

  def terminate(): Future[Done] = binding match
    case Some(b) =>
      b.terminate(2.seconds) map { _ => Done }
    case _ =>
      Future { Done }
