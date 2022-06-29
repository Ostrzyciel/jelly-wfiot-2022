package pl.ostrzyciel.superfast_jellyfish.benchmark

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.kafka.scaladsl.Producer
import akka.kafka.{ConsumerSettings, ProducerSettings}
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.RDFWriter
import org.apache.jena.riot.system.AsyncParser
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}
import pl.ostrzyciel.superfast_jellyfish.convert.*
import pl.ostrzyciel.superfast_jellyfish.proto.RDF_StreamFrame

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters.*

object KafkaLatencyBench:
  val config = ConfigFactory.load()

  val prodSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "Producer", config)
  val consSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "Consumer", config)
  val prodSettings = ProducerSettings(prodSystem, new StringSerializer, new ByteArraySerializer)
  val consSettings = ConsumerSettings(consSystem, new StringDeserializer, new ByteArrayDeserializer)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

  var useGzip = false

  // Arguments: [serialization language] [source file] [gzip compr. on or off (0/1)]
  def main(args: Array[String]): Unit =
    val sourceData = AsyncParser.asyncParseTriples(args(1)).asScala
      .take(LatencyUtil.mSizes.max * LatencyUtil.messageCount)
      .toSeq

    val models = Map.from(for mSize <- LatencyUtil.mSizes yield
      mSize -> sourceData
        .take(mSize * LatencyUtil.messageCount)
        .grouped(mSize)
        .map(ts => {
          val model = ModelFactory.createDefaultModel()
          ts.foreach(t => model.getGraph.add(t))
          model
        })
        .toSeq
    )

    if args(2).toInt == 1 then
      useGzip = true
    val lang = args(0)
    val resultMap = scala.collection.mutable.Map[String, Any]()

    LatencyUtil.run(
      (i, ms) => runOne(lang, models(ms), i),
      resultMap,
    )
    saveRunInfo("kafka_latency", config, Map(
      "times" -> resultMap,
      "useGzip" -> useGzip,
      "file" -> args(1),
      "lang" -> lang,
      "serverProd" -> config.getString("akka.kafka.producer.kafka-clients.bootstrap.servers"),
      "serverCons" -> config.getString("akka.kafka.consumer.kafka-clients.bootstrap.servers"),
    ))
    sys.exit()


  def runOne(lang: String, models: Seq[Model], interval: FiniteDuration): Seq[(Long, Long)] =
    implicit val ec: ExecutionContextExecutor = consSystem.executionContext

    val tsCons = new ArrayBuffer[Long]()
    val tsProd = new ArrayBuffer[Long]()

    def inputStream(bytes: Array[Byte]): InputStream =
      var is: InputStream = new ByteArrayInputStream(bytes)
      if useGzip then
        is = new GZIPInputStream(is)
      is

    val deserializer: Array[Byte] => Seq[TripleOrQuad] = if lang == "protobuf" then
      val protoDec = new ProtobufDecoder()
      (bytes: Array[Byte]) => {
        val frame = RDF_StreamFrame.parseFrom(inputStream(bytes))
        val triples = frame.row.flatMap(r => protoDec.ingestRow(r))
        // parsed and converted all rows – mark the timestamp
        tsCons.append(System.nanoTime)
        triples
      }
    else
      (bytes: Array[Byte]) => {
        val triples = AsyncParser.asyncParseTriples(inputStream(bytes), jenaLangs(lang), "")
          .asScala
          .toSeq
        tsCons.append(System.nanoTime)
        triples
      }

    val serializer = KafkaFullStreamBench.getSerializer(lang, useGzip, config)

    val consFut = KafkaFullStreamBench.runCons(deserializer, LatencyUtil.messageCount, consSettings)(consSystem)
    val prodFut = runProd(serializer, models, interval, LatencyUtil.messageCount, tsProd)(prodSystem)

    val resultFut = for
      _ <- consFut
      _ <- prodFut
    yield
      if tsProd.size != tsCons.size then
        throw new Error("Message count mismatch")
      tsProd.zip(tsCons).toSeq

    Await.result(resultFut, Duration.Inf)

  def runProd(serializer: Model => Array[Byte], sourceModels: Seq[Model], interval: FiniteDuration, nMess: Int,
              timestamps: ArrayBuffer[Long])(implicit system: ActorSystem[Nothing]):
    Future[Done] =
    implicit val ec: ExecutionContextExecutor = system.executionContext
    val kafkaProd = Producer.plainSink(prodSettings)
    Source.tick(2.seconds, interval, 0)
      .take(nMess)
      .zip(Source(sourceModels))
      .map((_, m) => {
        timestamps.append(System.nanoTime)
        m
      })
      .map(serializer)
      .map(bytes => new ProducerRecord[String, Array[Byte]]("rdf", bytes))
      .runWith(kafkaProd)


