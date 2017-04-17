package spatial.transform

import argon.transform.ForwardTransformer
import spatial.SpatialExp

trait SwitchFlattener extends ForwardTransformer {
  val IR: SpatialExp
  import IR._
  override val name = "Switch Flattener"

  var enable: Option[Exp[Bool]] = None

  def withEnable[T](en: Exp[Bool])(blk: => T)(implicit ctx: SrcCtx): T = {
    var prevEnable = enable
    enable = Some(enable.map(bool_and(_, en)).getOrElse(en))
    val result = blk
    enable = prevEnable
    result
  }

  var canInline = Map[Exp[_], Boolean]()

  override def transform[T: Type](lhs: Sym[T], rhs: Op[T])(
      implicit ctx: SrcCtx): Exp[T] = rhs match {
    case Switch(blk, cases)    => inlineBlock(blk)
    case SwitchCase(cond, blk) => withEnable(f(cond)) { inlineBlock(blk) }

    // TODO: Could potentially make this a bit simpler if default node mirroring could be overridden
    case op: EnabledController =>
      transferMetadataIfNew(lhs) {
        op.mirrorWithEn(f, enable.toSeq).asInstanceOf[Exp[T]]
      }._1
    case op: EnabledOp[_] if enable.isDefined =>
      transferMetadataIfNew(lhs) {
        op.mirrorWithEn(f, enable.get).asInstanceOf[Exp[T]]
      }._1

    case _ => super.transform(lhs, rhs)

  }
}
