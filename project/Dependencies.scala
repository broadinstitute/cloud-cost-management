import sbt._

object Dependencies {
  val minitestVersion = "2.2.2"
  val http4sVersion = "0.18.21"
  //val http4sVersion = "0.20.0-M4"

  val common = List(
    "io.grpc" % "grpc-netty" % "1.11.0",
    "io.monix" %% "minitest" % minitestVersion % "test",
    "io.monix" %% "minitest-laws" % minitestVersion % "test"
  )

  val automation = common

  val server = common ++ List(
    "io.grpc" % "grpc-services" % "1.11.0",
    "io.circe" %% "circe-core" % "0.10.0",
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  )
}