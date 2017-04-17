package spatial.codegen.scalagen

import argon.ops.FixPtExp
import spatial.api.{RegisterFileExp, VectorExp}
import org.virtualized.SourceContext
import spatial.SpatialExp
import spatial.analysis.NodeClasses

trait ScalaGenRegFile extends ScalaGenMemories {
  val IR: SpatialExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: RegFileType[_] => src"Array[${tp.child}]"
    case _                  => super.remap(tp)
  }

  private def shiftIn(lhs: Exp[_],
                      rf: Exp[_],
                      inds: Seq[Exp[Index]],
                      d: Int,
                      data: Exp[_],
                      isVec: Boolean,
                      en: Exp[Bool]): Unit = {
    val len    = if (isVec) lenOf(data) else 1
    val dims   = stagedDimsOf(rf)
    val size   = dims(d)
    val stride = (dims.drop(d + 1).map(quote) :+ "1").mkString("*")

    open(src"val $lhs = if ($en) {")
    emit(src"val ofs = ${flattenAddress(dims, inds, None)}")
    emit(src"val stride = $stride")
    open(src"for (j <- $size-1 to 0 by - 1) {")
    if (isVec)
      emit(
        src"if (j < $len) $rf.update(ofs+j*stride, $data(j)) else $rf.update(ofs + j*stride, $rf.apply(ofs + (j - $len)*stride))")
    else
      emit(
        src"if (j < $len) $rf.update(ofs+j*stride, $data) else $rf.update(ofs + j*stride, $rf.apply(ofs + (j - $len)*stride))")
    close("}")
    close("}")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op @ RegFileNew(dims) =>
      if (enableMemGen)
        emit(
          src"val $lhs = Array.fill(${dims.map(quote).mkString("*")})(${invalid(op.mT)})")
    case op @ RegFileLoad(rf, inds, en) =>
      val dims = stagedDimsOf(rf)
      open(src"val $lhs = {")
      oobApply(op.mT, rf, lhs, inds) {
        emit(
          src"if ($en) $rf.apply(${flattenAddress(dims, inds, None)}) else ${invalid(op.mT)}")
      }
      close("}")

    case op @ RegFileStore(rf, inds, data, en) =>
      val dims = stagedDimsOf(rf)
      open(src"val $lhs = {")
      oobUpdate(op.mT, rf, lhs, inds) {
        emit(
          src"if ($en) $rf.update(${flattenAddress(dims, inds, None)}, $data)")
      }
      close("}")

    case RegFileShiftIn(rf, i, d, data, en) =>
      shiftIn(lhs, rf, i, d, data, isVec = false, en)
    case ParRegFileShiftIn(rf, i, d, data, en) =>
      shiftIn(lhs, rf, i, d, data, isVec = true, en)

    case _ => super.emitNode(lhs, rhs)
  }

}
