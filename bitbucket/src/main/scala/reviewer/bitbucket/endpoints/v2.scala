package reviewer.bitbucket.endpoints

import java.net.URI

import io.circe.{Decoder, Encoder}
import reviewer.bitbucket.model.{Build, PullRequests, RepoSlug, Status, Unknown, Username, Workspace}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe._
import io.circe.generic.auto._
import sttp.tapir.model.UsernamePassword

import scala.util.Try

object v2 {
  import codecs._
  import json._
  import schema._

  val fetchPullRequests: Endpoint[(UsernamePassword, Username), Unit, PullRequests, Nothing] =
    endpoint
      .get
      .in(auth.basic[UsernamePassword])
      .in("pullrequests" / path[Username])
      .out(jsonBody[PullRequests])
      .errorOut(statusCode(StatusCode.NotFound))

  val repoPullRequests: Endpoint[(UsernamePassword, Workspace, RepoSlug), Unit, PullRequests, Nothing] =
    endpoint.get
      .in(auth.basic[UsernamePassword])
      .in("repositories" /path[Workspace] / path[RepoSlug] )
      .out(jsonBody[PullRequests])
      .errorOut(statusCode(StatusCode.NotFound))

}

object json {
  import io.circe.optics.JsonPath._
  import cats.implicits._

  implicit val uriDecoder: Decoder[URI] = Decoder.decodeString.emapTry(u => Try(URI.create(u)))
  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap(_.toString)

  implicit val statusDecoder: Decoder[Status] = Decoder.decodeJson.emap {
    json =>
      val statusType = root.selectDynamic("type").string.getOption(json)
      val state = root.state.string.getOption(json)
      (statusType, state).mapN {
        case ("build", "SUCCESSFUL") =>
          Build(true)
        case (other, _) =>
          Unknown(other, false)
      }.toRight("Could not parse state")

  }
}
object schema {
  implicit val uriSchema: Schema[URI] = Schema(SchemaType.SString)
}

object codecs {
  implicit val usernameCodec: Codec[String, Username, CodecFormat.TextPlain] =
    Codec.string.map(Username)(_.value)

  implicit val workspaceCodec: Codec[String, Workspace, CodecFormat.TextPlain] =
    Codec.string.map(Workspace)(_.value)

  implicit val repoSlugCodec: Codec[String, RepoSlug, CodecFormat.TextPlain] =
    Codec.string.map(RepoSlug)(_.value)
}