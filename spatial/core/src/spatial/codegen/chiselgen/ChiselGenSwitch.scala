package spatial.codegen.chiselgen


import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.SRAMExp
import spatial.SpatialConfig
import spatial.SpatialExp

trait ChiselGenSwitch extends ChiselCodegen {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    // case op@Switch(body,selects,cases) =>
    //   emit(s"// Switch with $body $selects $cases")
    //   emitBlock(body)
    //   if (Bits.unapply(op.mT).isDefined) {
    //   //   open(src"val $lhs = {")
    //   //     selects.indices.foreach { i =>
    //     // emit(src"""val """)
    //   //       emit(src"""${if (i == 0) "if" else "else if"} (${selects(i)}) { ${cases(i)} }""")
    //   //     }
    //   //     emit(src"else { ${invalid(op.mT)} }")
    //   //   close("}")
    //   }
    //   // else {
    //   //   emit(src"val $lhs = ()")
    //   // }

    // case SwitchCase(body) =>
    //   emit(src"// Ignore SwitchCase node $lhs")
    //   // open(src"val $lhs = {")
    //   emitBlock(body)
    //   // close("}")

    case _ => super.emitNode(lhs, rhs)
  }
}
