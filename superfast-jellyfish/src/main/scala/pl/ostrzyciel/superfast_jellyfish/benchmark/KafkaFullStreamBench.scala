package pl.ostrzyciel.superfast_jellyfish.benchmark

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFWriter}
import org.apache.jena.riot.system.AsyncParser
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}
import pl.ostrzyciel.superfast_jellyfish.convert.*
import pl.ostrzyciel.superfast_jellyfish.proto.RDF_StreamFrame

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters.*

object KafkaFullStreamBench:
  case class KafkaResult(t0cons: Long, t0prod: Long, t1cons: Long, t1prod: Long, triples: Long, bytes: Long):
    def time = t1cons - t0prod

  val config = ConfigFactory.load()

  val prodSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "Producer", config)
  val consSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "Consumer", config)
  val prodSettings = ProducerSettings(prodSystem, new StringSerializer, new ByteArraySerializer)
  val consSettings = ConsumerSettings(consSystem, new StringDeserializer, new ByteArrayDeserializer)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

  var sourceModels = Seq[Model]()
  var useGzip = false

  // Arguments: [serialization language] [source file] [gzip compr. on or off (0/1)]
  def main(args: Array[String]): Unit =
    implicit val ec: ExecutionContextExecutor = consSystem.executionContext
    sourceModels = AsyncParser.asyncParseTriples(args(1)).asScala
      .grouped(1000)
      .map(ts => {
        val frameModel = ModelFactory.createDefaultModel()
        ts.foreach(t => frameModel.getGraph.add(t))
        frameModel
      })
      .toSeq

    if args(2).toInt == 1 then
      useGzip = true

    val lang = args(0)
    val times = new ArrayBuffer[KafkaResult]()

    for i <- 1 to REPEATS do
      println(s"Try: $i")
      val fut = runTest(lang) map { kr =>
        times.append(kr)
        println("Sleeping 5 seconds...")
        Thread.sleep(5000)
      }
      Await.result(fut, Duration.Inf)

    printSpeed(
      sourceModels.foldLeft(0.toLong)((s, m) => s + m.size()),
      times.map(_.time),
    )
    saveRunInfo("kafka_full", config, Map(
      "times" -> times,
      "lang" -> lang,
      "useGzip" -> useGzip,
      "serverProd" -> config.getString("akka.kafka.producer.kafka-clients.bootstrap.servers"),
      "serverCons" -> config.getString("akka.kafka.consumer.kafka-clients.bootstrap.servers"),
      "file" -> args(1),
    ))
    sys.exit()


  def runTest(lang: String): Future[KafkaResult] =
    implicit val ec: ExecutionContextExecutor = consSystem.executionContext

    val t0cons: Long = System.nanoTime()
    var t0prod: Long = -1
    var t1cons: Long = -1
    var t1prod: Long = -1
    var triplesRec: Long = -1
    var bytesRec: Long = -1

    def inputStream(bytes: Array[Byte]): InputStream =
      var is: InputStream = new ByteArrayInputStream(bytes)
      if useGzip then
        is = new GZIPInputStream(is)
      is

    val deserializer: Array[Byte] => IterableOnce[TripleOrQuad] = if lang == "protobuf" then
      val protoDec = new ProtobufDecoder()
      (bytes: Array[Byte]) => {
        val frame = RDF_StreamFrame.parseFrom(inputStream(bytes))
        frame.row.flatMap(r => protoDec.ingestRow(r))
      }
    else
      (bytes: Array[Byte]) => {
        AsyncParser.asyncParseTriples(inputStream(bytes), jenaLangs(lang), "").asScala
      }


    val consFut = runCons(deserializer, sourceModels.size, consSettings)(consSystem)
    val consFut1 = consFut map { (triples, bytes) =>
      t1cons = System.nanoTime()
      triplesRec = triples
      bytesRec = bytes
    }

    println("Sleeping 2 seconds...")
    val waitFuture = Future {Thread.sleep(2000)}

    val serializer = getSerializer(lang, useGzip, config)
    val prodFut = waitFuture flatMap { _ =>
      t0prod = System.nanoTime()
      runProd(serializer, sourceModels, prodSettings)(prodSystem)
    } map { _ =>
      t1prod = System.nanoTime()
    }

    for
      _ <- consFut1
      _ <- prodFut
    yield
      KafkaResult(t0cons, t0prod, t1cons, t1prod, triplesRec, bytesRec)

  def getSerializer(lang: String, useGzip: Boolean, config: Config) =
    def outputStream(): (ByteArrayOutputStream, OutputStream) =
      val bos = new ByteArrayOutputStream()
      if useGzip then
        (bos, GZIPOutputStream(bos))
      else
        (bos, bos)

    if lang == "protobuf" then
      val protoEnc = new ProtobufEncoder(RDFStreamOptions(config))
      (m: Model) => {
        val (bos, os) = outputStream()
        val srs = m.getGraph.stream.iterator.asScala
          .flatMap(t => protoEnc.toProtobufRows(t))
        RDF_StreamFrame(srs.toSeq).writeTo(os)
        os.close()
        bos.toByteArray
      }
    else
      (m: Model) => {
        val (bos, os) = outputStream()
        RDFWriter.create()
          .lang(jenaLangs(lang))
          .source(m)
          .output(os)
        os.close()
        bos.toByteArray
      }

  def runCons(deserializer: Array[Byte] => IterableOnce[TripleOrQuad], expectedCount: Int,
              settings: ConsumerSettings[String, Array[Byte]])(implicit system: ActorSystem[Nothing]):
  Future[(Long, Long)] =
    implicit val ec: ExecutionContextExecutor = system.executionContext
    val kafkaCons = Consumer.plainSource(
      settings,
      Subscriptions.assignmentOffsetsForTimes(
        new TopicPartition("rdf", 0) -> (System.currentTimeMillis - 100)
      )
    )
    var totalSize: Long = 0
    var count: Long = 0
    var triples: Long = 0
    val consFut = kafkaCons
      .map(cr => {
        totalSize += cr.serializedValueSize()
        cr.value()
      })
      .map(bytes =>
        deserializer(bytes) match
          case r: Seq[Any] => triples += r.size
          case r => r.iterator.foreach(_ => triples += 1)
      )
      .map(_ => {
        count += 1
        count
      })
      .takeWhile(
        _ => count < expectedCount
      )
      .run()
    consFut map { _ => (triples, totalSize) }

  def runProd(serializer: Model => Array[Byte], sourceModels: Seq[Model],
              settings: ProducerSettings[String, Array[Byte]])(implicit system: ActorSystem[Nothing]):
  Future[Done] =
    implicit val ec: ExecutionContextExecutor = system.executionContext
    val kafkaProd = Producer.plainSink(settings)
    Source[Model](sourceModels)
      .map(serializer)
      .map(bytes => new ProducerRecord[String, Array[Byte]]("rdf", bytes))
      .runWith(kafkaProd)