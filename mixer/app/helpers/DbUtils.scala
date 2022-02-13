package helpers

import helpers.ErrorHandler.NotFoundException
import play.api.db.slick.HasDatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

trait DbUtils { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  /**
  * exec a dbio query as transactionally
   * @param dbio DBIO[T]
   * @tparam T Any
   * @return
   */
  def execTransact[T](dbio: DBIO[T]): Future[T] =
    db.run(dbio.transactionally)

  def execAwait[T](dbio: DBIO[T]): T = {
    val query = db.run(dbio)
    Await.result(query, Duration.Inf)
  }

  /**
  * exception handling for get any object from db
   * @param inp
   * @tparam T
   * @return
   */
  def notFoundHandle[T](inp: Try[Option[T]]): T = {
    inp.toEither match {
      case Right(Some(result)) => result
      case Right(None) =>  throw NotFoundException()
      case Left(ex) => throw ex
    }
  }

}
