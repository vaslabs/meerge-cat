package reviewer.bitbucket.model

import java.net.URI

import io.circe.Json

case class Username(value: String)

case class PullRequests(values: List[PullRequestSummary], next: Option[URI])

case class PullRequestSummary(id: Long, title: String, author: Author, links: Json)

sealed trait Status {
  def passed: Boolean
}

case class Build(passed: Boolean) extends Status
case class Unknown(name: String, passed: Boolean) extends Status


case class PullRequest(summary: PullRequestSummary, status: Status)

object PullRequest {
  case class Id(value: Long)
}

sealed trait State
case object Open extends State
case object Other extends State

case class Author(nickname: String)


object Repository {
  case class Name(value: String)
}