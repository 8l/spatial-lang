package spatial.codegen.scalagen

import argon.codegen.scalagen.ScalaCodegen
import spatial.SpatialExp
import spatial.api.ControllerExp

trait ScalaGenController
    extends ScalaCodegen
    with ScalaGenStream
    with ScalaGenMemories {
  val IR: SpatialExp
  import IR._

  def localMems: List[Exp[_]]

  private def emitNestedLoop(lhs: Exp[_],
                             cchain: Exp[CounterChain],
                             iters: Seq[Bound[Index]])(func: => Unit): Unit = {
    for (i <- iters.indices)
      open(
        src"$cchain($i).foreach{case (is,vs) => is.zip(vs).foreach{case (${iters(i)},v) => if (v) {")

    func

    iters.indices.foreach { _ =>
      close("}}}")
    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case Hwblock(func, isForever) =>
      enableMemGen = true
      localMems.foreach {
        case lhs @ Op(rhs) =>
          if (!isOffChipMemory(lhs)) emitNode(lhs.asInstanceOf[Sym[_]], rhs)
      }
      enableMemGen = false

      emit(src"/** BEGIN HARDWARE BLOCK $lhs **/")
      if (!isForever) {
        open(src"val $lhs = {")
        emitBlock(func)
        close("}")
      } else {
        if (streamIns.nonEmpty) {
          emit(
            src"def hasItems = " + streamIns
              .map(quote)
              .map(_ + ".nonEmpty")
              .mkString(" || "))
        } else {
          emit(
            s"""print("No Stream inputs detected for loop at ${lhs.ctx}. Enter number of iterations: ")""")
          emit(src"val ${lhs}_iters = Console.readLine.toInt")
          emit(src"var ${lhs}_ctr = 0")
          emit(
            src"def hasItems: Boolean = { val has = ${lhs}_ctr < ${lhs}_iters ; ${lhs}_ctr += 1; has }")
        }
        open(src"while(hasItems) {")
        emitBlock(func)
        close("}")
        emit(src"val $lhs = ()")
      }
      streamOuts.foreach {
        case x @ Def(StreamOutNew(bus)) =>
          if (!bus.isInstanceOf[DRAMBus[_]]) emit(src"print_$x()") // HACK: Print out streams after block finishes running
      }
      emit(src"/** END HARDWARE BLOCK $lhs **/")

    case UnitPipe(ens, func) =>
      emit(src"/** BEGIN UNIT PIPE $lhs **/")
      val en = if (ens.isEmpty) "true" else ens.map(quote).mkString(" && ")
      open(src"val $lhs = if ($en) {")
      emitBlock(func)
      close("}")
      emit(src"/** END UNIT PIPE $lhs **/")

    case ParallelPipe(ens, func) =>
      emit(src"/** BEGIN PARALLEL PIPE $lhs **/")
      val en = if (ens.isEmpty) "true" else ens.map(quote).mkString(" && ")
      open(src"val $lhs = if ($en) {")
      emitBlock(func)
      close("}")
      emit(src"/** END PARALLEL PIPE $lhs **/")

    case OpForeach(cchain, func, iters) =>
      emit(src"/** BEGIN FOREACH $lhs **/")
      open(src"val $lhs = {")
      emitNestedLoop(lhs, cchain, iters) { emitBlock(func) }
      close("}")
      emit(src"/** END FOREACH $lhs **/")

    case OpReduce(cchain,
                  accum,
                  map,
                  load,
                  reduce,
                  store,
                  zero,
                  fold,
                  rV,
                  iters) =>
      emit(src"/** BEGIN REDUCE $lhs **/")
      open(src"val $lhs = {")
      emitNestedLoop(lhs, cchain, iters) {
        visitBlock(map)
        visitBlock(load)
        emit(src"val ${rV._1} = ${load.result}")
        emit(src"val ${rV._2} = ${map.result}")
        visitBlock(reduce)
        emitBlock(store)
      }
      close("}")
      emit(src"/** END REDUCE $lhs **/")

    case OpMemReduce(cchainMap,
                     cchainRed,
                     accum,
                     map,
                     loadRes,
                     loadAcc,
                     reduce,
                     storeAcc,
                     zero,
                     fold,
                     rV,
                     itersMap,
                     itersRed) =>
      emit(src"/** BEGIN MEM REDUCE $lhs **/")
      open(src"val $lhs = {")
      emitNestedLoop(lhs, cchainMap, itersMap) {
        visitBlock(map)
        emitNestedLoop(lhs, cchainRed, itersRed) {
          visitBlock(loadRes)
          visitBlock(loadAcc)
          emit(src"val ${rV._1} = ${loadRes.result}")
          emit(src"val ${rV._2} = ${loadAcc.result}")
          visitBlock(reduce)
          visitBlock(storeAcc)
        }
      }
      close("}")
      emit(src"/** END MEM REDUCE $lhs **/")

    case _ => super.emitNode(lhs, rhs)
  }
}
