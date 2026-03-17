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

import cats.effect.kernel.Async

private[effect] abstract class DirectCompat { this: direct.type =>

  inline def async[F[_]](using Async[F]): AsyncSyntax[F] = new AsyncSyntax[F]

  final class AsyncSyntax[F[_]](using Async[F]) {
    def apply[A](body: Await[F] ?=> A): F[A] =
      asyncImpl[F, A](implicit a => body)
  }
}
