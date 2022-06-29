package pl.ostrzyciel.superfast_jellyfish

import akka.{Done, NotUsed}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.ClosedShape
import akka.stream.scaladsl.*
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.jena.graph.Triple
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.RDFDataMgr
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.ostrzyciel.superfast_jellyfish.convert.*
import pl.ostrzyciel.superfast_jellyfish.stream.*
import pl.ostrzyciel.superfast_jellyfish.proto.RDF_StreamFrame

import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class InternalStreamingSpec extends AnyWordSpec, Matchers, ScalaFutures:
  val config: Config = ConfigFactory.load()
  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "test", config)
  implicit val executionContext: ExecutionContextExecutor = system.executionContext

  private def testTripleStream(sourceModel: Model, maxMessageSize: Int, opts: RDFStreamOptions) =
    s"streaming thing (mSize: $maxMessageSize, $opts)" should {
      s"stream" in {
        val source = Source[Iterator[TripleOrQuad]](Seq(sourceModel.getGraph.stream.iterator.asScala))

        val encoderFlow = EncoderFlow.fromGrouped(
          EncoderFlow.Options(maxMessageSize, false),
          opts
        )
        val flow = encoderFlow.async.via(DecoderFlow.toFlat)
        val resultModel = ModelFactory.createDefaultModel()

        val future = source.via(flow)
          .runForeach(qt => qt match
            case t: Triple => resultModel.getGraph.add(t)
            case _ =>
          )
        Await.result(future, 1_000.seconds)

        resultModel.size() should be (sourceModel.size())
        val resultSet = resultModel.getGraph.stream.iterator.asScala.toSet
        val sourceSet = sourceModel.getGraph.stream.iterator.asScala.toSet

        for (st <- sourceSet)
          assert(resultSet(st), s"result set does not contain: $st")

        println(s"$maxMessageSize, $opts")
      }
    }

  val messageSizes = Seq(
    100, 1000, 10_000, 100_000, 1_000_000,
  )
  val nameTableSizes = Seq(
    10, 100, 1000,
  )
  val prefixTableSizes = Seq(
    0, 10, 100,
  )

  val sourceModel = RDFDataMgr.loadModel(getClass.getResource("/mix.nt.gz").toString)

  for mSize <- messageSizes
      nameTableSize <- nameTableSizes
      prefixTableSize <- prefixTableSizes
  do
    testTripleStream(sourceModel, mSize, RDFStreamOptions(nameTableSize, prefixTableSize))
