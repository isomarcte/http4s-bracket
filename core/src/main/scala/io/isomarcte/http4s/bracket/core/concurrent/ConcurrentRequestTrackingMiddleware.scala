package io.isomarcte.http4s.bracket.core.concurrent

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._
import io.isomarcte.http4s.bracket.core._
import org.http4s.server._

/** Middelwares for tracking the quantity of concurrent requests.
  *
  * These are generalized middlewares and can be used to implement metrics,
  * logging, max concurrent requests, etc.
  *
  * @note The concurrent request count is decremented on the completion of the
  *       Response body, or in the event of any error, and is guaranteed to
  *       only occur once.
  */
object ConcurrentRequestTrackingMiddleware {

  /** Run a side effect each time the concurrent request count increments and
    * decrements.
    *
    * @note Each side effect is given the current number of concurrent
    *       requests as an argument.
    *
    * @note `onIncrement` should will be given a value < 1 and `onDecrement`
    *       should will never be given a value < 0.
    */
  def apply[F[_]: Concurrent](
    onIncrement: ConcurrentRequests => F[Unit],
    onDecrement: ConcurrentRequests => F[Unit]
  ): F[ContextMiddleware[F, ConcurrentRequests]] =
    Ref
      .of[F, ConcurrentRequests](ConcurrentRequests.zero)
      .map(ref =>
        BracketRequestResponseMiddleware.bracketRequestResponse[F, ConcurrentRequests](
          ref.updateAndGet(_.increment).flatTap(onIncrement)
        )(Function.const(ref.updateAndGet(_.decrement).flatMap(onDecrement)))
      )

  /** As [[#apply]], but runs the same effect on increment and decrement of the concurrent request count. */
  def onChange[F[_]: Concurrent](onChange: ConcurrentRequests => F[Unit]): F[ContextMiddleware[F, ConcurrentRequests]] =
    apply[F](onChange, onChange)
}
