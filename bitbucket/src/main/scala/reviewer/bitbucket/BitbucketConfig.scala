package reviewer.bitbucket

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import reviewer.bitbucket.model.{RepoSlug, Workspace}

case class BitbucketConfig(repositories: List[Repository], author: Option[String])

case class Repository(workspace: Workspace, repoSlug: RepoSlug)


object ConfigReaders {
  implicit val repositoryReader: ConfigReader[Repository] = ConfigReader.fromString {
    value =>
      value.indexOf('/') match {
        case ind if ind >= 0 && ind < value.size - 1 =>
          val workspace = value.substring(0, ind)
          val repo = value.substring(ind + 1)
          Right(Repository(Workspace(workspace), RepoSlug(repo)))
        case _ =>
          Left(CannotConvert(value, "Repository", "Doesn't meet the criteria of workspace/reposlug"))
      }
  }
}