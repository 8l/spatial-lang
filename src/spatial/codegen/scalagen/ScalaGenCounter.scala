package spatial.codegen.scalagen

import argon.codegen.FileDependencies
import argon.codegen.scalagen.ScalaCodegen
import spatial.api.CounterExp
import spatial.SpatialConfig

trait ScalaGenCounter extends ScalaCodegen with FileDependencies {
  val IR: CounterExp
  import IR._

  dependencies ::= AlwaysDep(s"${SpatialConfig.HOME}/src/spatial/codegen/scalagen/resources/Counter.scala")
  dependencies ::= AlwaysDep(s"${SpatialConfig.HOME}/src/spatial/codegen/scalagen/resources/Forever.scala")

  override protected def remap(tp: Staged[_]): String = tp match {
    case CounterType      => src"Counter"
    case CounterChainType => src"Array[Counter]"
    case _ => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case CounterNew(start,end,step,par) => emit(src"val $lhs = Counter($start, $end, $step, $par)")
    case CounterChainNew(ctrs) => emit(src"""val $lhs = Array(${ctrs.map(quote).mkString(",")})""")
    case Forever() => emit(src"""val $lhs = Forever()""")
    case _ => super.emitNode(lhs, rhs)
  }

}
