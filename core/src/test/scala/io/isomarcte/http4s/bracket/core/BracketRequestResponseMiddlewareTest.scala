package io.isomarcte.http4s.bracket.core

import fs2.Stream
import cats.data._
import cats.effect._
import cats.effect.concurrent._
import cats.data.Kleisli
import org.http4s._
import org.http4s.server._
import org.http4s.circe._
import io.circe._
import io.circe.syntax._

final class BracketRequestResponseMiddlewareTest extends BaseTest {

  "When no errors occur, acquire, release, or the service" should "all execute correctly" in
    io {
      for {
        acquireRef <- Ref.of[IO, Long](0L)
        releaseRef <- Ref.of[IO, Long](0L)
        middleware =
          BracketRequestResponseMiddleware.bracketRequestResponseCase[IO, Long](acquireRef.updateAndGet(_ + 1L)) {
            case (_, ec) =>
              IO(ec shouldBe ExitCase.Completed) *> releaseRef.update(_ + 1L)
          }
        routes = middleware(
          Kleisli((contextRequest: ContextRequest[IO, Long]) =>
            OptionT.liftF(IO(Response(status = Status.Ok).withEntity(contextRequest.context.asJson)))
          )
        )
        response      <- routes.run(Request[IO]()).getOrElseF(IO(fail("Got None for response")))
        acquireCount  <- acquireRef.get
        responseBody  <- response.as[Json]
        releaseCount  <- releaseRef.get
        responseValue <- IO.fromEither(Decoder[Long].decodeJson(responseBody))
      } yield {
        acquireCount shouldBe 1L
        releaseCount shouldBe 1L
        responseValue shouldBe 1L
      }
    }

  "When an error occurs in the service" should "acquire/release should still all execute correctly" in
    io {
      for {
        acquireRef <- Ref.of[IO, Long](0L)
        releaseRef <- Ref.of[IO, Long](0L)
        error = new RuntimeException
        middleware =
          BracketRequestResponseMiddleware.bracketRequestResponseCase[IO, Long](acquireRef.updateAndGet(_ + 1L)) {
            case (_, ec) =>
              IO(ec shouldBe ExitCase.Error(error)) *> releaseRef.update(_ + 1L)
          }
        routes = middleware(Kleisli(Function.const(OptionT.liftF(IO.raiseError(error)))))
        response     <- routes.run(Request[IO]()).value.attempt
        acquireCount <- acquireRef.get
        releaseCount <- releaseRef.get
      } yield {
        acquireCount shouldBe 1L
        releaseCount shouldBe 1L
        response shouldBe Left(error)
      }
    }

  "When no response is given" should "acquire/release should still all execute correctly" in
    io {
      for {
        acquireRef <- Ref.of[IO, Long](0L)
        releaseRef <- Ref.of[IO, Long](0L)
        middleware =
          BracketRequestResponseMiddleware.bracketRequestResponseCase[IO, Long](acquireRef.updateAndGet(_ + 1L)) {
            case (_, ec) =>
              IO(ec shouldBe ExitCase.Completed) *> releaseRef.update(_ + 1L)
          }
        routes = middleware(Kleisli(Function.const(OptionT.none)))
        response     <- routes.run(Request[IO]()).value
        acquireCount <- acquireRef.get
        releaseCount <- releaseRef.get
      } yield {
        acquireCount shouldBe 1L
        releaseCount shouldBe 1L
        response shouldBe None
      }
    }

  "When more than one request is running at a time" should "release Refs should reflect the current state" in
    io {
      for {
        acquireRef <- Ref.of[IO, Long](0L)
        releaseRef <- Ref.of[IO, Long](0L)
        middleware =
          BracketRequestResponseMiddleware.bracketRequestResponseCase[IO, Long](acquireRef.updateAndGet(_ + 1L)) {
            case (_, ec) =>
              IO(ec shouldBe ExitCase.Completed) *> releaseRef.update(_ + 1L)
          }
        routes = middleware(
          Kleisli((contextRequest: ContextRequest[IO, Long]) =>
            OptionT.liftF(IO(Response(status = Status.Ok).withEntity(contextRequest.context.asJson)))
          )
        )
        // T0
        acquireCount0 <- acquireRef.get
        releaseCount0 <- releaseRef.get
        // T1
        response1     <- routes.run(Request[IO]()).value
        acquireCount1 <- acquireRef.get
        releaseCount1 <- releaseRef.get
        // T2
        response2     <- routes.run(Request[IO]()).value
        acquireCount2 <- acquireRef.get
        releaseCount2 <- releaseRef.get
        // T3
        responseBody3  <- response1.fold(IO.raiseError[Json](new AssertionError))(_.as[Json])
        responseValue3 <- IO.fromEither(Decoder[Long].decodeJson(responseBody3))
        acquireCount3  <- acquireRef.get
        releaseCount3  <- releaseRef.get
        // T4
        responseBody4  <- response2.fold(IO.raiseError[Json](new AssertionError))(_.as[Json])
        responseValue4 <- IO.fromEither(Decoder[Long].decodeJson(responseBody4))
        acquireCount4  <- acquireRef.get
        releaseCount4  <- releaseRef.get
      } yield {
        // T0
        acquireCount0 shouldBe 0L
        releaseCount0 shouldBe 0L
        // T1
        response1.map(_.status) shouldBe Some(Status.Ok)
        acquireCount1 shouldBe 1L
        releaseCount1 shouldBe 0L
        // T2
        response2.map(_.status) shouldBe Some(Status.Ok)
        acquireCount2 shouldBe 2L
        releaseCount2 shouldBe 0L
        // T3
        responseValue3 shouldBe 1L
        acquireCount3 shouldBe 2L
        releaseCount3 shouldBe 1L
        // T4
        responseValue4 shouldBe 2L
        acquireCount4 shouldBe 2L
        releaseCount4 shouldBe 2L
      }
    }

  "When an error occurs during processing of the Response body" should "acquire/release should all execute correctly" in
    io {
      for {
        acquireRef <- Ref.of[IO, Long](0L)
        releaseRef <- Ref.of[IO, Long](0L)
        error = new AssertionError
        middleware =
          BracketRequestResponseMiddleware.bracketRequestResponseCase[IO, Long](acquireRef.updateAndGet(_ + 1L)) {
            case (_, ec) =>
              IO(ec shouldBe ExitCase.Error(error)) *> releaseRef.update(_ + 1L)
          }
        routes = middleware(
          Kleisli(
            Function.const(OptionT.liftF(IO(Response(status = Status.Ok).withBodyStream(Stream.raiseError[IO](error)))))
          )
        )
        response     <- routes.run(Request[IO]()).getOrElseF(IO(fail("Got None for response")))
        acquireCount <- acquireRef.get
        responseBody <- response.as[Json].attempt
        releaseCount <- releaseRef.get
      } yield {
        acquireCount shouldBe 1L
        releaseCount shouldBe 1L
        responseBody shouldBe Left(error)
      }
    }

  "When an error occurs during acquire" should "release should not run" in
    io {
      val error: Throwable = new AssertionError
      val middleware: ContextMiddleware[IO, Unit] =
        BracketRequestResponseMiddleware.bracketRequestResponseCase[IO, Unit](IO.raiseError[Unit](error)) { case _ =>
          IO(fail("Release should not execute")).void
        }
      val routes: HttpRoutes[IO] = middleware(Kleisli(Function.const(OptionT.none)))
      for {
        response <- routes.run(Request[IO]()).value.attempt
      } yield {
        response shouldBe Left(error)
      }
    }

  "When an error occurs during release" should
    "acquire should execute correctly and release should only be attempted once" in
    io {
      val error: Throwable = new AssertionError
      for {
        acquireRef <- Ref.of[IO, Long](0L)
        releaseRef <- Ref.of[IO, Long](0L)
        middleware =
          BracketRequestResponseMiddleware.bracketRequestResponseCase[IO, Long](acquireRef.updateAndGet(_ + 1L)) {
            case (_, ec) =>
              IO(ec shouldBe ExitCase.Completed) *> releaseRef.update(_ + 1L) *> IO.raiseError[Unit](error)
          }
        routes = middleware(
          Kleisli((contextRequest: ContextRequest[IO, Long]) =>
            OptionT.liftF(IO(Response(status = Status.Ok).withEntity(contextRequest.context.asJson)))
          )
        )
        response     <- routes.run(Request[IO]()).getOrElseF(IO(fail("Got None for response")))
        acquireCount <- acquireRef.get
        responseBody <- response.as[Json].attempt
        releaseCount <- releaseRef.get
      } yield {
        acquireCount shouldBe 1L
        releaseCount shouldBe 1L
        responseBody shouldBe Left(error)
      }
    }
}
