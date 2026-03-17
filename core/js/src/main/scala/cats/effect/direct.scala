/*
 * Copyright 2021-2026 Typelevel
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

package cats.effect

import cats.effect.kernel.{Async, Cont, MonadCancelThrow}
import cats.effect.kernel.syntax.all._
import cats.syntax.all._
import java.util.concurrent.CancellationException
import scala.scalajs.js
import scala.scalajs.js.|

object direct extends DirectCompat {

  private final val RightUnit = Right(())

  private[effect] def asyncImpl[F[_], A](body: Await[F] => A)(implicit F: Async[F]): F[A] = {
    F.delay(new Await[F]).flatMap { await =>
      F.delay(body(await)).guarantee(F.delay(await.done = true)).background.use { oc =>
        def continue: F[Unit] =
          F.cont {
            new Cont[F, Unit, Unit] {
              def apply[G[_]](implicit G: MonadCancelThrow[G]) = { (cb, get, lift) =>
                def delay[B](thunk: => B) = lift(F.delay(thunk))
                G.uncancelable { poll =>
                  delay(await.next).flatMap { // wait until the next step is available
                    case null => delay(await.gate = cb) *> get
                    case _ => G.unit
                  } *> delay {
                    if (await.done)
                      G.unit
                    else {
                      val next = await.next
                      await.next = null.asInstanceOf[F[Any]]
                      val resolve = await.resolve
                      await.resolve = null
                      val reject = await.reject
                      await.reject = null

                      poll(lift(next)) // run the next step and return its result to the async block
                        .flatMap(a => delay(resolve(a)))
                        .onError { case e => delay(reject(e): Unit) }
                        .onCancel(delay(reject(new CancellationException): Unit))
                        .void
                    }
                  }.flatten
                }
              }
            }
          }

        def loop: F[A] =
          F.delay(await.done).ifM(
            oc.flatMap(_.embedNever),
            continue >> loop
          )

        loop
      }
    }
  }

  final class Await[F[_]] private[direct] {
    private[direct] var gate: Right[Nothing, Unit] => Unit = _
    private[direct] var next: F[Any] = _
    private[direct] var resolve: js.Function1[Any | js.Thenable[Any], ?] = _
    private[direct] var reject: js.Function1[Any, ?] = _
    private[direct] var done = false

    private[direct] def continue(next: F[Any], resolve: js.Function1[Any | js.Thenable[Any], ?], reject: js.Function1[Any, ?]): Unit = {
      this.next = next
      this.resolve = resolve
      this.reject = reject
      if (gate ne null) {
        gate(RightUnit)
        gate = null
      }
    }

    private[direct] def complete(): Unit = {
      done = true
      if (gate ne null) {
        gate(RightUnit)
        gate = null
      }
    }
  }

  implicit final class AwaitSyntax[F[_], A](val self: F[A]) extends AnyVal {
    def await(implicit await: Await[F]): A = {
      import scala.scalajs.js.wasm.JSPI.allowOrphanJSAwait
      val result = new js.Promise[Any](await.continue(self.asInstanceOf[F[Any]], _, _))
      js.await(result).asInstanceOf[A]
    }
  }
}
