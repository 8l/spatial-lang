package spatial.codegen.scalagen

import spatial.SpatialExp
import spatial.api.FIFOExp

trait ScalaGenFIFO extends ScalaGenMemories {
  val IR: SpatialExp
  import IR._

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: FIFOType[_] => src"scala.collection.mutable.Queue[${tp.child}]"
    case _               => super.remap(tp)
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op @ FIFONew(size) =>
      if (enableMemGen)
        emit(
          src"val $lhs = new scala.collection.mutable.Queue[${op.mT}] // size: $size")
    case FIFOEnq(fifo, v, en) =>
      emit(src"val $lhs = if ($en) $fifo.enqueue($v)")
    case op @ FIFODeq(fifo, en) =>
      emit(
        src"val $lhs = if ($en && $fifo.nonEmpty) $fifo.dequeue() else ${invalid(op.mT)}")
    case _ => super.emitNode(lhs, rhs)
  }
}
