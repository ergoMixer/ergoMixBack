package db

import java.sql.{ResultSet => RS}

import db.core.DataStructures.{Col, Where, _}
import db.core.Util._
import db.core.{Table, _}
import play.api.db.Database

object DBManager {

  val noCols = Array[Col]()

  def apply(name: String)(cols: Col*)(priCols: Col*)(playDb: Database) = new DBManager(Table(name, cols.toArray, priCols.toArray))(playDb)

  def apply(name: String, cols: Cols, priCols: Cols)(playDb: Database): DBManager = apply(name)(cols: _ *)(priCols: _ *)(playDb)

  def apply(name: String, cols: Cols, priCol: Col)(playDb: Database): DBManager = apply(name, cols, Array(priCol))(playDb)

  def apply(name: String, cols: Cols)(playDb: Database): DBManager = apply(name, cols, noCols)(playDb)

  def apply(table: Table)(playDb: Database) = new DBManager(table)(playDb)

}

/* Database functionality for making the following SQL queries: SELECT, DELETE, INSERT and UPDATE */
class DBManager(private[db] val table: Table)(implicit private[db] val playDb: Database)
  extends DBManagerDML(table: Table) {

  private[db] implicit val dbDML: DBManagerDML = this

  import table._

  private[db] def getTable = table

  def getConnection = playDb.getConnection

  private[db] def insertRS[T](rs: RS) = bmap(rs.next)(insertArray(getTable.tableCols.map(get(rs, _)))).size

  private[db] def exists(wheres: Wheres) = countRows(wheres) >= 1

  def exists(wheres: Where*): Boolean = exists(wheres.toArray)

  private[db] def isNonEmpty = exists()

  private[db] def isEmpty = !isNonEmpty


  def deleteAll: Int = delete(Array()) // deletes all rows from the table. returns the number of rows deleted (Int)

  private[db] def countRows(where: Where): Long = countRows(Array(where))

  private[db] def countAllRows = countRows(Array[Where]())

  private[db] def incrementColTx(where: Where, increment: Increment) = incrementColsTx(Array(where), Array(increment))

  private[db] def updateCol(where: Where, update: Update[Any]): Int = updateCols(Array(where), Array(update))

  private[db] def insertList(data: List[Any]): Long = insertArray(data.toArray)

  // initialize table
//  createTableIfNotExists // create table if it does not exist

  private def dropTable: Int = using(getConnection) { conn => using(conn.prepareStatement(dropSQLString)) { st => st.executeUpdate } }

  def createTableIfNotExists = using(getConnection) {
    conn => using(conn.prepareStatement(createSQLString))(_.executeUpdate)
  }

}



   
   
   
   
   
   
   
   
   
   
   
   
   
   



