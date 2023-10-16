package helpers

import java.io._
import java.security.SecureRandom
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}
import java.util.Date
import javax.inject.{Inject, Singleton}

import config.MainConfigs
import dao.DAOUtils
import network.NetworkUtils
import play.api.Logger

@Singleton
class ErgoMixerUtils @Inject() (daoUtils: DAOUtils, networkUtils: NetworkUtils) {
  private val logger: Logger = Logger(this.getClass)

  def currentDateTimeString(pattern: String = "yyyy-MM-dd'T'HH-mm-ss"): String = {
    val date      = new Date()
    val formatter = new SimpleDateFormat(pattern)
    formatter.format(date)
  }

  def getBegOfDayTimestamp(timestamp: Long): Long = {
    val startOfDay = new Timestamp(timestamp).toLocalDateTime.toLocalDate.atStartOfDay()
    Timestamp.valueOf(startOfDay).getTime
  }

  def getFee(tokenId: String, isFull: Boolean): Long =
    if (tokenId.nonEmpty) {
      if (isFull) MainConfigs.defaultFullTokenFee
      else MainConfigs.defaultHalfTokenFee

    } else {
      if (isFull) MainConfigs.defaultFullFee
      else MainConfigs.defaultHalfFee
    }

  def getStackTraceStr(e: Throwable): String = {
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    e.printStackTrace(pw)
    sw.toString
  }

  def getRandomValidBoxId(origBoxIds: Seq[String]): Option[String] = {
    val random = new SecureRandom()
    val boxIds = new scala.util.Random(random).shuffle(origBoxIds)
    boxIds.find { boxId =>
      try {
        networkUtils.getUnspentBoxById(boxId)
        true
      } catch {
        case a: Throwable =>
          logger.error(s"      Error reading boxId $boxId: " + a.getMessage)
          false
      }
    }
  }

  def getRandom(seq: Seq[String]): Option[String] = {
    val random = new SecureRandom()
    new scala.util.Random(random).shuffle(seq).headOption
  }

  def backup(): String = {
    val (dbUrl, baseDbUrl) = daoUtils.getDbUrl
    val zip                = new File(baseDbUrl + s"ergoMixerBackup-${currentDateTimeString()}.zip")
    if (zip.exists()) zip.delete()
    val toZip = Seq(s"$dbUrl.mv.db", s"$dbUrl.trace.db")
    val buf   = new Array[Byte](2048)
    val out   = new ZipOutputStream(new FileOutputStream(zip))
    toZip.map(name => new File(name)).foreach { file =>
      val in = new FileInputStream(file.getAbsolutePath)
      out.putNextEntry(new ZipEntry(file.getName))
      var len = in.read(buf)
      while (len > 0) {
        out.write(buf, 0, len)
        len = in.read(buf)
      }
      out.closeEntry()
      in.close()
    }
    out.close()
    zip.getAbsolutePath
  }

  def restore(): Unit = {
    val (_, baseDbUrl) = daoUtils.getDbUrl
    val zip            = new File(baseDbUrl + "ergoMixerRestore.zip")
    if (zip.exists() && zip.length() > 0) {
      val buf      = new Array[Byte](2048)
      val in       = new ZipInputStream(new FileInputStream(zip))
      var zipEntry = in.getNextEntry
      while (zipEntry != null) {
        val nf = new File(baseDbUrl, zipEntry.getName)
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
