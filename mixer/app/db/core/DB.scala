package db.core

import java.sql.Connection

import db.config._
import snaq.db.ConnectionPool

case class DB(c:DBConfig) {
  lazy val pool = new ConnectionPool("poolname", 1, 200, 300, 180000, c.url, c.dbuser, c.dbpass)

  var connection:Connection = _
  def getConnection = pool.getConnection(3000)
}


