package db.core

import java.sql.SQLException

import org.bouncycastle.util.encoders.Base64
import db.core.DataStructures._
import play.api.Logger

object Util {
  private val logger: Logger = Logger(this.getClass)

  import java.sql.{PreparedStatement, ResultSet}
  protected [db] def getWheresData(cWheres:Wheres):Array[(Any, DataType)] = cWheres.flatMap(_.compositeWheresData)
  
  protected [db] def getSelectData(cCols:Cols, cWheres:Wheres, cOrderings:Orderings):Array[(Any, DataType)] = {
    cCols.flatMap{a => a.compositeColData} ++ getWheresData(cWheres)++ cOrderings.flatMap{_.col.compositeColData}
  }

  def getRows(cols:Cols, datas:List[List[Any]]) = {
    datas map {case d => getRow(cols, d)}
  }
  def castData(d:DataType, data:String): Any = {
    d match{
      case TIMESTAMP => data.toLong
      case VARBINARY(_) | BLOB => Base64.decode(data)
      case BIGDEC(_, _) | UBIGDEC(_, _) => BigDecimal(data)
      case VARCHAR(_)|VARCHARANY => data
      case INT | UINT(_) => data.toInt
      case LONG | ULONG(_) | ULONGAuto => data.toLong
      case any => throw new Exception("unsupported type: "+any)
    }
  }
  def getRow(cols:Cols, data:Array[Any]):Row = getRow(cols, data.toList)
  def getRow(cols:Cols, data:List[Any]) = {
    val names = cols.map(_.name.toLowerCase)
    val newData = data.map { d =>
      d match {
        case a:Array[Byte] => new String(a, "UTF-8")
        case any => any
      }
    }
    Row(newData, names)
  }

  def colTypeString(t:DataType) = t match {
    case VARCHAR(size) => "VARCHAR("+size+")"
    case VARCHARANY => "VARCHAR"
    case VARBINARY(size) => "VARBINARY("+size+")"
    case BIGDEC(size, precision) => s"DECIMAL($size, $precision)"
    case UBIGDEC(size, precision) => s"DECIMAL($size, $precision)"
    case TIMESTAMP => "TIMESTAMP"
    case INT | UINT(_) => "INT"
    case BOOL => "BOOLEAN"
    case LONG | ULONG(_) => "BIGINT"
    case ULONGAuto => "BIGINT AUTO_INCREMENT"
    case BLOB => "BLOB"
    case ULONGCOMPOSITE | CASTTYPE(_) | CompositeCol(_, _, _)|CONST(_, _) => throw new Exception("Unsupported colType: "+t)
  }

  def get(rs:ResultSet, col:Col, optAlias:Option[String]=None) = {
    def getFunc(d:DataType):String=> Any = {
      d match{
        case TIMESTAMP => rs.getTimestamp 
        case VARBINARY(s) => rs.getBytes
        case BOOL => rs.getBoolean
        case BIGDEC(size, scale) => s => BigDecimal(rs.getBigDecimal(s))
        case UBIGDEC(size, scale) => s => BigDecimal(rs.getBigDecimal(s))
        case VARCHAR(_)|VARCHARANY => rs.getString
        case INT | UINT(_) => rs.getInt
        case ULONGCOMPOSITE | LONG | ULONG(_) | ULONGAuto => rs.getLong
        case BLOB => rs.getBytes
        case CASTTYPE(cd) => getFunc(cd)
        case CompositeCol(lhs, _, _) => getFunc(lhs.colType)
        case CONST(_, t) => getFunc(t)
        case any => _ => throw new Exception("unsupported type: "+any)
      }
    }
    getFunc(col.colType)(if (optAlias.isDefined) optAlias.get else col.alias)
  }

  def unableToConvert(src:Any, dest:Class[_]) = throw new Exception(s"I don't know how to convert $src of type ${src.getClass.getCanonicalName} to ${dest.getCanonicalName}")

  def set(ctr: Int, st:PreparedStatement, data: Any, dataType:DataType)(implicit ignoreUnsigned:Boolean = true):Unit = {
    def signException(x:Number, d:DataType) = if (!ignoreUnsigned) throw new SQLException("unsigned constraint violation: ["+x+"] for ["+d+"]")
    def rangeException(n:Number, x:Any, d:DataType) = throw new SQLException("range constraint violation: ["+x+"] for ["+d+"] (limit: "+n+")")

    dataType match {
      case CASTTYPE(castedDataType) =>
        set(ctr, st, data, castedDataType)
      case TIMESTAMP  =>
        data match {
          case x: java.sql.Timestamp => st.setTimestamp(ctr, x)
          case x: String =>
            st.setString(ctr, x)
          case x: Long => st.setLong(ctr, x)
          case any =>
            unableToConvert(any, classOf[java.sql.Timestamp])
        }
      case VARBINARY(s) => data match { case x: Array[Byte] => st.setBytes(ctr, x) }
      case VARCHAR(s)   =>  data match {
          case x: String =>
            if (x.size > s) throw new SQLException("Varchar size > "+s+": "+x.take(10)+"...")
            st.setString(ctr, x)
          case x: Int =>  // for having clause
            st.setInt(ctr, x)
          case any => unableToConvert(any, classOf[String])
        }
      case VARCHARANY => data match {
          case x:String => st.setString(ctr, x)
          case any => unableToConvert(any, classOf[String])
        }
      case BIGDEC(s, p) => data match {
          case x: BigDecimal =>
            if (x.scale > s) throw new SQLException("scala bigdec size > "+s+": "+x.toString.take(10)+"...")
            st.setBigDecimal(ctr, x.bigDecimal)
          case x: BigInt => set(ctr, st, BigDecimal(x), dataType)
          case x: Long => set(ctr, st, BigDecimal(x), dataType)
          case x: Int => set(ctr, st, BigDecimal(x), dataType)
          case any => unableToConvert(any, classOf[BigDecimal])
        }
      case INT => data match {
          case x: Int => st.setInt(ctr, x)
          case any => unableToConvert(any, classOf[Int])
        }
      case LONG | ULONGCOMPOSITE => data match {
          case x: Long => st.setLong(ctr, x)
          case x: BigInt => if (x <= Long.MaxValue && x >= Long.MinValue) st.setLong(ctr, x.toLong)
          case x: Int => st.setLong(ctr, x)
          case x: java.sql.Timestamp => st.setLong(ctr, x.getTime)
          case x: String => st.setString(ctr, x)
          case any => unableToConvert(any, classOf[Long])
        }
      case UBIGDEC(s, p) => data match {
          case x: BigDecimal => // ignoreUnsigned is used to avoid errors when data is in where clause. See top of method definition
            if (x < 0) signException(x, dataType)
            if (x.scale > s) rangeException(s, x.toString.take(10)+"...", dataType)
            st.setBigDecimal(ctr, x.bigDecimal)
          case x: BigInt => set(ctr, st, BigDecimal(x), dataType)
          case x: Long => set(ctr, st, BigDecimal(x), dataType)
          case x: Int => set(ctr, st, BigDecimal(x), dataType)
          case any => unableToConvert(any, classOf[BigInt])
        }
      case BOOL => data match {
          case x:Boolean => st.setBoolean(ctr, x)
          case any => unableToConvert(any, classOf[Boolean])
        }

      case UINT(n) => data match {
          case x: Int =>
            if (x < 0) signException(x, dataType)
            if (x > n) rangeException(n, x, dataType)
            st.setInt(ctr, x)
          case x:Boolean if n == 1 => st.setInt(ctr, if (x) 1 else 0)
          case any => unableToConvert(any, classOf[Int])
        }
      case ULONGAuto => set(ctr, st, data, ULONG)
      case ULONG(n) => data match {
          case x: Long =>
            if (x < 0) signException(x, dataType)
            if (x > n) rangeException(n, x, dataType)
            st.setLong(ctr, x)
          case x: BigInt => if (x <= Long.MaxValue && x >= Long.MinValue) st.setLong(ctr, x.toLong)
          case x: Int => st.setLong(ctr, x)
          case x: java.sql.Timestamp => st.setLong(ctr, x.getTime)
          case any => unableToConvert(any, classOf[Long])
        }
      case BLOB => data match { case x: Array[Byte] => st.setBytes(ctr, x) }
      case CompositeCol(_, oper, _) => 
        (oper, data) match {
          case (Add| Sub| Mul| Div | Mod, a:Int) => st.setInt(ctr, a)
          case (Add| Sub| Mul| Div | Mod, a:Long) => st.setLong(ctr, a)
          case (Add| Sub| Mul| Div, a:Double) => st.setDouble(ctr, a)
          case (Add| Sub| Mul| Div, a:Float) => st.setFloat(ctr, a)
          case (Upper | Lower, a:String) => st.setString(ctr, a)
          case _ => throw new Exception("Data type not supported: "+data+" for oper: "+oper)
        }
      case CONST(_, constType) => 
        set(ctr, st, data, constType)
      case _ =>
        logger.error("[DB] (Set) Ignoring data (ctr = "+ctr+", data: "+data + ", type: "+ dataType)
        
    }
  }

  def canDoComparison(col:Col, op:Op) = (col.colType, op) match {
    case (colType, _) if colType.isSortable => true
    case (_, From | In | NotIn | IsNull | IsNotNull) => true
    case (CompositeCol(_, _, _), _) => true
    case (_, Eq | Ne) => true
    case (VARCHAR(_), Like | NotLike | RegExp | NotRegExp) => true
    case _ => false
  }
  
  def incrementItWithResult(a:Array[Any], incrVals:Array[Any]):Array[(Any, Any)] = (a zip incrVals).map {
    case (a:Int, i:Int) => a+i
    case (a:Long, i:Long) => a+i
    case (a:Long, i:Int) => a+i
    case (a:BigInt, i:BigInt) => a+i
    case (a:BigDecimal, i:BigInt) => a + BigDecimal(i)
    case (a:BigDecimal, i:Int) => a + i
    case (a:BigDecimal, i:BigDecimal) => a + i
    case (a:BigDecimal, i:Long) => a + i
    case (a:BigInt, i:Long) => a+i
    case (a:BigInt, i:Int) => a+i
    case (a, i) => throw new Exception(s"cannot do increment on $a with $i")
  } zip (a)
}
