package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.SpatialConfig
import spatial.SpatialExp

trait ChiselGenStructs extends ChiselCodegen {
  val IR: SpatialExp
  import IR._

  override protected def spatialNeedsFPType(tp: Type[_]): Boolean =
    tp match { // FIXME: Why doesn't overriding needsFPType work here?!?!
      case FixPtType(s, d, f) => if (s) true else if (f == 0) false else true
      case IntType()          => false
      case LongType()         => false
      case FloatType()        => true
      case DoubleType()       => true
      case _                  => super.needsFPType(tp)
    }

  def dumprint(tp: Type[_]): String = tp match {
    case FixPtType(s, d, f) => s"$s,$d,$f "
    case _                  => "na "
  }

  protected def tupCoordinates(tp: Type[_], field: String): (Int, Int) =
    tp match {
      case x: Tup2Type[_, _] =>
        field match {
          case "_1" =>
            val s     = 0
            val width = bitWidth(x.m2)
            (s, width)
          case "_2" =>
            val s     = bitWidth(x.m2)
            val width = bitWidth(x.m1)
            (s, width)
        }
      case x: StructType[_] =>
        val idx      = x.fields.indexWhere(_._1 == field)
        val width    = bitWidth(x.fields(idx)._2)
        val prec     = x.fields.take(idx)
        val precBits = prec.map { case (_, bt) => bitWidth(bt) }.sum
        (precBits, width)
    }

  override protected def bitWidth(tp: Type[_]): Int = tp match {
    case e: Tup2Type[_, _] =>
      super.bitWidth(e.typeArguments(0)) + super.bitWidth(e.typeArguments(1))
    case _ => super.bitWidth(tp)
  }

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(e: SimpleStruct[_]) =>
              s"x${lhs.id}_tuple"
            case Def(e: FieldApply[_, _]) =>
              s"x${lhs.id}_apply"
            case _ =>
              super.quote(s)
          }
        case _ =>
          super.quote(s)
      }
    } else {
      super.quote(s)
    }
  }

  override protected def remap(tp: Type[_]): String = tp match {
    // case tp: DRAMType[_] => src"Array[${tp.child}]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case SimpleStruct(tuples) =>
      val items = tuples
        .map { t =>
          val width = bitWidth(t._2.tp)
          if (src"${t._1}" == "offset") {
            src"${t._2}"
          } else {
            if (width > 1 & !spatialNeedsFPType(t._2.tp)) {
              src"${t._2}(${width - 1},0)"
            } else { src"${t._2}" } // FIXME: This is a hacky way to fix chisel/verilog auto-upcasting from multiplies
          }
        }
        .reverse
        .mkString(",")
      val totalWidth = tuples
        .map { t =>
          if (src"${t._1}" == "offset") {
            64
          } else {
            bitWidth(t._2.tp)
          }
        }
        .reduce { _ + _ }
      emitGlobal(src"val $lhs = Wire(UInt(${totalWidth}.W))")
      emit(src"$lhs := Utils.Cat($items)")
    case FieldApply(struct, field) =>
      val (start, width) = tupCoordinates(struct.tp, field)
      if (spatialNeedsFPType(lhs.tp)) {
        lhs.tp match {
          case FixPtType(s, d, f) =>
            emit(
              src"""val ${lhs} = Utils.FixedPoint(${if (s) 1 else 0}, $d, $f, ${struct}(${start + width - 1}, $start))""")
          case _ =>
            emit(src"val $lhs = ${struct}(${start + width - 1}, $start)")
        }
      } else {
        emit(src"val $lhs = ${struct}(${start + width - 1}, $start)")
      }

    case _ => super.emitNode(lhs, rhs)
  }

}
