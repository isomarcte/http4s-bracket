package io.isomarcte.http4s.bracket.core

import cats.data._
import cats.effect._
import cats.effect.syntax.all._
import cats.implicits._
import org.http4s._
import org.http4s.server._

/** Middelwares which allow for bracketing on a Request/Response, including
  * the completion of the Response body stream.
  *
  * These are analogous to `cats.effect.Bracket` and `fs2.Stream.bracket`. The
  * reason that they exist is because due to the full termination of a
  * Response being a function of the termination of the `fs2.Stream` which
  * backs the response body, you can't actually use either
  * `cats.effect.Bracket` or `fs2.Stream.bracket` directly.
  *
  * http4s has encodings similar to these in the main repo, but they are all
  * special cased. Having a generic implementation opens up the possibility of
  * writing many interesting middlewares with ease.
  *
  * @see
  * [[io.isomarcte.http4s.bracket.core.concurrent.ConcurrentRequestTrackingMiddleware]]
  * for an example.
  */
object BracketRequestResponseMiddleware {

  /** Bracket on the start of a request and the completion of processing the
    * response ''body Stream''.
    *
    * @note A careful reader might be wondering where the analogous `use`
    *       parameter from `cats.effect.Bracket` has gone. The use of the
    *       acquired resource is running the request, thus the `use` function
    *       is actually just the normal context route function from http4s
    *       `Kleisli[OptionT[F, *], ContextRequest[F, A], Response[F]]`.
    *
    * @param acquire Effect to run each time a request is received. The result
    *        of it is put into a `ContextRequest` and passed to the underlying
    *        routes.
    *
    * @param release Effect to run at the termination of each response body,
    *        or on any error after `acquire` has run. Will always be called
    *        exactly once if `acquire` is invoked, for each request/response.
    */
  def bracketRequestResponseCase[F[_], A](
    acquire: F[A]
  )(release: (A, ExitCase[Throwable]) => F[Unit])(implicit F: Concurrent[F]): ContextMiddleware[F, A] =
    (contextService: Kleisli[OptionT[F, *], ContextRequest[F, A], Response[F]]) =>
      Kleisli((request: Request[F]) =>
        OptionT
          .liftF(
            acquire.flatMap(a =>
              atMostOnce_[F, Kleisli[F, (A, ExitCase[Throwable]), *], Unit](
                Kleisli { case (a, ec) =>
                  release(a, ec)
                }
              ).map(releaseK => (a, releaseK))
            )
          )
          .flatMap { case (a, release) =>
            OptionT(
              contextService(ContextRequest(a, request))
                .foldF(release.run((a, ExitCase.Completed)) *> F.pure(None: Option[Response[F]]))(response =>
                  F.pure(Some(response.copy(body = response.body.onFinalizeCaseWeak(ec => release.run((a, ec))))))
                )
                .guaranteeCase {
                  case ExitCase.Completed =>
                    F.unit
                  case otherwise =>
                    release.run((a, otherwise))
                }
            )
          }
      )

  /** As [[#bracketRequestResponseCase]], but `release` is simplified, ignoring
    * the exit condition.
    */
  def bracketRequestResponse[F[_]: Concurrent, A](acquire: F[A])(release: A => F[Unit]): ContextMiddleware[F, A] =
    bracketRequestResponseCase[F, A](acquire) { case (a, _) =>
      release(a)
    }
}
