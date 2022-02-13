package dao

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import spire.ClassTag

import java.util.concurrent.Executors
import javax.inject.Inject
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class DAOUtils @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends HasDatabaseConfigProvider[JdbcProfile] {

    def awaitResult[T: ClassTag](variable: Future[T]): T = Await.result(variable, Duration.Inf)

    def shutdown = {
        db.close()
        implicit val context = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
        Future {
            Thread.sleep(10000)
            System.exit(0)
        }
    }
}
