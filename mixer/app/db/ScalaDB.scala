package db

import db.core.DataStructures._
import db.{DBManager => DBM}
import play.api.db.Database

object ScalaDB {

  object Implicits {
    implicit def strToCol(a: String) = constCol(a)

    implicit def intToCol(a: Int) = constCol(a)

    implicit def boolToCol(a: Boolean) = constCol(a)

    implicit def longToCol(a: Long) = constCol(a)

    implicit def bigIntToCol(a: BigInt) = constCol(a)
  }

  type Max = Int
  type Offset = Long
  type toT[T] = Array[Any] => T
  type DBSelect[T] = (DBManager, Cols, Wheres, Max, Offset, Orderings, toT[T])

  // tables
  type TableName = String
  type PriKey = Cols

  type DBTab = (Database, TableName, Cols, PriKey)


  object Tab {
    def apply(tableName: TableName)(playDb: Database) = new Tab((playDb, tableName, Array[Col](), Array[Col]()))

    def withName(tableName: TableName)(implicit playDb: Database) = Tab(tableName)(playDb)
  }

  case class Tab(dbTab: DBTab) {
    private val (playDb, origTableName, origCols, origPriKey) = dbTab

    def create = DBM(origTableName)(origCols: _*)(origPriKey: _*)(playDb)

    def withCols(cols: Cols) = Tab((playDb, origTableName, origCols ++ cols, origPriKey))

    def withCols(cols: Col*): Tab = withCols(cols.toArray)

    def withPriKey(priKey: PriKey) = Tab((playDb, origTableName, origCols, origPriKey ++ priKey)).create

    def withPriKey(priKey: Col*): DBManager = withPriKey(priKey.toArray)
  }

  case class Sel[T](qry: DBSelect[T]) {
    private val (db, origCols, origWheres, origMax, origOffset, origOrderings, origToT) = qry

    def execute(implicit optConn: OptConn = None) = db.selectCols(origWheres, origCols, origToT)(origOrderings, origMax, origOffset)

    def where(wheres: Wheres) = Sel(db, origCols, origWheres ++ (wheres.filterNot(origWheres.contains)), origMax, origOffset, origOrderings, origToT)

    def where(wheres: Where*): Sel[_] = where(wheres.toArray)

    def select(cols: Cols) = Sel(db, origCols ++ (cols.filterNot(origCols.contains)), origWheres, origMax, origOffset, origOrderings, origToT)

    def select(cols: Col*): Sel[_] = select(cols.toArray)

    def max(max: Int) = Sel(db, origCols, origWheres, max, origOffset, origOrderings, origToT)

    def offset(offset: Long) = Sel(db, origCols, origWheres, origMax, offset, origOrderings, origToT)

    def orderBy(orderings: Orderings) = Sel(db, origCols, origWheres, origMax, origOffset, origOrderings ++ (orderings.filterNot(origOrderings.contains)), origToT)

    def orderBy(orderings: Ordering*): Sel[_] = orderBy(orderings.toArray)

    def as[B](arrayAnyToT: toT[B]) = Sel(db, origCols, origWheres, origMax, origOffset, origOrderings, arrayAnyToT).execute

    def castFirstAs[T] = as(_ (0).as[T])

    def firstAs[T](anyToT: Any => T) = as(a => anyToT(a(0)))

    def firstAsT[T] = as(a => a(0).asInstanceOf[T])

    def asList = Sel[List[Any]](db, origCols, origWheres, origMax, origOffset, origOrderings, _.toList).execute

    def into(otherDB: DBManager) =
      db.selectInto(otherDB, origWheres, origCols)(origOrderings, origMax, origOffset)

    def into(otherDB: ScalaDB) =
      db.selectInto(otherDB.db, origWheres, origCols)(origOrderings, origMax, origOffset)

  }

  type DBIncr = (DBManager, Increments, Wheres)

  case class Inc(qry: DBIncr) {
    private val (db, origIncrs, origWheres) = qry

    def execute(implicit optConn: OptConn = None) = db.incrementColsTxWithLastValue(origWheres, origIncrs)

    def increment(increments: Increments) = Inc(db, origIncrs ++ (increments.filterNot(origIncrs.contains)), origWheres)

    def increment(increments: Increment*): Inc = increment(increments.toArray)

    def where(wheres: Wheres) = Inc(db, origIncrs, origWheres ++ (wheres.filterNot(origWheres.contains))).execute

    def where(wheres: Where*): (Int, Array[Any]) = where(wheres.toArray)
  }

  type DBUpd = (DBManager, Updates[Any], Wheres)

  case class Upd(qry: DBUpd) {
    private val (db, origUpds, origWheres) = qry

    def execute(implicit optConn: OptConn = None) = db.updateCols(origWheres, origUpds)

    def update(updates: Updates[Any]) = Upd(db, origUpds ++ (updates.filterNot(origUpds.contains)), origWheres)

    def update(updates: Update[Any]*): Upd = update(updates.toArray)

    def where(wheres: Wheres) = Upd(db, origUpds, origWheres ++ (wheres.filterNot(origWheres.contains))).execute

    def where(wheres: Where*): Int = where(wheres.toArray)
  }

  class ScalaDB(val db: DBManager) {
    def name = db.getTable.tableName

    def select(cols: Col*): Sel[_] = select(cols.toArray)

    def select(cols: Cols) = Sel(db, cols, Array(), Int.MaxValue, 0, Array(), (a: Array[Any]) => a.toList)

    def selectStar = Sel(db, db.getTable.tableCols, Array(), Int.MaxValue, 0, Array(), (a: Array[Any]) => a.toList)

    def deleteWhere(wheres: Where*)(implicit optConn: OptConn = None) = db.delete(wheres.toArray)

    def countWhere(wheres: Where*) = db.countRows(wheres.toArray)

    def increment(increments: Increment*) = Inc(db, increments.toArray, Array())

    def update(updates: Update[Any]*): Upd = update(updates.toArray)

    def update(updates: Updates[Any]) = Upd(db, updates, Array())

    def insert(anys: Any*) = {
      anys.size match {
        case 1 =>
          anys(0) match {
            case _: Array[Byte] => db.insertArray(anys.toArray)
            case a: Array[_] => db.insertArray(a.asInstanceOf[Array[Any]])
            case _ => db.insertArray(anys.toArray)
          }
        case _ => db.insertArray(anys.toArray)
      }
    }
  }

  implicit def dbToDB(s: DBManager) = new ScalaDB(s)

}
