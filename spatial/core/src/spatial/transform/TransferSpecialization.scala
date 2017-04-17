package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp

trait TransferSpecialization extends ForwardTransformer {
  val IR: SpatialExp
  import IR._

  override val name = "Transfer Specialization"

  override def transform[T: Type](lhs: Sym[T], rhs: Op[T])(
      implicit ctx: SrcCtx): Exp[T] = rhs match {
    case e: DenseTransfer[_, _] => e.expand(f).asInstanceOf[Exp[T]]
    case e: SparseTransfer[_]   => e.expand(f).asInstanceOf[Exp[T]]
    case _                      => super.transform(lhs, rhs)
  }

}
