package pl.ostrzyciel.superfast_jellyfish.benchmark

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.system.AsyncParser
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFWriter}
import org.apache.jena.sys.JenaSystem
import pl.ostrzyciel.superfast_jellyfish.convert.{ProtobufDecoder, ProtobufEncoder, RDFStreamOptions}
import pl.ostrzyciel.superfast_jellyfish.proto.{RDF_StreamFrame, RDF_StreamRow}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext}
import scala.jdk.CollectionConverters.*

object RawSerDesBench:
  val conf = ConfigFactory.load()
  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "RawSerDesBench", conf)
  implicit val ec: ExecutionContext = system.executionContext

  val times = mutable.ArrayBuffer.empty[Long]
  var modelSize: Long = 0

  // [ser/des] [method] [source file path]
  def main(args: Array[String]): Unit =
    if args(0) == "ser" then
      mainSer(args)
    else if args(0) == "des" then
      mainDes(args)

    printSpeed(modelSize, times)
    saveRunInfo(s"raw_${args(0)}", conf, Map(
      "size" -> modelSize,
      "times" -> times,
      "lang" -> args(1),
      "file" -> args(2),
      "task" -> args(0),
    ))
    sys.exit()

  def getSourceModels(args: Array[String]): Seq[Model] =
    println("Loading the source file...")
    val models = AsyncParser.asyncParseTriples(args(2)).asScala
      .grouped(1000)
      .map(ts => {
        val model = ModelFactory.createDefaultModel()
        ts.foreach(model.getGraph.add)
        model
      })
      .toSeq
    modelSize = models.map(_.size()).sum
    models

  def mainSer(args: Array[String]): Unit =
    val sourceModels = getSourceModels(args)

    for i <- 1 to REPEATS do
      System.gc()
      println("Sleeping 3 seconds...")
      Thread.sleep(3000)
      println("Try: " + i)
      if args(1) == "protobuf" then
        val stream = OutputStream.nullOutputStream
        times += time {
          serProtobuf(sourceModels, frame => frame.writeTo(stream))
        }
      else
        times += time {
          for model <- sourceModels do
            serJena(model, jenaLangs(args(1)), OutputStream.nullOutputStream)
        }

  def mainDes(args: Array[String]): Unit =
    println("Serializing to memory...")

    if args(1) == "protobuf" then
      val serialized = {
        val serBuffer = ArrayBuffer[Array[Byte]]()
        serProtobuf(getSourceModels(args), frame => serBuffer.append(frame.toByteArray))
        serBuffer
      }

      for i <- 1 to REPEATS do
        System.gc()
        println("Sleeping 3 seconds...")
        Thread.sleep(3000)
        println("Try: " + i)
        times += time {
          desProtobuf(serialized)
        }

    else
      val serBuffer = ArrayBuffer[Array[Byte]]()
      for model <- getSourceModels(args) do
        val oStream = new ByteArrayOutputStream()
        serJena(model, jenaLangs(args(1)), oStream)
        serBuffer.append(oStream.toByteArray)

      for i <- 1 to REPEATS do
        System.gc()
        println("Sleeping 3 seconds...")
        Thread.sleep(3000)
        println("Try: " + i)
        times += time {
          for buffer <- serBuffer do
            desJena(new ByteArrayInputStream(buffer), jenaLangs(args(1)))
        }


  def serProtobuf(sourceModels: Seq[Model], closure: RDF_StreamFrame => Unit): Unit =
    val encoder = new ProtobufEncoder(
      RDFStreamOptions(conf),
    )
    sourceModels
      .map(m => {
        val rows = m.getGraph.stream().iterator.asScala
          .flatMap(triple => encoder.toProtobufRows(triple))
          .toSeq
        RDF_StreamFrame(rows)
      })
      .foreach(closure)

  def serJena(sourceModel: Model, lang: Lang, outputStream: OutputStream): Unit =
    RDFWriter.create()
      .lang(lang)
      .source(sourceModel.getGraph)
      .output(outputStream)

  def desProtobuf(input: Iterable[Array[Byte]]): Unit =
    val decoder = new ProtobufDecoder()
    input
      .map(RDF_StreamFrame.parseFrom)
      .map(stream => stream.row.map(decoder.ingestRow).foreach(_ => {}))
      .foreach(_ => {})

  def desJena(input: InputStream, lang: Lang): Unit =
    AsyncParser.asyncParseTriples(input, lang, "")
      .forEachRemaining(_ => {})
