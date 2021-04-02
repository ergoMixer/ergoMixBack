package helpers

import java.io._
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import app.Configs
import io.circe.Json
import javax.inject.Inject
import network.NetworkUtils
import play.api.Logger
import sigmastate.SType
import sigmastate.lang.SigmaParser

class ErgoMixerUtils @Inject()(networkUtils: NetworkUtils) {
  private val logger: Logger = Logger(this.getClass)

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

  def getRandomValidBoxId(origBoxIds: Seq[String]): Option[String] = networkUtils.usingClient { implicit ctx =>
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

  def getRandom(seq: Seq[String]) = networkUtils.usingClient { implicit ctx =>
    val random = new SecureRandom()
    new scala.util.Random(random).shuffle(seq).headOption
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
