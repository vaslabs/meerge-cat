name := "meerge-cat"


scalaVersion in ThisBuild := "2.13.3"
import ReleaseTransformations._
import microsites.ExtraMdFileConfig

lazy val `meerge-cat` = (project in file("."))
  .aggregate(bitbucket)
  .settings(noPublishSettings)

lazy val bitbucket = (project in file("bitbucket"))
  .settings(scalacOptions ++= compilerFlags)
  .settings(libraryDependencies ++= Dependencies.modules.bitbucket)
  .enablePlugins(DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .settings(dockerSettings)
  .settings(universalPackageSettings)
  .settings(licenseSettings)

lazy val site = (project in file("site"))
  .enablePlugins(MicrositesPlugin)
  .settings(noPublishSettings)
  .settings(siteSettings)
  .settings(licenseSettings)

val compilerFlags = Seq(
  "-deprecation",
  "-feature",
  "-language:postfixOps",              //Allow postfix operator notation, such as `1 to 10 toList'
  "-language:implicitConversions",
  "-language:higherKinds",
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Ywarn-numeric-widen",              // Warn when numerics are widened.
  "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  "-Ywarn-unused:params",              // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",            // Warn if a private member is unused.
  "-Ywarn-value-discard",               // Warn when non-Unit expression results are unused.
  "-Ywarn-unused:imports",
  "-Xfatal-warnings"
)

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,                            // : ReleaseStep
  inquireVersions,                                      // : ReleaseStep
  runClean,                                             // : ReleaseStep
  runTest,
  setReleaseVersion,                                    // : ReleaseStep
  commitReleaseVersion,                                 // : ReleaseStep, performs the initial git checks
  tagRelease,                                           // : ReleaseStep
  ReleaseStep(releaseStepCommand("docker:publish")),      // : ReleaseStep, publish docker
  setNextVersion,                                       // : ReleaseStep
  commitNextVersion,                                    // : ReleaseStep
  pushChanges                                           // : ReleaseStep, also checks that an upstream branch is properly configured
)


lazy val dockerSettings = Seq(
  (packageName in Docker) := "meerge-cat",
  dockerBaseImage := "openjdk:14-jdk-alpine3.10",
  dockerUsername := Some("vaslabs")
)

lazy val universalPackageSettings = Seq(
  name in Universal := "meerge-cat"
)

lazy val licenseSettings = Seq(
  licenses := List("Apache 2.0" -> new URL("https://opensource.org/licenses/Apache-2.0")),
  homepage := Some(url("https://git.vaslabs.org/vaslabs/sbt-kubeyml")),
  startYear := Some(2020)
)

lazy val noPublishSettings = Seq(
  publish / skip := true
)

lazy val githubToken = sys.env.get("GITHUB_TOKEN")
lazy val siteSettings = Seq(
  micrositeName := "Meerge Cat",
  micrositeDescription := "Portable bot for auto-merging PRs",
  micrositeUrl := "https://meergecat.vaslabs.io",
  micrositeTwitter := "@vaslabs",
  micrositeTwitterCreator := "@vaslabs",
  micrositeGithubOwner := "vaslabs",
  micrositeGithubRepo := "meerge-cat",
  micrositeAuthor := "Vasilis Nicolaou",
  micrositeGithubToken := githubToken,
  micrositePushSiteWith := githubToken.map(_ => GitHub4s).getOrElse(GHPagesPlugin),
  micrositeGitterChannel := false,
  micrositeExtraMdFiles := Map(
    file("README.md") -> ExtraMdFileConfig(
      "index.md",
      "home",
      Map("section" -> "home", "position" -> "0", "permalink" -> "/")
    )
  ),
  excludeFilter in ghpagesCleanSite :=
    new FileFilter{
      def accept(f: File) = (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath
    } || "versions.html"
)