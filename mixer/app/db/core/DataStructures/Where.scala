
package db.core.DataStructures

import db.core.{Table, Util}

object Where{
  def apply(where:(Col, Op, Any)) = {
    new Where(where._1, where._2, where._3)
  }
  val alwaysTrue = Where(constCol(true) === true)
  val alwaysFalse = Where(constCol(true) === false)
}

case class Where(col:Col, op:Op, data:Any) {
  def checkValidOp = if (!Util.canDoComparison(col, op)) throw new Exception(s"invalid op $op for col $col of type ${col.colType} in table ${col.optTable.getOrElse("none")}")
  
  data match {
    case null if op == IsNull => 
    case null if op == IsNotNull => 
    case _:Int => 
    case _:String => 
    case _:Long => 
    case _:Col => 
    case _:Boolean => 
    case _:Array[Byte] => 
    case _:Array[String] => 
    case _:Array[Long] => 
    case _:Array[Int] => 
    case _:Double =>
    case _:Float => 
    case _:BigInt => 
    case _:BigDecimal => 
    case null => 
      throw new Exception(s"'null' not allowed for $col")
    case any:Any => 
      throw new Exception(s"Unknown data type: ${any.getClass} with value $data in WHERE clause $this")
      
  }
  def to(table:Table):Where = op match {
    case whereJoin:WhereJoin => Where(col, whereJoin.to(table), data)
    case _ => Where(col.to(table), op, data.to(table))
  }
  def isDataAnotherCol = data match {
    case _:Col => true
    case _ => false
  }
  lazy val whereSQLString:String = op match {
    case WhereJoin(left, whereOp, right) => "("+left.whereSQLString+" "+whereOp+" "+right.whereSQLString+")"        
    case _ => col.colSQLString+" "+op+ " "+ (
        (op, data) match {
          case (From, n) => throw new Exception("from operation must map to NestedSelect. Found: "+n+" of type: "+n.getClass)
          case (_, null) => ""
          case (_, any) => 
            any.anySQLString
        }
      )
  }
  lazy val compositeWheres:Array[Where] = {
    op match {
      case WhereJoin(left, whereOp, right) => left.compositeWheres ++ right.compositeWheres
      case _ => Array(this)
    }
  }
  lazy val compositeWheresData:Array[(Any, DataType)] = compositeWheres.flatMap(w => 
    // first block is for composite cols .. select * from T where a+4 = 5. The first block handles the data (i.e., 4) in (a+4)
    w.col.compositeColData ++ (
      (w.op, w.data) match {
        case (IsNull| IsNotNull, null) => Nil
        case (_, c:Col) => c.compositeColData
        case (_ ,d:Array[Byte]) => List((d, w.col.colType)) // for blob
        case (_ ,d:Array[_]) => 
          d.toList.map(x => (x, w.col.colType))
        case (_ ,d:Any) => List((d, w.col.colType))
      }
    )
  )

  def or (where:Where) = Where(col, WhereJoin(this, Or, where), data)
  def and (where:Where) = Where(col, WhereJoin(this, And, where), data)

  // validation for ops
  op match {
    case Like | NotLike | RegExp | NotRegExp => 
      data match {
        case s:String =>
        case any => throw new Exception(s"Operation $op accepts only strings as data. Found: ${any.getClass.getCanonicalName}")
      }
    case WhereJoin(left, _ , right) if right == left => throw new Exception(s"WhereJoin: $this has same left and right wheres: $left")
    case _ => 
  }
  data match {
    case c:Col if col == c && c.optTable == col.optTable =>  throw new Exception(s"Where: $this has same left and right cols: $c")
    case _ =>
  }
  override def toString = s"WHERE $col $op $data"
}
  
