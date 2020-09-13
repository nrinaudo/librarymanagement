package sbt.internal.librarymanagement

import java.io.IOException
import java.net.{ SocketException, SocketTimeoutException }

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

private[sbt] object IvyUtil {
  def separate[A, B](l: Seq[Either[A, B]]): (Seq[A], Seq[B]) =
    (l.flatMap(_.left.toOption), l.flatMap(_.right.toOption))

  @tailrec
  final def retryWithBackoff[T](
      f: => T,
      predicate: Throwable => Boolean,
      maxAttempts: Int,
      retry: Int = 0
  ): T = {
    // Using Try helps in catching NonFatal exceptions only
    Try {
      f
    } match {
      case Success(value)                                          => value
      case Failure(e) if predicate(e) && retry < (maxAttempts - 1) =>
        // max 8s backoff
        val backoff = math.min(math.pow(2d, retry.toDouble).toLong * 1000L, 8000L)
        Thread.sleep(backoff)
        retryWithBackoff(f, predicate, maxAttempts, retry + 1)
      case Failure(e) => throw e
    }
  }

  object TransientNetworkException {
    private val _r = """.*HTTP response code: (503|429).*""".r

    @inline private def check(s: String): Boolean = {
      if (s == null) return false

      _r.pattern.matcher(s).matches()
    }

    def apply(t: Throwable): Boolean = t match {
      case _: SocketException | _: SocketTimeoutException => true
      case e: IOException if check(e.getMessage)          => true
      case _                                              => false
    }
  }

}
