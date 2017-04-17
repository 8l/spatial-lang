package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import argon.ops.FixPtExp
import spatial.api.SRAMExp
import spatial.{SpatialConfig, SpatialExp}

trait CppGenSRAM extends CppCodegen {
  val IR: SpatialExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: SRAMType[_] => src"Array[${tp.child}]"
    case _               => super.remap(tp)
  }

  override def quote(s: Exp[_]): String = {
    if (SpatialConfig.enableNaming) {
      s match {
        case lhs: Sym[_] =>
          lhs match {
            case Def(SRAMNew(dims)) => s"x${lhs.id}_sram"
            case _                  => super.quote(s)
          }
        case _ =>
          super.quote(s)
      }
    } else {
      super.quote(s)
    }
  }

  def flattenAddress(dims: Seq[Exp[Index]],
                     indices: Seq[Exp[Index]],
                     ofs: Option[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length) { i =>
      (dims.drop(i + 1).map(quote) :+ "1").mkString("*")
    }
    indices
      .zip(strides)
      .map { case (i, s) => src"$i*$s" }
      .mkString(" + ") + ofs
      .map { o =>
        src" + $o"
      }
      .getOrElse("")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case _ => super.emitNode(lhs, rhs)
  }
}
