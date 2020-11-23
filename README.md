# Http4s Bracket #

This project provides a function for bracketing the processing of a Request and the full completion of a Response from [http4s][http4s], similar to `cats.effect.Bracket` from `cats-effect`.

You might wonder why we can't just use `cats.effect.Bracket`. To answer that question, let's consider the `HttpRoutes` type from `http4s`.

```scala
  type HttpRoutes[F[_]] = Kleisli[OptionT[F, *], Request[F], Response[F]]
```

You _can_ already bracket on this type, assuming your `F` type has an instance of `cats.effect.Bracket` (which it typically will). However, this will only bracket over the generation of the `Response[F]` type, _not_ the complete processing of the `Response.body` `fs2.Stream[Byte]`.

It is this feature which `BracketRequestResponseMiddleware` provides.

This function is very useful for metrics gathering, logging, and controlling concurrency in a http4s system.

Indeed versions of similar functions exist in http4s right now, but they are all specialized to a particular usage, not generalized.

**It is my intent to PR this function into http4s proper. If and when it is merged and released, this repo will be closed.**

[http4s]: https://http4s.org/ "Http4s"
