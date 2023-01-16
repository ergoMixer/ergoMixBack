package dao

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import spire.ClassTag

import java.util.concurrent.Executors
import javax.inject.Inject
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

class DAOUtils @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends HasDatabaseConfigProvider[JdbcProfile] {

    def awaitResult[T: ClassTag](variable: Future[T]): T = Await.result(variable, Duration.Inf)

    def shutdown(shutdownSystem: Boolean = false): Future[Unit] = {
        db.close()
        implicit val context: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
        Future {
            Thread.sleep(10000)
            if (shutdownSystem) Runtime.getRuntime.halt(0)
        }
    }

    /**
     * a function for get db url path
     * @return (absolute path with db file, db directory path )
     */
    def getDbUrl: (String, String) = {
        val mainUrl = dbConfig.config.getString("db.url").split(":").last.replace("~", System.getProperty("user.home"))
        val dbNameIndex = mainUrl.lastIndexOf("/") + 1
        
        (mainUrl, mainUrl.slice(0, dbNameIndex))
    }

}
