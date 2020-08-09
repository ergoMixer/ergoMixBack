package db.config

import org.h2.tools.Server

trait DBConfig {
  val dbname:String // e.g. petstore
  val dbuser:String // e.g. user
  val dbpass:String // e.g. pass

  def url = s"jdbc:h2:${System.getProperty("user.home")}/h2db/$dbname"    /*  jdbc:h2:~/somedir/someDb */
    
  def init = {
    try {
      val server = Server.createTcpServer()
      if (! server.isRunning(true)) server.start
    } catch { case any:Throwable => println ("could not start h2 db server") }
    Class.forName("org.h2.Driver")
  }
  init
}
