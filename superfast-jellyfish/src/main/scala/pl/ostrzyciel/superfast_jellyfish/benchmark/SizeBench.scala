package pl.ostrzyciel.superfast_jellyfish.benchmark

import akka.{Done, NotUsed}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.ClosedShape
import akka.stream.scaladsl.*
import com.typesafe.config.ConfigFactory
import org.apache.jena.graph.Triple
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFWriter}
import org.apache.jena.riot.system.AsyncParser
import pl.ostrzyciel.superfast_jellyfish.convert.RDFStreamOptions
import pl.ostrzyciel.superfast_jellyfish.stream.EncoderFlow

import java.io.OutputStream
import java.util.zip.GZIPOutputStream
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

object SizeBench:
  val conf = ConfigFactory.load()
  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "RawSerDesBench", conf)
  implicit val ec: ExecutionContext = system.executionContext

  // Arguments: [source file] [comma-separated keys of tests (optional)]
  def main(args: Array[String]): Unit =
    val fOpts = EncoderFlow.Options(conf)
    println(fOpts)

    def makeStream = new DebugOutputStream(args.contains("debug"))

    val sOptList = Seq(
      ("full", RDFStreamOptions()),
      ("noprefix", RDFStreamOptions(prefixTableSize = 0)),
      ("noprefix-sm", RDFStreamOptions(prefixTableSize = 0, nameTableSize = 256)),
      ("norepeat", RDFStreamOptions(useRepeat = false)),
    )

    // Add Jelly-based serializations
    val sinkMap: mutable.Map[String, (OutputStream => Sink[IterableOnce[Triple], Future[Done]], DebugOutputStream)] =
      mutable.Map.from(
        for (trialKey, sOpts) <- sOptList yield (
          "jelly-" + trialKey,
          (
            (stream: OutputStream) =>
              EncoderFlow.fromGrouped(fOpts, sOpts)
                .toMat(Sink.foreach(r => r.writeTo(stream)))(Keep.right),
            makeStream
          ),
        )
      )

    // Add Jena-based serializations
    for (langName, lang) <- jenaLangs do
      sinkMap(langName) =
        (
          (stream: OutputStream) =>
            Flow[IterableOnce[Triple]]
              .map(ts => {
                val jModel = ModelFactory.createDefaultModel()
                ts.iterator.foreach(t => jModel.getGraph.add(t))
                jModel
              })
              .toMat(Sink.foreach(
                m => RDFWriter.create()
                  .lang(lang)
                  .source(m.getGraph)
                  .output(stream)
              ))(Keep.right),
          makeStream
        )

    // Add gzip variants
    sinkMap ++= mutable.Map.from(
      for (key, sink) <- sinkMap yield
        (key + "-gzip", (
          (stream: OutputStream) => {
            val gzipStream = new GZIPOutputStream(stream)
            sink._1(gzipStream)
          },
          makeStream,
        ))
      )

    if args.length > 1 then
      val keysToKeep = args(1).split(',')
      sinkMap.filterInPlace((k, _) => keysToKeep.contains(k))

    val sinks = sinkMap.values.map(v => v._1(v._2)).toSeq

    val g: RunnableGraph[Seq[Future[Done]]] = RunnableGraph.fromGraph(GraphDSL.create(sinks) {
      implicit b => sinkList =>
        import GraphDSL.Implicits.*

        val source = Source
          .fromIterator(() => AsyncParser.asyncParseTriples(args(0)).asScala)
          .grouped(1000)
          .async

        val broadcast = b.add(Broadcast[Seq[Triple]](sinkList.size))
        source ~> broadcast.in
        sinkList.zipWithIndex.foreach(
          (sink, i) => broadcast.out(i) ~> Flow[Seq[Triple]].async ~> sink
        )

        ClosedShape
    })

    Await.result(Future.sequence(g.run()), Duration.Inf)

    for (k, v) <- sinkMap do
      println(s"$k: ${v._2.getByteCount}")
      v._2.print()

    saveRunInfo("size", conf, Map(
      "sizes" -> sinkMap.map((k, v) => (k, v._2.getByteCount)),
      "file" -> args(0),
    ))

    sys.exit()
