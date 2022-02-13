package dao.stealth

import play.api.Logger

import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import helpers.DbUtils

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

class ForkedResultDAO @Inject() (extractedBlockDAO: ExtractedBlockDAO, transactionDAO: TransactionDAO, inputDAO: InputDAO, outputDAO: OutputDAO, assetDAO: AssetDAO, registerDAO: RegisterDAO, protected val dbConfigProvider: DatabaseConfigProvider) (implicit executionContext: ExecutionContext)
        extends DbUtils with HasDatabaseConfigProvider[JdbcProfile] {

    /**
     * Migrate blocks from a detected fork to alternate tables.
     * @param height height of block to be migrated
     * */
    def migrateBlockByHeight(height: Int): Unit = {

        val action = for {
            headerId <- extractedBlockDAO.getHeaderIdByHeightQuery(height)
            _ <- extractedBlockDAO.migrateForkByHeaderId(notFoundHandle(headerId))
            _ <- transactionDAO.migrateForkByHeaderId(notFoundHandle(headerId))
            _ <- outputDAO.migrateForkByHeaderId(notFoundHandle(headerId))
            _ <- assetDAO.migrateForkByHeaderId(notFoundHandle(headerId))
            boxIds <- outputDAO.getBoxIdsByHeaderIdQuery(notFoundHandle(headerId))
            _ <- registerDAO.migrateForkByBoxIds(boxIds)
            _ <- inputDAO.migrateForkByHeaderId(notFoundHandle(headerId))
        } yield {

        }
        val response = execTransact(action)
        Await.result(response, Duration.Inf)
    }
}
