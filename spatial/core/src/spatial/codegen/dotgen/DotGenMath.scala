package spatial.codegen.dotgen

import argon.codegen.dotgen.DotCodegen
import spatial.SpatialExp
import argon.Config

trait DotGenMath extends DotCodegen {
  val IR: SpatialExp
  import IR._

  override def attr(n:Exp[_]) = n match {
    case lhs: Sym[_] => lhs match {
      case Def(Mux(_,_,_)) => super.attr(n).shape(mux)
      case Def(Min(_,_)) => super.attr(n).shape(circle).label("min")
      case Def(Max(_,_)) => super.attr(n).shape(circle).label("max")
      case _ => super.attr(n)
    }
    case _ => super.attr(n)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case FixAbs(x)  => 

    case FltAbs(x)  => 
    case FltLog(x)  => x.tp match {
      case DoubleType() => 
      case FloatType()  => 
    }
    case FltExp(x)  => x.tp match {
      case DoubleType() =>
      case FloatType()  => 
    }
    case FltSqrt(x) => x.tp match {
      case DoubleType() => 
      case FloatType()  => 
    }

    case Mux(sel, a, b) => if (Config.dotDetail > 0) {
      emitVert(lhs)
      emitEdge(a, lhs)
      emitEdge(b, lhs)
      emitSel(sel, lhs)
    }

    case Min(a, b) => if (Config.dotDetail > 0) {
      emitVert(lhs)
      emitEdge(a, lhs)
      emitEdge(b, lhs)
    }
    case Max(a, b) => if (Config.dotDetail > 0) {
      emitVert(lhs)
      emitEdge(a, lhs)
      emitEdge(b, lhs)
    }

    case _ => super.emitNode(lhs, rhs)
  }

}
