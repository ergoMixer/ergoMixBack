package testHandlers

import dao._

import dao.stealth.{AssetDAO, DataInputDAO, ExtractedBlockDAO, ExtractionResultDAO, ForkedResultDAO, InputDAO, OutputDAO, RegisterDAO, ScanDAO, StealthDAO, TransactionDAO}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Mode}
import services.Module

import spire.ClassTag

import java.io.File
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@Singleton
class DaoContext @Inject()(val allDepositsDAO: AllDepositsDAO,
                           val emissionDAO: EmissionDAO,
                           val mixStateDAO: MixStateDAO,
                           val tokenEmissionDAO: TokenEmissionDAO,
                           val allMixDAO: AllMixDAO,
                           val fullMixDAO: FullMixDAO,
                           val mixStateHistoryDAO: MixStateHistoryDAO,
                           val unspentDepositsDAO: UnspentDepositsDAO,
                           val covertAddressesDAO: CovertAddressesDAO,
                           val halfMixDAO: HalfMixDAO,
                           val mixTransactionsDAO: MixTransactionsDAO,
                           val withdrawDAO: WithdrawDAO,
                           val covertDefaultsDAO: CovertDefaultsDAO,
                           val mixingCovertRequestDAO: MixingCovertRequestDAO,
                           val mixingGroupRequestDAO: MixingGroupRequestDAO,
                           val rescanDAO: RescanDAO,
                           val distributeTransactionsDAO: DistributeTransactionsDAO,
                           val mixingRequestsDAO: MixingRequestsDAO,
                           val spentDepositsDAO: SpentDepositsDAO,
                           val withdrawCovertTokenDAO: WithdrawCovertTokenDAO,
                           val hopMixDAO: HopMixDAO,
                           val covertsDAO: CovertsDAO
                          )


@Singleton
class StealthDaoContext @Inject()(
                                  val assetDAO: AssetDAO,
                                  val dataInputDAO: DataInputDAO,
                                  val extractedBlockDAO: ExtractedBlockDAO,
                                  val extractionResultDAO: ExtractionResultDAO,
                                  val forkedResultDAO: ForkedResultDAO,
                                  val inputDAO: InputDAO,
                                  val outputDAO: OutputDAO,
                                  val registerDAO: RegisterDAO,
                                  val scanDAO: ScanDAO,
                                  val stealthDAO: StealthDAO,
                                  val transactionDAO: TransactionDAO
                                )


class TestSuite extends AnyPropSpec with should.Matchers with GuiceOneAppPerSuite with BeforeAndAfterAll {
  implicit override lazy val app: Application = new GuiceApplicationBuilder().
    configure(
      "slick.dbs.default.driver" -> "slick.driver.H2Driver$",
      "slick.dbs.default.db.driver" -> "org.h2.Driver",
      "slick.dbs.default.db.url" -> "jdbc:h2:./test/db/database",
      "slick.dbs.default.db.user" -> "test",
      "slick.dbs.default.db.password" -> "test")
    .in(Mode.Test)
    .disable[Module]
    .build

  protected def daoContext(implicit app: Application): DaoContext = {
    Application.instanceCache[DaoContext].apply(app)
  }

  protected def stealthDaoContext(implicit app: Application): StealthDaoContext = {
    Application.instanceCache[StealthDaoContext].apply(app)
  }

  override protected def afterAll(): Unit = {
    // delete db after test done.
    new File("./test/db/database.mv.db").deleteOnExit()
    new File("./test/db/database.trace.db").deleteOnExit()
  }
}
