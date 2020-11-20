package io.isomarcte.http4s.bracket.core.concurrent

import cats._
import cats.implicits._

final case class ConcurrentRequests(value: Long) extends AnyVal {
  def increment: ConcurrentRequests = copy(value = value + 1L)
  def decrement: ConcurrentRequests = copy(value = value - 1L)
}

object ConcurrentRequests {
  implicit lazy val showInstance: Show[ConcurrentRequests] = Show.fromToString

  implicit lazy val orderInstance: Order[ConcurrentRequests] = Order.by(_.value)

  implicit lazy val hashInstance: Hash[ConcurrentRequests] = Hash.by(_.value)

  lazy val zero: ConcurrentRequests = ConcurrentRequests(0L)
}
