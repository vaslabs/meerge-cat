package reviewer.bitbucket

import java.net.URI

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.stream.scaladsl.{Sink, Source}
import cats.effect.{ExitCode, IO}
import cats.implicits._
import com.monovore.decline.Opts
import com.monovore.decline.effect._
import com.typesafe.config.Config
import reviewer.bitbucket.algorithm.{GatherPullRequestsWithSuccessfulBuilds, Merge}
import reviewer.bitbucket.endpoints.v2
import reviewer.bitbucket.model.{PullRequests, Username}
import sttp.client.akkahttp.AkkaHttpBackend
import sttp.model.Uri
import sttp.tapir.client.sttp._
import sttp.tapir.model.UsernamePassword

import scala.concurrent.{ExecutionContext, Future}
import pureconfig._
import pureconfig.generic.auto._

object Main extends CommandIOApp(
  name="reviewer",
  header="Reviewer bot to automatically merge PRs",
  version = "0.0.1-alpha"
) {

  import BitbucketOpts._

  override def main: Opts[IO[ExitCode]] = (dryRun orElse mergeAll).map {

    case command: RunCommand =>
      implicit val actorSystem = ActorSystem("Reviewer")
      implicit val executionContext = ExecutionContext.global
      val password = IO.fromEither(sys.env.get(command.passwordVariable)
        .toRight(new RuntimeException(s"Could not find password with env variable ${command.passwordVariable}"))
      )
      val usernamePassword = password.map(p => UsernamePassword(command.username.value, Some(p)))

      usernamePassword.flatMap(startStream(_, command.apiBaseUri, sink(command))).guarantee {
        IO.fromFuture(IO(actorSystem.terminate())) *> IO.unit
      } *> IO(ExitCode.Success)
  }

  private def sink(runCommand: RunCommand)(implicit actorSystem: ActorSystem, executionContext: ExecutionContext): Sink[Merge, Future[Done]] = runCommand match {
    case Run(_, username, passwordVar) =>
      val password = sys.env.getOrElse(passwordVar, "")
      Sink.foreachAsync(1) {
        merge =>
          Http().singleRequest(
            HttpRequest(HttpMethods.POST, uri = merge.link)
              .addHeader(Authorization(BasicHttpCredentials(username.value, password)))
          )
          .map(_.discardEntityBytes())
          .map(_ => println(s"Merged ${merge.link}"))
      }
    case DryRun(_, _, _) =>
      Sink.foreach(println)
  }


  private def startStream(usernamePassword: UsernamePassword, baseUri: Uri, sink: Sink[Merge, Future[Done]])(implicit actorSystem: ActorSystem): IO[Done] = {
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
        ).runWith(sink)
      }
    }
  }

  private def pullRequestsFromConfig(
          config: BitbucketConfig,
          usernamePassword: UsernamePassword,
          baseUri: Uri)(implicit backend: AkkaHttpBackend): Source[PullRequests, NotUsed] =
    Source(config.repositories).flatMapConcat {
      repo =>
        Source.future {
          val request = v2.repoPullRequests.toSttpRequestUnsafe(baseUri)
            .apply(usernamePassword, repo.workspace, repo.repoSlug)
          request.send()
        }
    }.map(_.body)
      .collect {
        case Right(o) =>
          o
      }

}

object BitbucketOpts {

  sealed trait RunCommand {
    def apiBaseUri: Uri
    def username: Username
    def passwordVariable: String
  }

  case class DryRun(apiBaseUri: Uri, username: Username, passwordVariable: String) extends RunCommand
  case class Run(apiBaseUri: Uri, username: Username, passwordVariable: String) extends RunCommand

  val usernameOpt: Opts[Username] = Opts
    .option[String]("username", "The username of the bot to auto-merge its PRs", "u")
    .map(Username)

  val passwordEnvVariable: Opts[String] = Opts
    .option[String]("password-env", "The environment variable name to pick the password from", "p")

  val apiBaseUri: Opts[Uri] = Opts.option[String]("api-uri", "The base uri for the version control api (bitbucket v2 supported only)")
    .map(URI.create)
    .map(Uri.apply)

  val dryRun = Opts.subcommand[DryRun]("dry-run", "Just display the merge requests that would have been merged") {
    (apiBaseUri, usernameOpt, passwordEnvVariable).mapN(DryRun)
  }

  val mergeAll = Opts.subcommand[Run]("merge-all", "Merge all user pull requests automatically if build is successful") {
    (apiBaseUri, usernameOpt, passwordEnvVariable).mapN(Run)
  }
}