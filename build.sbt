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

ThisBuild / tlBaseVersion := "1.1"

ThisBuild / startYear := Some(2021)

ThisBuild / developers := List(
  tlGitHubDev("djspiewak", "Daniel Spiewak"),
  tlGitHubDev("baccata", "Olivier Melois")
)

val Scala212 = "2.12.21"
val Scala213 = "2.13.18"
val Scala3LTS = "3.3.7"
val Scala38 = "3.8.2"
ThisBuild / crossScalaVersions := Seq(Scala212, Scala213, Scala3LTS)

ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  (ThisBuild / crossScalaVersions).value.filter(_.startsWith("2.")).map { scala =>
    MatrixExclude(Map("scala" -> scala.substring(0, scala.lastIndexOf('.')), "project" -> "rootNative"))
  }
}

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("21"))

val CatsEffectVersion = "3.7.0"

lazy val root = tlCrossRootProject.aggregate(core)

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
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
  .jsSettings(
    crossScalaVersions := Seq(Scala212, Scala213, Scala38),
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withExperimentalUseWebAssembly(true)
    },
    jsEnv := {
      import org.scalajs.jsenv.nodejs.NodeJSEnv
      val config = NodeJSEnv.Config()
        .withArgs(List(
          "--experimental-wasm-exnref", // always required
          "--experimental-wasm-jspi", // required for js.async/js.await
          "--experimental-wasm-imported-strings", // optional (good for performance)
        ))
      new NodeJSEnv(config)
    },
  )
  .nativeSettings(
    crossScalaVersions := Seq(Scala3LTS)
  )
