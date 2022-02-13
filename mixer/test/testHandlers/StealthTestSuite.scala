package testHandlers

import dao.stealth._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Mode}
import services.Module

import java.io.File
import javax.inject.{Inject, Singleton}

@Singleton
class StealthDaoContext @Inject()(val assetDAO: AssetDAO,
                                  val dataInputDAO: DataInputDAO,
                                  val extractedBlockDAO: ExtractedBlockDAO,
                                  val extractionResultDAO: ExtractionResultDAO,
                                  val forkedResultDAO: ForkedResultDAO,
                                  val inputDAO: InputDAO,
                                  val outputDAO: OutputDAO,
                                  val registerDAO: RegisterDAO,
                                  val scanDAO: ScanDAO,
                                  val stealthDAO: StealthDAO,
                                  val transactionDAO: TransactionDAO)


class StealthTestSuite extends AnyPropSpec with should.Matchers with GuiceOneAppPerSuite with BeforeAndAfterAll {
  implicit override lazy val app: Application = new GuiceApplicationBuilder().
    configure(
      "slick.dbs.default.profile" -> "slick.jdbc.H2Profile$",
      "slick.dbs.default.driver" -> "slick.driver.H2Driver$",
      "slick.dbs.default.db.driver" -> "org.h2.Driver",
      "slick.dbs.default.db.url" -> "jdbc:h2:./test/db/scanner",
      "slick.dbs.default.db.user" -> "test",
      "slick.dbs.default.db.password" -> "test",
      "play.evolutions.autoApply" -> true,
      "slick.dbs.default.db.numThreads" -> 20,
      "slick.dbs.default.db.maxConnections" -> 20)
    .in(Mode.Test)
    .disable[Module]
    .build

  protected def stealthDaoContext(implicit app: Application): StealthDaoContext = {
    Application.instanceCache[StealthDaoContext].apply(app)
  }

  override protected def afterAll(): Unit = {
    // delete db after test done.
    new File("./test/db/scanner.mv.db").deleteOnExit()
    new File("./test/db/scanner.trace.db").deleteOnExit()
  }
}
