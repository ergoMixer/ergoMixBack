package db.core

import DataStructures._
import Util._

case class Table(tableName:String, tableCols:Cols, priKey:Cols) {
  import Table._
  if (Col.reservedNames.contains(tableName.toUpperCase)) throw new Exception(s"Table name '$tableName' is a reserved word")

  val colNames = tableCols.map(_.name.toUpperCase) 
  val priKeyColNames = priKey.map(_.name.toUpperCase) 
  if (colNames.distinct.size != colNames.size) throw new Exception(s"Columns contain duplicates in table $tableName. Rename some cols and retry.")
  if (priKeyColNames.distinct.size != priKeyColNames.size) throw new Exception("Primary keys contain duplicates.")

  if (!priKeyColNames.forall(colNames.contains)) throw new Exception(s"One or more primary keys not present in table $tableName.")
  val autoIncrCols = tableCols.filter(_.colType == ULONGAuto)
  if (autoIncrCols.size > 1) throw new Exception(s"At most one column can be Auto-Increment. Currently: ${autoIncrCols.size} in table $tableName")

  val autoIncrementCol = autoIncrCols.headOption
  
  def this(tableName:String, col:Col, priKey:Col) = this(tableName, Array(col), Array(priKey))

  override def toString = tableName

  def assertColExists(c:Col) = c.compositeCols.foreach{col =>
    if (!containsCol(col)) throw new Exception(s"Table ${col.optTable.getOrElse(tableName)} does not have column ${col.name}")
  }
  private def containsCol(anyCol:Col) = {
    anyCol.colType match {
      case ULONGCOMPOSITE|CASTTYPE(_) => true
      case CONST(_, _) => true
      case _ => anyCol.optTable.getOrElse(this).tableCols.contains(anyCol.simpleCol)
    }    
  }
  
  protected [db] def createSQLString = "CREATE TABLE IF NOT EXISTS "+tableName+
    " ("+tableCols.map(f => f.name+" "+colTypeString(f.colType)).reduceLeft(_+", "+_)+priKeyString+checkString+")"
  
  private def checkString = tableCols.filter(
    _.colType match {
      case ULONG(_) | UINT(_) | UBIGDEC(_, _) => true
      case _ => false
    }
  ) match {
    case f if f.size > 0 => ","+f.map("CHECK("+_.name+" >= 0)").reduceLeft(_+","+_) 
    case _ => ""
  }
  private def priKeyString = if (priKey == null || priKey.size == 0) "" else ", PRIMARY KEY ("+priKey.map(_.name).reduceLeft(_+", "+_)+")"

  def insertSQLString = {
    val (actualCols, qMark) = tableCols.collect{
      case Col(name, ULONGAuto, _) => None //(name, "?")
      case Col(name, _, _) => Some((name, "?"))
    }.flatten.unzip
    "INSERT INTO "+tableName+"("+actualCols.reduceLeft{_+","+_}+")"+
    " VALUES("+ qMark.reduceLeft(_+", "+_)+ ")"
  }

  def dropSQLString = "DROP TABLE "+tableName

  def deleteSQLString(search:Wheres) = "DELETE FROM "+tableName+whereString(search, "WHERE", "and")

  def selectSQLString(selectCols:Cols, wheres:Wheres)(implicit orderings:Orderings=Array(), limit:Int = 0, offset:Long = 0) =
    "SELECT "+getSelectString(selectCols)+
    " FROM "+getTableNames(selectCols, wheres)+whereString(wheres, "WHERE", "and")+getOrderLimitSQLString(orderings,limit, offset)

  def insertIntoSQLString(table:Table, selectCols:Cols, wheres:Wheres)(implicit orderings:Orderings=Array(), limit:Int = 0, offset:Long = 0) =
    "INSERT INTO "+table.tableName+" "+selectSQLString(selectCols:Cols, wheres:Wheres)

  def getTableNames(selectCols:Cols, wheres:Wheres) = {
    val selectTables = selectCols.flatMap(_.compositeTableNames).toSet
    
    val whereTables = wheres.flatMap(_.col.compositeTableNames).toSet 
    val dataTables = wheres.collect{
      case Where(_, _, col:Col) => col.compositeTableNames
    }.flatten.toSet

    val allTables =   (selectTables ++ whereTables ++ dataTables + tableName)
    allTables.reduceLeft(_+","+_)
  }

  def countSQLString(wheres:Wheres) = "SELECT COUNT(*) FROM "+ getTableNames(Array(), wheres) + whereString(wheres, "WHERE", "and")
  
  def updateSQLString[T](wheres:Wheres, updates:Updates[T])(implicit toWhere:Update[_] => Where = upd => Where(upd.col, Eq, upd.data)) = {
    if (updates.size < 1) throw new Exception("Updates size must be >= 1")
    "UPDATE "+tableName+whereString(updates.map(toWhere), "SET", ",")+whereString(wheres, "WHERE", "and")
  }
  
  def incrementColsString(wheres:Wheres, increments:Increments) =
    updateSQLString(wheres, increments)(increment => Where(increment.col, IncrOp(increment), increment.data)) 

}

object Table {
  
  def apply(tableName:String, col:Col, priKey:Col) = new Table(tableName, Array(col), Array(priKey))
  def apply(tableName:String, tableCols:Cols) = new Table(tableName, tableCols, Array[Col]())
  def apply(tableName:String, tableCols:Col*):Table = apply(tableName, tableCols.toArray)

  private def getOrdering(isDescending:Boolean) = if (isDescending) " DESC" else ""
  private def getOrderStr(orderings:Orderings) = {
    assert(orderings.size > 0)      // "ORDER BY supplier_city DESC, supplier_state ASC;"
    " ORDER BY "+orderings.map{
      x => x.col.colSQLString+getOrdering(x.isDescending)
    }.reduceLeft(_+","+_)
  }
  private def getOrderLimitSQLString(orderings:Orderings, limit:Int, offset:Long) = {
    (if (orderings.size == 0) "" else getOrderStr(orderings)) + 
    (if (limit == 0) "" else " LIMIT "+limit) +
    (if (offset == 0) "" else " OFFSET "+offset)
  }
  private def whereString(wheres:Wheres, str:String, joinOp:String) = if (wheres.size == 0) "" else {
     " "+str+" "+wheres.map{_.whereSQLString}.reduceLeft(_+" "+joinOp+" "+_)
  }

  private def getSelectString(cols:Cols) = if (cols.size == 0) "*" else cols.map(col => col.colSQLString +" AS "+col.alias).reduceLeft(_+","+_)

}
