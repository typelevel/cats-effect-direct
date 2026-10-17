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

name := "cats-effect-direct"

ThisBuild / tlBaseVersion := "1.0"

ThisBuild / startYear := Some(2021)

ThisBuild / developers := List(
  tlGitHubDev("djspiewak", "Daniel Spiewak"),
  tlGitHubDev("baccata", "Olivier Melois")
)

ThisBuild / crossScalaVersions := Seq("2.12.21", "3.10.0", "3.3.7")

ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  (ThisBuild / crossScalaVersions).value.filter(_.startsWith("2.")).map { scala =>
    MatrixExclude(Map("scala" -> scala.substring(0, scala.lastIndexOf('.')), "project" -> "rootNative"))
  }
}

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("21"))

val CatsEffectVersion = "3.7.0"

lazy val root = tlCrossRootProject.aggregate(core)

lazy val core = crossProject(JVMPlatform, /*JSPlatform,*/ NativePlatform)
  .in(file("core"))
  .settings(
    name := "cats-effect-direct",
    headerEndYear := Some(2026),
    tlFatalWarnings := {
      tlFatalWarnings.value && !tlIsScala3.value
    },
    libraryDependencies ++= Seq(
      "org.typelevel" %% "scalac-compat-annotation" % "0.1.4",
      "org.typelevel" %%% "cats-effect-std" % CatsEffectVersion,
      "org.typelevel" %%% "cats-effect" % CatsEffectVersion % Test,
      "org.typelevel" %%% "munit-cats-effect" % "2.2.0" % Test
    )
  )
  .jvmSettings(
    javaOptions ++= Seq("--add-exports", "java.base/jdk.internal.vm=ALL-UNNAMED"),
    Test / fork := true
  )
  .nativeSettings(
    crossScalaVersions := (ThisBuild / crossScalaVersions).value.filter(_.startsWith("3.")))
