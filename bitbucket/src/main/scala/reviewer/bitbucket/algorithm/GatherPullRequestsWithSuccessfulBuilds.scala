package reviewer.bitbucket.algorithm

import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.scaladsl.Framing.FramingException
import akka.stream.scaladsl.{Flow, Framing, Source}
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.optics.JsonPath._
import reviewer.bitbucket.endpoints.json._
import reviewer.bitbucket.model.{PullRequestSummary, PullRequests, Status}
class GatherPullRequestsWithSuccessfulBuilds(httpClient: String => Source[HttpResponse, NotUsed])(implicit val actorSystem: ActorSystem) {

  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val mergeablePullRequests: Flow[PullRequests, Merge, NotUsed] = Flow.fromFunction[PullRequests, List[PullRequestSummary]](
    _.values
  ) .mapConcat(identity)
    .via(checkDiffForConflicts).map {
    case summary =>
      root.statuses.href.string.getOption(summary.links)
      .map(_ -> summary)
  }
  .collect {
    case Some(o) => o
  }.flatMapConcat{
    case (uri, summary) =>
        httpClient(uri)
        .filter(_.status == StatusCodes.OK)
        .map(_.entity.httpEntity)
        .mapAsync(1)(
          jsonUnmarshaller.apply
        )
        .map(summary -> _)
  }.map {
    case (summary, json) =>
      root.values.as[List[Status]].getOption(json)
        .map(summary -> _)
  }.collect {
    case Some(o) => o
  }.filter(_._2.exists(_.passed))
    .map(_._1.links)
    .map {
      json =>
        root.merge.href.string.getOption(json)
    }.collect {
    case Some(o) => Merge(o)
  }

  private lazy val checkDiffForConflicts: Flow[PullRequestSummary, PullRequestSummary, NotUsed] =
    Flow.fromGraph[PullRequestSummary, PullRequestSummary, NotUsed] {
      Flow[PullRequestSummary].flatMapConcat { summary =>
        val diffUri = root.diff.href.string.getOption(summary.links)
        diffUri.fold(Source.empty[HttpResponse]) { diffUri =>
          println(s"Checking ${diffUri} for conflicts")
          httpClient(diffUri)
        }.via(checkDiffBodyForConflicts)
          .collect {
            case NoConflict => summary
          }
      }
    }

  private lazy val checkDiffBodyForConflicts: Flow[HttpResponse, MergeState, NotUsed] = Flow[HttpResponse]
    .map(_.entity.httpEntity)
    .flatMapConcat { entity =>
      entity.dataBytes
        .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, true))
        .fold[MergeState](NoConflict) {
          case (state, next) =>
            state + MergeState(next)
        }.recover {
        case _: FramingException =>
          println(s"Long line of code ${entity.httpEntity} detected, skipping")
          Conflict
    }
  }

}

sealed trait MergeState {
  def + (mergeState: MergeState): MergeState
}
object MergeState {
  def apply(byteString: ByteString): MergeState = {
    if (byteString.decodeString(StandardCharsets.UTF_8).contains(">>>>>>>")) {
      Conflict
    } else
      NoConflict
  }
}
case object Conflict extends MergeState {
  override def +(mergeState: MergeState): MergeState = Conflict
}
case object NoConflict extends MergeState {
  override def +(mergeState: MergeState): MergeState = mergeState
}

case class Merge(link: String)

