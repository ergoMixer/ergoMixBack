package db

import java.sql.{Connection, PreparedStatement, ResultSet, Statement}

import db.core.DataStructures.{Col, Where, _}
import db.core.Table
import db.core.Util._

private[db] abstract class DBManagerDML(table: Table) {
  def using[A <: {def close(): Unit}, B](param: A)(f: A => B): B = try {
    f(param)
  } finally {
    param.close()
  }

  import scala.collection.mutable.ListBuffer

  def usingOpt[A <: {def close(): Unit}, B](optA: Option[A], getA: => A)(f: A => B): B = {
    optA match {
      case Some(a) =>
        f(a) // dont close.
      case _ =>
        val a = getA
        try f(a) finally a.close
    }
  }

  protected def getConnection: Connection

  import table._

  // 'canonical' methods map cols without table names (i.e., from this table) to cols with table names
  // e.g. if this table is T1 then cols in selectCols without optTable.isDefined will have optTable changed to T1.
  private def canonical(c: Col): Col = c.to(table)

  private def canonical(cols: Cols): Cols = cols.map(canonical)

  private def canonical(w: Where): Where = w.to(table)

  private def canonical(wheres: Wheres): Wheres = wheres.map(canonical)

  private def canonical(o: Ordering): Ordering = Ordering(canonical(o.col), o.isDescending)

  private def canonical(orderings: Orderings): Orderings = orderings.map(canonical)

  private def canonical[T](u: Update[T]): Update[T] = Update(u.col, u.data.to(table).asInstanceOf[T])

  private def canonical[T](updates: Updates[T]): Updates[T] = updates.map(update => canonical[T](update))

  private[db] def setData(data: Array[(Any, DataType)], startCtr: Int, st: PreparedStatement) = {
    var setCtr = startCtr
    data.foreach {
      case (data, dataType) =>
        setCtr += 1
        set(setCtr, st, data, dataType)
    }
    setCtr
  }

  private[db] def insertArray(data: Array[Any])(implicit optConn: OptConn = None) = {
    val actualDataCols = tableCols.filter {
      case Col(_, ULONGAuto, _) => false
      case Col(_, _, _) => true
    }
    val returnAutoIncrementIndex = if (actualDataCols.size == tableCols.size) false else true
    if (data.size != actualDataCols.size) throw new Exception(s"Schema mismatch for table $table. Expected: ${actualDataCols.size}, found: ${data.size}. [AutoIncrment columns should not have data]")
    usingOpt(optConn, getConnection) { conn =>
      using(
        { // ref above link
          if (returnAutoIncrementIndex) conn.prepareStatement(insertSQLString, Statement.RETURN_GENERATED_KEYS)
          else conn.prepareStatement(insertSQLString)
        }
      ) {
        st => {
          setData(data zip actualDataCols.map(_.colType), 0, st)
          val ret = st.executeUpdate()
          if (returnAutoIncrementIndex) {
            val genKey = st.getGeneratedKeys
            if (genKey.next) {
              genKey.getLong(1)
            } else ret
          } else ret
        }
      }
    }
  }

  private[db] def updateCols(wheres: Wheres, updates: Updates[Any])(implicit optConn: Option[java.sql.Connection] = None): Int = {
    val (cWheres, cUpdates) = (canonical(wheres), canonical(updates))
    usingOpt(optConn, getConnection) { conn =>
      using(conn.prepareStatement(updateSQLString(cWheres, cUpdates))) {
        st => {
          setData(cUpdates.map(u => (u.data, u.col.colType)) ++ getWheresData(cWheres), 0, st)
          st.executeUpdate()
        }
      }
    }
  }

  private val lock = new Object

  private[db] def incrementColsTx(wheres: Wheres, increments: Increments)(implicit optConn: OptConn = None): Int = {
    incrementColsTxWithLastValue(wheres: Wheres, increments: Increments)._1
  }

  private[db] def incrementColsTxWithLastValue(wheres: Wheres, increments: Increments)(implicit optConn: OptConn = None): (Int, Array[Any]) = {
    usingOpt(optConn, getConnection) { conn =>
      lock.synchronized {
        conn.setAutoCommit(false) // transaction start
        val incrCols = increments.map(_.col)
        try {
          val aftersAndBefores = selectCols(wheres, incrCols, incrementItWithResult(_, increments.map(_.data)))(Array(), 0, 0, Some(conn))
          val resCols = aftersAndBefores.size match {
            case 0 => throw new Exception(s"Zero rows matched in table $table while incrementing cols ${incrCols.map(_.name).reduceLeft(_ + "," + _)}")
            case 1 =>
              val afterAndBefore = aftersAndBefores(0)
              val (afters, befores) = afterAndBefore.unzip
              val numRowsUpdated = updateCols(
                wheres,
                incrCols zip afters map { case (col, after) => Update(col, after) }
              )(Some(conn))
              (numRowsUpdated, befores)
            case n => throw new Exception(s"More than 1 rows ($n) matched in table $table for cols ${incrCols.map(_.name).reduceLeft(_ + "," + _)}")
          }
          conn.commit // transaction commit if everything goes well
          resCols
        } catch {
          case e: Exception =>
            e.printStackTrace
            conn.rollback
            throw new Exception(s"Error ${e.getMessage} in table $table while incrementing cols ${incrCols.map(_.name).reduceLeft(_ + "," + _)}")
        } finally {
          conn.setAutoCommit(true)
        }
      }
    }
  }

  private[db] def delete(wheres: Wheres)(implicit optConn: OptConn = None): Int = {
    val cWheres = canonical(wheres)
    using(getConnection) { conn =>
      using(conn.prepareStatement(deleteSQLString(cWheres))) { st => {
        setData(getWheresData(cWheres), 0, st)
        st.executeUpdate()
      }
      }
    }
  }

  private[db] def bmap[T](test: => Boolean)(block: => T): List[T] = {
    val ret = new ListBuffer[T]
    while (test) ret += block
    ret.toList
  }

  private[db] def selectInto(db: DBManager, cWheres: Wheres, cCols: Cols)(implicit cOrderings: Orderings = Array(), limit: Int = 0, offset: Long = 0, optConn: OptConn = None) = {
    // query of type INSERT INTO T1 SELECT A, B, C FROM T2
    selectAnyResultSet(
      (cCols, cWheres, cOrderings, limit, offset) =>
        insertIntoSQLString(db.getTable, cCols, cWheres)(cOrderings, limit, offset),
      _.executeUpdate,
      cWheres: Wheres, cCols: Cols
    )
  }

  private def selectResultSet[T](cWheres: Wheres, cCols: Cols, func: ResultSet => T)(implicit cOrderings: Orderings = Array(), limit: Int = 0, offset: Long = 0, optConn: OptConn = None): T = {
    selectAnyResultSet(
      (cCols, cWheres, cOrderings, limit, offset) => {
        selectSQLString(cCols, cWheres)(cOrderings, limit, offset)
      },
      st => using(st.executeQuery) {
        func
      },
      cWheres: Wheres, cCols: Cols
    )
  }

  private def selectAnyResultSet[T](
                                     getSQLString: (Cols, Wheres, Orderings, Int, Long) => String,
                                     doSQLQuery: PreparedStatement => T,
                                     cWheres: Wheres, cCols: Cols
                                   )(implicit cOrderings: Orderings = Array(), limit: Int = 0, offset: Long = 0, optConn: OptConn = None): T = {
    usingOpt(optConn, getConnection) { conn =>
      using(conn.prepareStatement(getSQLString(cCols, cWheres, cOrderings, limit, offset))) { st => {
        setData(
          getSelectData(cCols: Cols, cWheres: Wheres, cOrderings: Orderings), 0, st
        )
        doSQLQuery(st)
      }
      }
    }
  }

  private[db] def selectCols[T](wheres: Wheres, cols: Cols, func: Array[Any] => T)(implicit orderings: Orderings = Array(), limit: Int = 0, offset: Long = 0, optConn: OptConn = None): List[T] = {
    val (cWheres, cCols, cOrderings) = (canonical(wheres), canonical(cols), canonical(orderings))
    selectResultSet(cWheres, cCols, rs => bmap(rs.next)(func(cCols.map(get(rs, _)))))(cOrderings, limit, offset, optConn)
  }

  private[db] def selectRS[T](wheres: Wheres, cols: Cols, func: ResultSet => T)(implicit orderings: Orderings = Array(), limit: Int = 0, offset: Long = 0, optConn: OptConn = None): T = {
    val (cWheres, cCols, cOrderings) = (canonical(wheres), canonical(cols), canonical(orderings))
    selectResultSet(cWheres, cCols, func)(cOrderings, limit, offset, optConn)
  }

  private[db] def countRows(wheres: Wheres)(implicit optConn: OptConn = None): Long = {
    val cWheres = canonical(wheres)
    usingOpt(optConn, getConnection) { conn =>
      using(conn.prepareStatement(countSQLString(cWheres))) { st => {
        setData(getWheresData(cWheres), 0, st)
        using(st.executeQuery) { rs =>
          rs.next
          rs.getLong(1)
        }
      }
      }
    }
  }
}




