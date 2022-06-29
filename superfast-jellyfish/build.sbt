lazy val akkaV = "2.6.19"
lazy val akkaHttpV = "10.2.9"
lazy val slf4jV = "1.7.36"
lazy val jenaV = "4.5.0"

enablePlugins(AkkaGrpcPlugin)
enablePlugins(GraalVMNativeImagePlugin)

lazy val commonSettings = Seq(
  name := "superfast_jellyfish",
  version := "0.1",
  scalaVersion := "3.1.2",

  // Packages available only with Scala 2.13
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http2-support" % akkaHttpV,
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "com.typesafe.akka" %% "akka-actor-typed" % akkaV,
    "com.typesafe.akka" %% "akka-stream-typed" % akkaV,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % Test,
    "com.typesafe.akka" %% "akka-discovery" % akkaV,
    "com.typesafe.akka" %% "akka-stream-kafka" % "3.0.0",
  ).map(_.cross(CrossVersion.for3Use2_13)),

  // Compiled with Scala 3 or not Scala at all
  libraryDependencies ++= Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
    "org.scalatest" %% "scalatest" % "3.2.12" % Test,
    "org.slf4j" % "slf4j-api" % slf4jV,
    "org.slf4j" % "slf4j-simple" % slf4jV,
    "org.apache.jena" % "jena-core" % jenaV,
    "org.apache.jena" % "jena-arq" % jenaV,
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
    // For debugging
    "org.json4s" %% "json4s-jackson" % "4.0.5",
  ),

  // https://github.com/akka/akka-grpc/issues/1471#issuecomment-946476281
  excludeDependencies ++= Seq(
    "com.thesamet.scalapb"   % "scalapb-runtime_2.13",
    "org.scala-lang.modules" % "scala-collection-compat_2.13"
  ),

  assembly / assemblyMergeStrategy := {
    case PathList("module-info.class") => MergeStrategy.discard
    //case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case PathList("META-INF", xs @ _*) =>
      (xs map {_.toLowerCase}) match {
        case "services" :: xs => MergeStrategy.filterDistinctLines
        case _ => MergeStrategy.discard
      }
    case PathList("reference.conf") => MergeStrategy.concat
    case _ => MergeStrategy.first
  },

  graalVMNativeImageGraalVersion := Some("22.1.0"),
  graalVMNativeImageOptions := Seq("pl.ostrzyciel.superfast_jellyfish.benchmark.RawSerDesBench"),

  fork := true,

  Test / logBuffered := false,
)

lazy val settings = commonSettings

lazy val hai_designer = project
  .in(file("."))
  .settings(settings: _*)

