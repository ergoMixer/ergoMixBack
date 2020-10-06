package helpers

import java.io._
import java.net.{Authenticator, InetSocketAddress, PasswordAuthentication, Proxy}
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import app.Configs
import cli.MixUtils
import mixer.BlockExplorer
import play.api.Logger

object ErgoMixerUtils {
  private val logger: Logger = Logger(this.getClass)

  // The minimum number of confirmations for a current mix transaction before proceeding to next step
  val minConfirmations: Int = Configs.numConfirmation

  def getFee(tokenId: String, tokenAmount: Long, ergAmount: Long, isFull: Boolean): Long = {
    if (tokenId.nonEmpty) {
      if (isFull) Configs.defaultFullTokenFee
      else Configs.defaultHalfTokenFee

    } else {
      if (isFull) Configs.defaultFullFee
      else Configs.defaultHalfFee
    }
  }

  def getStackTraceStr(e: Throwable): String = {
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    e.printStackTrace(pw)
    sw.toString
  }

  def isDoubleSpent(boxId: String, wrtBoxId: String): Boolean = MixUtils.usingClient { implicit ctx =>
    val explorer: BlockExplorer = new BlockExplorer()
    explorer.getSpendingTxId(boxId).flatMap { txId =>
      // boxId has been spent, while the fullMixBox generated has zero confirmations. Looks like box has been double-spent elsewhere
      explorer.getTransaction(txId).map { tx =>
        // to be sure, get the complete tx and check that none if its outputs are our fullMixBox
        !tx.outboxes.map(_.id).contains(wrtBoxId)
      }
    }
  }.getOrElse(false)

  def getRandomValidBoxId(origBoxIds: Seq[String]) = MixUtils.usingClient { implicit ctx =>
    val random = new SecureRandom()
    val boxIds = new scala.util.Random(random).shuffle(origBoxIds)
    boxIds.find { boxId =>
      try {
        ctx.getBoxesById(boxId)
        true
      } catch {
        case a: Throwable =>
          logger.error(s"      Error reading boxId ${boxId}: " + a.getMessage)
          false
      }
    }
  }

  def getRandom(seq: Seq[String]) = MixUtils.usingClient { implicit ctx =>
    val random = new SecureRandom()
    new scala.util.Random(random).shuffle(seq).headOption
  }

  def prettyDate(timestamp: Long): String = {
    new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(new Date(timestamp))
  }

  def handleProxy(url: String, port: Int, protocol: String): Unit = {
    if (url.nonEmpty && port != -1 && protocol.nonEmpty) {
      val prot: Proxy.Type = {
        if (protocol.toLowerCase().contains("socks")) Proxy.Type.SOCKS
        else if (protocol.toLowerCase().contains("http")) Proxy.Type.HTTP
        else null
      }
      if (prot == null) {
        logger.error("protocol type for proxy is not valid.")
        return
      }
      Configs.proxy = new Proxy(prot, new InetSocketAddress(url, port))
    }
  }

  def backup(): String = {
    val path = System.getProperty("user.home") + "/ergoMixer"
    val zip = new File(path + "/ergoMixerBackup.zip")
    if (zip.exists()) zip.delete()
    val toZip = Seq(s"$path/database.mv.db", s"$path/database.trace.db")
    val buf = new Array[Byte](2048)
    val out = new ZipOutputStream(new FileOutputStream(zip))
    toZip.map(name => new File(name)).foreach(file => {
      val in = new FileInputStream(file.getAbsolutePath)
      out.putNextEntry(new ZipEntry(file.getName))
      var len = in.read(buf)
      while (len > 0) {
        out.write(buf, 0, len)
        len = in.read(buf)
      }
      out.closeEntry()
      in.close()
    })
    out.close()
    zip.getAbsolutePath
  }

  def restore(): Unit = {
    val path = System.getProperty("user.home") + "/ergoMixer"
    val zip = new File(path + "/ergoMixerRestore.zip")
    if (zip.exists() && zip.length() > 0) {
      val buf = new Array[Byte](2048)
      val in = new ZipInputStream(new FileInputStream(zip))
      var zipEntry = in.getNextEntry
      while (zipEntry != null) {
        val nf = new File(path, zipEntry.getName)
        if (nf.exists()) nf.delete()
        val fos = new FileOutputStream(nf)
        var len = in.read(buf)
        while (len > 0) {
          fos.write(buf, 0, len)
          len = in.read(buf)
        }
        fos.close()
        zipEntry = in.getNextEntry
      }
      in.closeEntry()
      zip.delete()

    } else {
      throw new Exception("No uploaded backup found")
    }
  }
}
