package reviewer.bitbucket

import java.net.URI

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers._
import akka.stream.scaladsl.{Sink, Source}
import cats.effect.{ExitCode, IO}
import cats.implicits._
import com.monovore.decline.Opts
import com.monovore.decline.effect._
import reviewer.bitbucket.algorithm.GatherPullRequestsWithSuccessfulBuilds
import reviewer.bitbucket.endpoints.v2
import reviewer.bitbucket.model.Username
import sttp.client.akkahttp.AkkaHttpBackend
import sttp.model.Uri
import sttp.tapir.client.sttp._
import sttp.tapir.model.UsernamePassword

object Main extends CommandIOApp(
  name="reviewer",
  header="Reviewer bot to automatically merge PRs",
  version = "0.0.1-alpha"
) {

  import BitbucketOpts._

  override def main: Opts[IO[ExitCode]] = (usernameOpt, passwordEnvVariable, apiBaseUri).mapN {
    case (username, passwordEnv, uri) => {
      implicit val actorSystem = ActorSystem("Reviewer")
      val password = IO.fromEither(sys.env.get(passwordEnv)
        .toRight(new RuntimeException(s"Could not find password with env variable ${passwordEnv}"))
      )
      val usernamePassword = password.map(p => UsernamePassword(username.value, Some(p)))
      usernamePassword.flatMap(startStream(_, uri))

    }.map(_ => ExitCode.Success)
  }


  private def startStream(usernamePassword: UsernamePassword, baseUri: Uri)(implicit actorSystem: ActorSystem): IO[Done] = {
    implicit val sttpBackend = AkkaHttpBackend.usingActorSystem(actorSystem)
    val algorithm = new GatherPullRequestsWithSuccessfulBuilds(
      uri => Source.future(Http().singleRequest(HttpRequest(uri = uri).addHeader(
        Authorization(BasicHttpCredentials(usernamePassword.username, usernamePassword.password.getOrElse("")))
      )))
    )
    IO.fromFuture {
      IO {
        Source.future {
          val request = v2.fetchPullRequests.toSttpRequestUnsafe(baseUri)
            .apply(usernamePassword, Username(usernamePassword.username))
          request.send()
        }.map(_.body)
          .collect {
            case Right(o) => o
          }.via(
          algorithm.mergeablePullRequests
        ).runWith(Sink.foreach(println))
      }
    }
  }

}

object BitbucketOpts {
  val usernameOpt: Opts[Username] = Opts
    .option[String]("username", "The username of the bot to auto-merge its PRs", "u")
    .map(Username)

  val passwordEnvVariable: Opts[String] = Opts
    .option[String]("password-env", "The environment variable name to pick the password from", "p")

  val apiBaseUri: Opts[Uri] = Opts.option[String]("api-uri", "The base uri for the version control api (bitbucket v2 supported only)")
    .map(URI.create)
    .map(Uri.apply)

}