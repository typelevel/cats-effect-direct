/*
 * Copyright 2021 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

name := "cats-effect-cps"

ThisBuild / tlBaseVersion := "0.5"

ThisBuild / startYear := Some(2021)

ThisBuild / developers := List(
  tlGitHubDev("djspiewak", "Daniel Spiewak"),
  tlGitHubDev("baccata", "Olivier Melois")
)

ThisBuild / crossScalaVersions := Seq("2.12.20", "2.13.17", "3.3.6")

ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  crossScalaVersions.value.filter(_.startsWith("2.")).map { scala =>
    MatrixExclude(Map("scala" -> scala, "project" -> "rootNative"))
  }
}

val CatsEffectVersion = "3.6.3"

lazy val root = tlCrossRootProject.aggregate(core)

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("core"))
  .settings(
    name := "cats-effect-cps",
    headerEndYear := Some(2022),
    scalacOptions ++= {
      if (tlIsScala3.value)
        Seq()
      else
        Seq("-Xasync")
    },
    tlFatalWarnings := {
      tlFatalWarnings.value && !tlIsScala3.value
    },
    libraryDependencies ++= Seq(
      "org.typelevel" %% "scalac-compat-annotation" % "0.1.4",
      "org.typelevel" %%% "cats-effect-std" % CatsEffectVersion,
      "org.typelevel" %%% "cats-effect" % CatsEffectVersion % Test,
      "org.typelevel" %%% "cats-effect-testing-specs2" % "1.6.0" % Test
    ),
    libraryDependencies ++= {
      if (tlIsScala3.value)
        Seq("com.github.rssh" %%% "dotty-cps-async" % "0.9.21")
      else
        Seq(
          "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
        )
    }
  )
  .nativeSettings(
    crossScalaVersions := (ThisBuild / crossScalaVersions).value
      .filter(_.startsWith("3."))
  )
