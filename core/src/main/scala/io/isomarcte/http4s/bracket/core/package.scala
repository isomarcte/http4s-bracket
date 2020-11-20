package io.isomarcte.http4s.bracket

import cats.effect._
import cats.effect.concurrent._
import cats.effect.syntax.all._
import cats.implicits._

package object core {

  def atMostOnce_[F[_], G[_], A](fa: G[A])(implicit F: Sync[F], G: Concurrent[G]): F[G[Unit]] =
    Semaphore
      .in[F, G](1L)
      .map((sem: Semaphore[G]) =>
        sem
          .tryAcquire
          .flatMap {
            case true =>
              // first!
              fa.void
            case _ =>
              G.unit
          }
          .uncancelable
      )

  def atMostOnce[F[_]](fa: F[Unit])(implicit F: Concurrent[F]): F[F[Unit]] = atMostOnce_[F, F, Unit](fa)
}
