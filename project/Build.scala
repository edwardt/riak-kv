import sbt._
import sbt.Keys._

import bintray.Plugin._
import bintray.Keys._

object Build extends Build {

  val customBintraySettings = bintrayPublishSettings ++ Seq(
    packageLabels in bintray       := Seq("riak"),
    bintrayOrganization in bintray := Some("plasmaconduit"),
    repository in bintray          := "releases"
  )

  val root = Project("root", file("."))
    .settings(customBintraySettings: _*)
    .settings(
      name                  := "riak-kv",
      organization          := "com.plasmaconduit",
      version               := "0.1.0",
      scalaVersion          := "2.11.2",
      licenses              += ("MIT", url("http://opensource.org/licenses/MIT")),
      resolvers           += "Plasma Conduit Repository" at "http://dl.bintray.com/plasmaconduit/releases",
      libraryDependencies += "com.plasmaconduit" %% "rx-netty-scala" % "0.14.0",
      libraryDependencies += "com.plasmaconduit" %% "json" % "0.5.0",
      libraryDependencies += "com.plasmaconduit" %% "url" % "0.3.0"
    )

}
