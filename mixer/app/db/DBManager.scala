package db

import java.sql.{ResultSet => RS}

import db.config.DBConfig
import db.core.DataStructures.{Col, Where, _}
import db.core.Util._
import db.core.{Table, _}

object DBManager {

  val noCols = Array[Col]()

  def apply(name:String)(cols:Col *)(priCols:Col *)(implicit config:DBConfig) = new DBManager(Table(name, cols.toArray, priCols.toArray))(config)
  def apply(name:String, cols:Cols, priCols:Cols)(implicit config:DBConfig):DBManager = apply(name)(cols: _ *)(priCols: _ *)(config)
  def apply(name:String, cols:Cols, priCol:Col)(implicit config:DBConfig):DBManager = apply(name, cols, Array(priCol))(config)
  def apply(name:String, cols:Cols)(implicit config:DBConfig):DBManager = apply(name, cols, noCols)(config)

  def apply(table:Table)(implicit config:DBConfig) = new DBManager(table)(config)

}

/* Database functionality for making the following SQL queries: SELECT, DELETE, INSERT and UPDATE */
class DBManager(private[db] val table:Table)(implicit private[db] val dbConfig:DBConfig)
  extends DBManagerDML(table:Table, dbConfig:DBConfig) {

  private [db] implicit val dbDML:DBManagerDML = this
  import table._

  private [db] def getTable = table
  private [db] def getConfig = dbConfig

  private lazy val db = new DB(dbConfig)
  def getConnection = db.getConnection

  private [db] def insertRS[T](rs:RS) = bmap(rs.next)(insertArray(getTable.tableCols.map(get(rs, _)))).size
  private [db] def exists(wheres:Wheres) = countRows(wheres) >= 1
  def exists(wheres:Where*):Boolean = exists(wheres.toArray)
  private [db] def isNonEmpty = exists()
  private [db] def isEmpty = !isNonEmpty


  def deleteAll:Int = delete(Array()) // deletes all rows from the table. returns the number of rows deleted (Int)

  private [db] def countRows(where:Where):Long = countRows(Array(where))

  private [db] def countAllRows = countRows(Array[Where]())

  private [db] def incrementColTx(where:Where, increment:Increment) = incrementColsTx(Array(where), Array(increment))

  private [db] def updateCol(where:Where, update:Update[Any]):Int = updateCols(Array(where), Array(update))

  private [db] def insertList(data:List[Any]):Long = insertArray(data.toArray)

  // initialize table
  createTableIfNotExists // create table if it does not exist

  private def dropTable:Int = using(getConnection) { conn => using(conn.prepareStatement(dropSQLString)){ st => st.executeUpdate} }

  def createTableIfNotExists = using(getConnection){
    conn => using(conn.prepareStatement(createSQLString))(_.executeUpdate)
  }

}



   
   
   
   
   
   
   
   
   
   
   
   
   
   



