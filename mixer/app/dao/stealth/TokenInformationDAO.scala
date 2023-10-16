package dao.stealth

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import models.StealthModels.TokenInformation
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

trait TokenInformationComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class TokenInformationTable(tag: Tag) extends Table[TokenInformation](tag, "TOKEN_INFORMATION") {
    def id = column[String]("ID", O.PrimaryKey)

    def name = column[String]("NAME")

    def decimals = column[Int]("DECIMALS")

    def * = (id, name.?, decimals.?) <> (TokenInformation.tupled, TokenInformation.unapply)
  }
}

@Singleton()
class TokenInformationDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext
) extends TokenInformationComponent
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val tokenQuery = TableQuery[TokenInformationTable]

  /**
   * inserts a token into db
   *
   * @param token TokenInformation
   */
  def insert(token: TokenInformation): Future[Unit] = db.run(tokenQuery += token).map(_ => ())

  /**
   * inserts list of tokens into db
   *
   * @param tokens Seq[TokenInformation]
   */
  def inserts(tokens: Seq[TokenInformation]): Future[Unit] = db.run(tokenQuery ++= tokens).map(_ => ())

  /**
   * check exist tokenId in db or not
   *
   * @param tokenId String
   * @return true of exist a TokenInformation with this tokenId
   */
  def existsByTokenId(tokenId: String): Future[Boolean] =
    db.run(tokenQuery.filter(token => token.id === tokenId).exists.result)

  /**
   * update token's information
   *
   * @param tokenInformation - TokenInformation
   */
  def updateToken(tokenInformation: TokenInformation): Future[Unit] = {
    val query = tokenQuery
      .filter(req => req.id === tokenInformation.id)
      .map(token => (token.name, token.decimals))
      .update((tokenInformation.name.getOrElse("No Name"), tokenInformation.decimals.getOrElse(0)))
    db.run(query).map(_ => ())
  }

  /**
   * select token by given tokenId
   *
   * @param tokenIds Seq[String]
   * @return Option of TokenInformation if exist
   */
  def selectByTokenIds(tokenIds: Seq[String]): Future[Seq[TokenInformation]] =
    db.run(tokenQuery.filter(req => req.id.inSet(tokenIds)).result)

  /**
   * select tokens doesn't have decimal and tokenName
   *
   * @return Seq[TokenInformation]
   */
  def selectWithoutInformationTokens(): Future[Seq[TokenInformation]] =
    db.run(tokenQuery.filter(token => token.decimals.?.isEmpty && token.name.?.isEmpty).result)
}
