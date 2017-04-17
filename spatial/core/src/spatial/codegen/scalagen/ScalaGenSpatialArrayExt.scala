package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaGenArray
import argon.ops.ArrayExtExp
import spatial.SpatialExp

// Version of Arrays that uses Number as iterator
trait ScalaGenSpatialArrayExt extends ScalaGenArray {
  val IR: SpatialExp
  import IR._

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case ArrayUpdate(array, i, data) =>
      emit(src"val $lhs = $array.update($i, $data)")
    case MapIndices(size, func, i) =>
      open(src"val $lhs = Array.tabulate($size){bbb => ")
      emit(src"val $i = Number(bbb)")
      emitBlock(func)
      close("}")

    case ArrayForeach(array, apply, func, i) =>
      open(src"val $lhs = $array.indices.foreach{bbb => ")
      emit(src"val $i = Number(bbb)")
      visitBlock(apply)
      emitBlock(func)
      close("}")

    case ArrayMap(array, apply, func, i) =>
      open(src"val $lhs = Array.tabulate($array.length){bbb => ")
      emit(src"val $i = Number(bbb)")
      visitBlock(apply)
      emitBlock(func)
      close("}")

    case ArrayZip(a, b, applyA, applyB, func, i) =>
      open(src"val $lhs = Array.tabulate($a.length){bbb => ")
      emit(src"val $i = Number(bbb)")
      visitBlock(applyA)
      visitBlock(applyB)
      emitBlock(func)
      close("}")

    case ArrayReduce(array, apply, reduce, i, rV) =>
      open(src"val $lhs = $array.reduce{(${rV._1},${rV._2}) => ")
      emitBlock(reduce)
      close("}")

    case ArrayFilter(array, apply, cond, i) =>
      open(src"val $lhs = $array.filter{${apply.result} => ")
      emitBlock(cond)
      close("}")

    case ArrayFlatMap(array, apply, func, i) =>
      open(src"val $lhs = $array.flatMap{${apply.result} => ")
      emitBlock(func)
      close("}")

    case _ => super.emitNode(lhs, rhs)
  }
}
