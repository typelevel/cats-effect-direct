# cats-effect-direct
<!-- [![Latest version](https://index.scala-lang.org/typelevel/cats-effect/cats-effect/latest.svg?color=orange)](https://index.scala-lang.org/typelevel/cats-effect/cats-effect) -->
[![Discord](https://img.shields.io/discord/632277896739946517.svg?label=&logo=discord&logoColor=ffffff&color=404244&labelColor=6A7EC2)](https://discord.gg/QNnHKHq5Ts)

> [!WARNING]
> **Coming from `cats-effect-cps`?** That was this project's name up to 0.5.0 (July 2025), when it was a macro-based transformation on top of [dotty-cps-async](https://github.com/dotty-cps-async/dotty-cps-async). Version 1.0.0 renamed it and [rewrote it](https://github.com/typelevel/cats-effect-direct/releases/tag/v1.0.0) on JDK continuations, so everything below is about cats-effect-direct 1.x, not cats-effect-cps.
>
> `org.typelevel::cats-effect-cps:0.5.0` depends on dotty-cps-async **0.9.21** (April 2024), which doesn't compile on Scala 3.9 and later ([#321](https://github.com/typelevel/cats-effect-direct/issues/321)) and has bugs that newer dotty-cps-async versions fix (e.g. [dotty-cps-async#144](https://github.com/dotty-cps-async/dotty-cps-async/issues/144)). Newer versions are published under a different organization (`io.github.dotty-cps-async`, not `com.github.rssh`), so adding one doesn't evict 0.9.21. Your options:
>
> - **Macro-based `async`/`await` on current Scala:** use dotty-cps-async's own Cats Effect support, [cps-async-connect](https://github.com/dotty-cps-async/cps-async-connect): `"io.github.dotty-cps-async" %% "cps-async-connect-cats-effect" % "1.4.0"`.
> - **Keep cats-effect-cps 0.5.0** for now: exclude the old dotty-cps-async and add a current one:
>   ```scala
>   libraryDependencies ++= Seq(
>     ("org.typelevel" %% "cats-effect-cps" % "0.5.0").exclude("com.github.rssh", "dotty-cps-async_3"),
>     "io.github.dotty-cps-async" %% "dotty-cps-async" % "1.4.0"
>   )
>   ```
> - **Move to cats-effect-direct 1.x**, described below (Java 21+, an extra JVM flag, no Scala.js).

This is a prototype library defining `async`/`await` syntax for Cats Effect leveraging the `Continuation` type built into the JDK as part of Project Loom. `Continuation` has the advantage of working regardless of other syntactic context (e.g. it has no problem with higher order functions like `foreach`, `for`-comprehensions, nested functions/classes, or any other crazy thing), and so unlike macro-based transformations it should be quite reliable.

However, the downsides to this are two-fold. First, this library only supports Java 21 and above. Second, you must add a special argument to any JVM invocation which uses this library. Specifically, your `java` invocation must look something like this:

```shell
$ java --add-exports java.base/jdk.internal.vm=ALL-UNNAMED ...
```

This parameter causes the JVM to expose its internal implementation module (which is where `Continuation` is hidden) to the main part of the runtime. Note that it's possible `Continuation`'s API may change in a binary-incompatible way in some future Java release. If this happens, this library will probably involve some relatively dynamic binding to ensure cross-compatibility. *Hopefully*, at some point, Oracle will expose `Continuation` without needing these shenanigans, since it's much more useful than virtual threads alone.

This functionality works for any type which forms a `Sync` (including `IO`).

## Usage

```sbt
libraryDependencies += "org.typelevel" %% "cats-effect-direct" % "<version>"

// enables access to virtual thread internals
javaOptions ++= Seq("--add-exports", "java.base/jdk.internal.vm=ALL-UNNAMED")
```

Published for Scala 2.13, 2.12, and 3.0. Depends on Cats Effect 3.7.0 or higher. Requires Java 21 or higher.

## Example

Consider the following program written using a `for`-comprehension (pretend `talkToServer` and `writeToFile` exist and do the obvious things, likely asynchronously):

```scala
import cats.effect._

for {
  results1 <- talkToServer("request1", None)
  _ <- IO.sleep(100.millis)
  results2 <- talkToServer("request2", Some(results1.data))

  back <- if (results2.isOK) {
    for {
      _ <- writeToFile(results2.data)
      _ <- IO.println("done!")
    } yield true
  } else {
    IO.println("abort abort abort").as(false)
  }
} yield back
```

Using cats-effect-direct, we can choose to rewrite the above in the following style:

```scala
import cats.effect.direct._

async[IO]:    // Scala 2 requires an additional "implicit a =>" syntax here
  val results1 = talkToServer("request1", None).await
  IO.sleep(100.millis).await

  val results2 = talkToServer("request2", Some(results1.data)).await

  if results2.isOK then
    writeToFile(results2.data).await
    IO.println("done!").await
    true
  else
    IO.println("abort abort abort").await
    false
  end
```

There are no meaningful performance differences between these two encodings. They do almost exactly the same thing using different syntax, similar to how `for`-comprehensions are actually `flatMap` and `map` functions under the surface.

Note that you can delegate to other functions written in direct style within the context of an `async` block. This is handled by passing the `Await[IO]` capability:

```scala
def sleepPrint(line: String)(using Await[IO]): Unit =
  IO.sleep(100.millis).await
  IO.println(line).await

async[IO]:
  sleepPrint("Hello")
  sleepPrint("World")
```

Or on Scala 2:

```scala
def sleepPrint(line: String)(implicit a: Await[IO]): Unit = {
  IO.sleep(100.millis).await
  IO.println(line).await
}

async[IO] { implicit a =>
  sleepPrint("Hello")
  sleepPrint("World")
}
```

## Caveats

### Blocking

While side effects are correctly suspended within `async` blocks, *blocking* side-effects (such as `Thread.sleep`, or more usefully, anything involving `File`) are not magically converted into async equivalents. `Continuation` is not the same as a virtual thread. Thus, unless the enclosing `IO` has been `evalOn`ed onto a thread pool backed by virtual threads (which is not generally recommended), you will need to wrap blocking calls in `scala.concurrent.blocking`. For example:

```scala
async[IO]:
  IO.println("we're here").await
  scala.concurrent.blocking(Thread.sleep(1000))
  IO.println("now we're here").await
```

Alternatively, and preferrably for this example, you could just `await` an `IO.sleep`:

```scala
async[IO]:
  IO.println("we're here").await
  IO.sleep(1.second).await
  IO.println("now we're here").await
```

### Starvation

`async` blocks make it quite easy to write fairly long and involved sections of imperative code, including recursion and loops, without yielding back to the `IO` runtime. While this is very convenient, it also opens up the possibility of starving the runtime or missing cancelation signals because of how this hogs the thread. **At present, `Continuation` is not a preemptable construct.**

To fix this, it is recommended that if you do have long looping logic within `async`, you periodically run `IO.cede.await`. This will yield back to the runtime and materialize any fiber cancelation, keeping things well behaved.
