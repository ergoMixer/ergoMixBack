package db.core

import java.util.Base64

import db._

package object DataStructures{
  implicit def anyToAny(s: Any) = new BetterAny(s)

  class BetterAny(val a:Any) {
    def as[T] = a.asInstanceOf[T]
    def anySQLString:String = a match {
      case t:Col => t.colSQLString
      case a:Array[String] if a.nonEmpty => "("+(a.map(x => new BetterAny(x).anySQLString).reduceLeft(_+","+_))+")"
      case a:Array[Long] if a.nonEmpty => "("+(a.map(_.anySQLString).reduceLeft(_+","+_))+")"
      case a:Array[Int] if a.nonEmpty => "("+(a.map(_.anySQLString).reduceLeft(_+","+_))+")"
      case a:Array[_] if a.isEmpty => throw new Exception("cannot deconstruct an empty array")
      case _ => "?"
    }

    def to(table:Table) = a match { // make it canonical if it is a column
      case c:Col => c.to(table)
      case w:Where => w.to(table)
      case null => null
      case any:Any => any
    }    
    def const = constCol(a)
  }

  def hash(s:String) = {
    val base64 = Base64.getEncoder
    val sha = java.security.MessageDigest.getInstance("SHA-1")
    base64.encodeToString(
      sha.digest(
        s.getBytes
      )
    ).filter(x =>
      x.isLetter
    ).take(10)
  }
  
  case class Row(data:Seq[Any], names:Array[String]) {
    def this(data:Seq[Any], names:Seq[String]) = this(data, names.toArray)
    def this(data:Array[Any], names:Seq[String]) = this(data.toList, names.toArray)
    def this(data:Array[Any], names:Array[String]) = this(data.toList, names)
  }

  sealed abstract class DataType {
    def isSortable:Boolean
    def toCastString:String // used for a 'cast as' operation example cast age as INTEGER. This should output 'INTEGER'
  }

  private [db] case class CASTTYPE(dataType:DataType) extends DataType {
    def isSortable:Boolean = dataType.isSortable
    def toCastString:String = dataType.toCastString
  }
  
  // composite column. Nothing to do with composite integers
  object ULONGCOMPOSITE extends DataType {
    def isSortable:Boolean = true
    def toCastString:String = throw new Exception(s"Unsupported cast for column $this")
  } 

  case class VARCHAR(size:Int) extends DataType {
    def isSortable = false 
    def toCastString:String = "VARCHAR"
  }
  
  object BOOL extends DataType {
    def isSortable = true 
    def toCastString:String = "BOOLEAN"
  }
  
  object VARCHARANY extends DataType {   // 'ANY' implies any size
    def isSortable = false 
    def toCastString:String = "VARCHAR"
  } 

  case class VARBINARY(size:Int) extends DataType {
    def isSortable = false 
    def toCastString:String = "VARBINARY"
  }

  case object TIMESTAMP extends DataType {
    def isSortable = true 
    def toCastString:String = "TIMESTAMP"
  }

  case object INT extends DataType  {
    def isSortable = true 
    def toCastString:String = "INT"
  }

  case class UINT(max:Int) extends DataType {
    def isSortable = true 
    def toCastString:String = "INT"
  }

  object UINT extends UINT(Int.MaxValue)

  case object LONG extends DataType {
    def isSortable = true 
    def toCastString:String = "BIGINT"
  }

  case class ULONG(max:Long) extends DataType {
    def isSortable = true 
    def toCastString:String = "BIGINT"
  }

  object ULONGAuto extends DataType { // for auto increment
    def isSortable = true 
    def toCastString:String = "BIGINT"
  }

  object ULONG extends ULONG(Long.MaxValue)
  
  case class UBIGDEC (size:Int, precision:Int) extends DataType {
    def this(size:Int) = this(size, 0)
    def isSortable = true
    def toCastString:String = "VARCHAR"
  }

  case class BIGDEC(size:Int, precision:Int) extends DataType {
    def this(size:Int) = this(size, 0)
    def isSortable = true 
    def toCastString:String = "VARCHAR"
  }
  
  
  /**
   * Scala data type corresponding to a BLOB SQL data type
   */
  case object BLOB extends DataType { 
    def isSortable = false 
    def toCastString:String = "INT"
  }
  
  
  case class CONST(a:Any, dataType:DataType) extends DataType {
    dataType match {
      case BIGDEC(_, _)|LONG|INT|VARCHAR(_)|BOOL =>
      case a => throw new Exception(s"invalid data type for CONST col: "+a)
    }
    def isSortable = false
    def toCastString:String = throw new Exception(s"Unsupported cast for column $this")
  }
  
  def constCol(data:Any) = { // special column to denote const types.. Example SELECT sal, 10 from Foo. Here 10 is const
    Col(data.getClass.getSimpleName, data match {
      case s:String => CONST(s, VARCHAR(255))
      case s:Int => CONST(s, INT)
      case s:Long => CONST(s, LONG)
      case s:Boolean => CONST(s, BOOL)
      case s:BigInt => CONST(s, BIGDEC(100, 0))
      case s:BigDecimal => CONST(s, BIGDEC(s.scale, s.precision))
      case _ => throw new Exception(s"I don't know how to convert data of type ${data.getClass} to a CONST Col")
    }, None)
  }
  
  sealed abstract class ColOperation(symbol:String) {// select col1 + col2 from Table. Here + is the ColOperation
    override def toString = symbol
  }

  case object Add extends ColOperation("+")
  case object Mul extends ColOperation("*")
  case object Sub extends ColOperation("-")
  case object Div extends ColOperation("/")
  case object Mod extends ColOperation("%")
  case object DateDiffSecond extends ColOperation("dateDiffSecond")
  case object DateDiffMillis extends ColOperation("dateDiffMillis")
  
  case object Upper extends ColOperation("UPPER")
  case object ABS extends ColOperation("ABS")
  case object Lower extends ColOperation("LOWER")
  
  case object Cast extends ColOperation("CAST")
  
  // Example of a query with composite cols: select (t1.c1*(t2.c2+(t3.c3+4))) from t1, t2, t3. 
  // For any col, we will store the composite-ness in the colType
  // In the above, (t1.c1*(t2.c2+(t3.c3+4))) is a composite column
  case class CompositeCol(left:Col, op:ColOperation, right:Any) extends DataType { // left can be another ordinary or composite column. (ordinary columns are non-composite ones) {
    def isSortable = left.colType.isSortable
    def toCastString:String = throw new Exception(s"Unsupported cast for column $this")
  }
  
  
  implicit def colToOrdering(col:Col) = Ordering(col, Increasing)

  implicit def dbMgrToTable(dbm:DBManager) = dbm.getTable
  
  abstract class Op {override def toString:String} // Op is op used in where clauses
  object Eq extends Op {override def toString = "="}
  object Le extends Op {override def toString = "<="}
  object Ge extends Op {override def toString = ">="}
  object Gt extends Op {override def toString = ">"}
  object Lt extends Op {override def toString = "<"}
  object Ne extends Op {override def toString = "<>"}

  object From extends Op{override def toString = "="}
  
  object In extends Op{override def toString = "IN"}
  object NotIn extends Op{override def toString = "NOT IN"}

  object IsNull extends Op{override def toString = "IS NULL"}
  object IsNotNull extends Op{override def toString = "IS NOT NULL"}

  object Like extends Op {override def toString = "LIKE"}
  object RegExp extends Op {override def toString = "REGEXP"}
  object NotRegExp extends Op {override def toString = "NOT REGEXP"}
  object NotLike extends Op {override def toString = "NOT LIKE"}

  
  val allOps = Seq(Eq, Le, Ge, Gt, Lt, Ne, Like, NotLike, RegExp, NotRegExp)
  def getOp(opString:String) = {
    val upper = opString.toUpperCase
    allOps.find(_.toString == upper) match {
      case Some(op) => op
      case _ => throw new Exception("operation not found: "+opString)
    }
  }

  case class IncrOp(increment:Update[_]) extends Op {override def toString = Eq+" "+increment.col.colSQLString+" + "}

  implicit def toWhere(a:(Col, Op, Any)) = Where(a)
  implicit def toColOpAny(w:Where) = (w.col, w.op, w.data):(Col, Op, Any)
  implicit def toWhere(a:Seq[(Col, Op, Any)]) = a.map(Where(_)) // .toArray
  implicit def toWhere(a:Array[(Col, Op, Any)]):Wheres = a.map(Where(_))
  
  sealed abstract class WhereJoinOp(symbol:String) {  // select where c1 = 3 or c3 = 4. Here or is the whereJoinOp
    override def toString = symbol
  }
  case object And extends WhereJoinOp("and")
  case object Or extends WhereJoinOp("or")
  case class WhereJoin(left:Where, whereJoinOp:WhereJoinOp, right:Where) extends Op {
    def to(table:Table):WhereJoin = {
      WhereJoin(left.to(table), whereJoinOp, right.to(table))
    }
  }
  
  case class Ordering(col:Col, isDescending:Boolean){
    lazy val order = if (isDescending) "DESC" else "ASC"
    override def toString = s"ORDERBY ($col, $order)"    
    def to(table:Table) = Ordering(col.to(table), isDescending)

  }
  
  object Update{def apply[T](update:(Col, T)) = new Update[T](update._1, update._2)}
  implicit def toUpdate[T](a:(Col, T)) = Update(a)
  implicit def toUpdate[_](a:Seq[(Col, Any)]) = a.map(Update(_))
  implicit def toUpdate[_](a:Array[(Col, Any)]):Updates[_] = a.map(Update(_))

  object Ordering{def apply(ordering:(Col, Boolean)) = new Ordering(ordering._1, ordering._2)}
  implicit def toOrdering(a:(Col, Boolean)) = Ordering(a)
  implicit def toOrdering(a:Seq[(Col, Boolean)]) = a.map(Ordering(_))
  implicit def toOrdering(a:Array[(Col, Boolean)]):Orderings = a.map(Ordering(_))

  final val Decreasing = true
  final val Increasing = false

  case class Update[+T](col:Col, data:T) {
    override def toString = s"UPDATE ($col, $data)"
    def and (that:Update[Any]):Updates[Any] = Array(this, that)    
  }
  
  type Updates[T] = Array[Update[T]]
  type Increment = Update[Number]
  type Increments = Array[Increment]
  type Cols = Array[Col]
    
  type Ops = Array[Op]
  type Wheres = Array[Where]
  type Orderings = Array[Ordering]
  type OptConn = Option[java.sql.Connection]
}
