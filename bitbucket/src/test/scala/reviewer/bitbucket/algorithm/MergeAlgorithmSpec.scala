package reviewer.bitbucket.algorithm

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpResponse}
import akka.stream.scaladsl.{Sink, Source}
import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import reviewer.bitbucket.model.{Author, PullRequestSummary, PullRequests}

import scala.util.Random
import io.circe.syntax._
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration._


class MergeAlgorithmSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {
  import MergeAlgorithmSpec._
  implicit val actorSystem = ActorSystem("MergeAlgorithmSpec")
  val algorithm = new GatherPullRequestsWithSuccessfulBuilds(testClient)

  "conflict free PR" must "be allowed" in {
    val mergeCommandsF =
      Source.single(PullRequests(List(mergeablePullRequest), None))
        .via(algorithm.mergeablePullRequests)
        .runWith(Sink.headOption[Merge])

    val mergeCommand = Await.result(mergeCommandsF, 3 seconds)

    mergeCommand mustBe Some(Merge(mergeUri(mergeablePullRequest.id)))
  }


}

object MergeAlgorithmSpec {
  private final val ConflictUri = "local://conflict/diff"
  private final val NoConflictUri = "local://mergeable/diff"
  private final val BuildStatusSuccess = "local://buildsuccess/statuses"
  private final val BuildStatusFailure = "local://buildfailure/statuses"
  private final val UnknownStatus = "local://unknown/statuses"
  private final val EmptyStatus = "local://unknown/statuses"

  private val server: Map[String, Unit => HttpResponse] = Map(
    ConflictUri -> (_ => textResponse(conflictResponse)),
    NoConflictUri -> (_ => textResponse(mergeableResponse)),
    BuildStatusSuccess -> (_ => jsonResponse(buildSuccess)),
    BuildStatusFailure -> (_ => jsonResponse(buildFailure)),
    UnknownStatus -> (_ => jsonResponse(unknownStatus)),
    EmptyStatus -> (_ => jsonResponse(noStatus))
  )

  val testClient: String => Source[HttpResponse, NotUsed] = {
      uri =>
        Source.single(server(uri).apply(()))
  }

  private def jsonResponse(body: String) = HttpResponse().withEntity(ContentTypes.`application/json`, body)
  private def textResponse(body: String) = HttpResponse().withEntity(ContentTypes.`text/plain(UTF-8)`, body)

  private def codeLine = Random.alphanumeric.take(64).mkString
  private def conflictLine = ">>>>>>"

  private def conflictResponse: String =
    List.apply(
      codeLine,
      codeLine,
      codeLine,
      conflictLine,
      codeLine
    ).mkString("\n")

  private def mergeableResponse: String = List(codeLine, codeLine).mkString("\n")

  def href(link: String): Json = Json.obj("href" -> link.asJson)

  def mergeablePullRequest = PullRequestSummary(1L, "1", Author("bot"), Json.obj(
  "diff" -> href(NoConflictUri),
        "merge" -> href(mergeUri(1L)),
        "statuses" -> href(BuildStatusSuccess)
  ))

  def mergeUri(id: Long) = s"local://${id}/merge"

  private def buildSuccess =
    """
      |{
      |    "values": [
      |        {
      |            "state": "SUCCESSFUL",
      |            "type": "build"
      |        }
      |    ]
      |}
      |""".stripMargin

  private def buildFailure =
    s"""
      |{
      |    "values": [
      |        {
      |            "state": "${Random.alphanumeric.take(32).mkString}",
      |            "type": "build"
      |        }
      |    ]
      |}
      |""".stripMargin


  private def unknownStatus =
    s"""
       |{
       |    "values": [
       |        {
       |            "state": "${Random.alphanumeric.take(32).mkString}",
       |            "type": "${Random.alphanumeric.take(32).mkString}"
       |        }
       |    ]
       |}
       |""".stripMargin

  private def noStatus =
    s"""
       |{
       |    "values": [
       |        {
       |            "state": "${Random.alphanumeric.take(32).mkString}",
       |            "type": "${Random.alphanumeric.take(32).mkString}"
       |        }
       |    ]
       |}
       |""".stripMargin
}
