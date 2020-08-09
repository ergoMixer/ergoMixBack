package db.config

import org.h2.tools.Server
import play.api.Logger

trait DBConfig {
  private val logger: Logger = Logger(this.getClass)
  val dbname:String // e.g. petstore
  val dbuser:String // e.g. user
  val dbpass:String // e.g. pass

  def url = s"jdbc:h2:${System.getProperty("user.home")}/ergoMixer/$dbname"    /*  jdbc:h2:~/somedir/someDb */
    
  def init = {
    try {
      val server = Server.createTcpServer()
      if (! server.isRunning(true)) server.start
    } catch { case any:Throwable => logger.error ("could not start h2 db server") }
    Class.forName("org.h2.Driver")
  }
  init
}
