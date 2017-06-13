package spatial.lang
package control

import forge._
import spatial.metadata._
import spatial.nodes._

protected class ForeachClass(style: ControlStyle) {
  /** 1 dimensional parallel foreach **/
  @api def apply(domain1D: Counter)(func: Index => MUnit): Controller = {
    Foreach.alloc(List(domain1D), {x: List[Index] => func(x.head) }, style)
  }
  /** 2 dimensional parallel foreach **/
  @api def apply(domain1: Counter, domain2: Counter)(func: (Index,Index) => MUnit): Controller = {
    Foreach.alloc(List(domain1,domain2), {x: List[Index] => func(x(0),x(1)) }, style)
  }
  /** 3 dimensional parallel foreach **/
  @api def apply(domain1: Counter, domain2: Counter, domain3: Counter)(func: (Index,Index,Index) => MUnit): Controller = {
    Foreach.alloc(List(domain1,domain2,domain3), {x: List[Index] => func(x(0),x(1),x(2)) }, style)
  }
  /** N dimensional parallel foreach **/
  @api def apply(domain1: Counter, domain2: Counter, domain3: Counter, domain4: Counter, domain5plus: Counter*)(func: List[Index] => MUnit): Controller = {
    Foreach.alloc(List(domain1,domain2,domain3,domain4) ++ domain5plus, func, style)
  }
  @api def apply(domain: Seq[Counter])(func: List[Index] => MUnit): Controller = {
    Foreach.alloc(domain, func, style)
  }
}

object Foreach extends ForeachClass(InnerPipe) {
  @internal def alloc(domain: Seq[Counter], func: List[Index] => MUnit, style: ControlStyle): Controller = {
    val iters = List.tabulate(domain.length){_ => fresh[Index] }
    val cchain = CounterChain(domain: _*)

    val pipe = op_foreach(Nil, cchain.s, () => func(wrap(iters)).s, iters)
    styleOf(pipe) = style
    levelOf(pipe) = InnerControl // Fixed in Level Analyzer
    Controller(pipe)
  }

  /** Constructors **/
  @internal def op_foreach(
    ens:    Seq[Exp[Bit]],
    domain: Exp[CounterChain],
    func:   () => Exp[MUnit],
    iters:  List[Bound[Index]]
  ): Sym[Controller] = {
    val fBlk = stageSealedBlock{ func() }
    val effects = fBlk.effects.star
    stageEffectful( OpForeach(ens, domain, fBlk, iters), effects)(ctx)
  }

  @internal def op_unrolled_foreach(
    en:     Seq[Exp[Bit]],
    cchain: Exp[CounterChain],
    func:   () => Exp[MUnit],
    iters:  Seq[Seq[Bound[Index]]],
    valids: Seq[Seq[Bound[Bit]]]
  ): Exp[Controller] = {
    val fBlk = stageSealedBlock { func() }
    val effects = fBlk.effects.star
    stageEffectful(UnrolledForeach(en, cchain, fBlk, iters, valids), effects)(ctx)
  }

}
