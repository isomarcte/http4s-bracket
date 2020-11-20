package io.isomarcte.http4s.bracket.core

import cats.effect._
import cats.effect.concurrent._
import cats.data.Kleisli

final class AtMostOnceTest extends BaseTest {

  "Running an effect at most once" should "only run it once" in
    io {
      Ref
        .of[IO, Long](0L)
        .flatMap { ref =>
          atMostOnce(ref.update(_ + 1)).map(incrementAtMostOnce => (ref, incrementAtMostOnce))
        }
        .flatMap { case (ref, incrementAtMostOnce) =>
          incrementAtMostOnce *> incrementAtMostOnce *> ref.get.flatMap(value => IO(value shouldBe 1L))
        }
    }

  it should "only run once when using a Kleisli" in
    io {
      Ref
        .of[IO, Long](0L)
        .flatMap { ref =>
          atMostOnce_[IO, Kleisli[IO, Unit, *], Unit](Kleisli.liftF(ref.update(_ + 1)))
            .map(incrementAtMostOnce => (ref, incrementAtMostOnce))
        }
        .flatMap { case (ref, incrementAtMostOnce) =>
          incrementAtMostOnce.run(()) *> incrementAtMostOnce.run(()) *> ref.get.flatMap(value => IO(value shouldBe 1L))
        }
    }
}
