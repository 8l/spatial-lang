package spatial.analysis

/** Used to make sure the user (and David) didn't do anything stupid **/
trait ScopeCheck extends SpatialTraversal {
  import IR._

  override val name    = "Accel Scope Check"
  override val recurse = Default

  def transferError(s: Exp[_]): Unit = {
    error(s.ctx, u"Untransferred host value $s was used in the Accel scope.")

  }

  def isTransferException(e: Exp[_]): Boolean = e match {
    case Exact(_) => true
    case Const(_) => true
    case s: Sym[_] if s.tp == TextType =>
      true // Exception to allow debug printing to work
    case s if isOffChipMemory(s) => true
    case _                       => false
  }

  override protected def visit(lhs: Sym[_], rhs: Op[_]) = rhs match {
    case Hwblock(blk, _) =>
      val (inputs, stms) = blockInputsAndNestedContents(blk)

      val illegalInputs = inputs.filter {
        case s @ Def(RegRead(_)) =>
          true // Special case on reg reads to disallow const prop through setArg
        case s => !isTransferException(s)
      }
      if (illegalInputs.nonEmpty) {
        val n = illegalInputs.size
        if (n == 1) {
          error(
            illegalInputs.head.ctx,
            u"Value ${illegalInputs.head} was defined on the host but used in the Accel scope without explicit transfer")
        } else {
          error(
            "Multiple values were defined on the host and used in the Accel scope without explicit transfer.")
        }
        error(
          "Use ArgIns or DRAMs coupled with setArg or setMem to transfer scalars or arrays to the accelerator")

        illegalInputs.foreach { in =>
          val use = stms.find(_.rhs.inputs.contains(in))

          if (n > 1) error(in.ctx, u"Value $in")
          error(in.ctx)

          if (use.isDefined) {
            error(use.get.lhs.head.ctx,
                  c"First use occurs here: ",
                  noError = true)
            error(use.get.lhs.head.ctx)
          }
        }
      }

    case _ => super.visit(lhs, rhs)
  }

}
