package io.isomarcte.http4s.bracket.core

import cats.effect._
import org.scalatest._
import org.scalatest.flatspec._
import org.scalatest.matchers.should._
import scala.concurrent.ExecutionContext

abstract class BaseTest extends AnyFlatSpec with Matchers {
  def io(fa: IO[Assertion]): Assertion = fa.unsafeRunSync()

  implicit final protected lazy val cs: ContextShift[IO] = BaseTest.cs
}

object BaseTest {
  implicit lazy val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
}
