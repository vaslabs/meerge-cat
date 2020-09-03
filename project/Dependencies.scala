import sbt._

object Dependencies {

  object version {
    object akka {
      val main = "2.6.8"
      val http = "10.2.0"
      val circeSupport = "1.34.0"
    }

    object circe {
      val core = "0.13.0"
      val extras = "0.13.0"
    }

    object decline {
      val core = "1.0.0"
    }

    object sttp {
      val core = "2.2.3"
    }
    object tapir {
      val core = "0.16.9"
    }

    val scalatest = "3.2.2"
  }

  object library {
    object akka {
      val streams = "com.typesafe.akka" %% "akka-stream" % version.akka.main
      val http = "com.typesafe.akka" %% "akka-http" % version.akka.http
      val circeSupport =  "de.heikoseeberger" %% "akka-http-circe" % version.akka.circeSupport
    }
    object circe {
      val all = Seq(
        "io.circe" %% "circe-core",
        "io.circe" %% "circe-generic",
        "io.circe" %% "circe-parser",
        "io.circe" %% "circe-optics"
      ).map(_ % version.circe.core)
      val extras = "io.circe" %% "circe-generic-extras" % version.circe.extras
    }

    object decline {
      val core = "com.monovore" %% "decline" % version.decline.core
      val catsEffect = "com.monovore" %% "decline-effect" % version.decline.core
    }

    object sttp {
      val akkaBackend = "com.softwaremill.sttp.client" %% "akka-http-backend" % version.sttp.core
    }
    object tapir {
      val core = "com.softwaremill.sttp.tapir" %% "tapir-core" % version.tapir.core
      val circe = "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % version.tapir.core
      val sttpClient = "com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % version.tapir.core
    }

    object test {
      val scalatest = "org.scalatest" %% "scalatest" % version.scalatest % Test
    }

  }

  object modules {
    import library._
    val bitbucket = circe.all ++ Seq(
        akka.streams,
        akka.http,
        akka.circeSupport,
        circe.extras,
        decline.catsEffect,
        decline.core,
        sttp.akkaBackend,
        tapir.circe,
        tapir.core,
        tapir.sttpClient,
        test.scalatest
      )
  }
}
