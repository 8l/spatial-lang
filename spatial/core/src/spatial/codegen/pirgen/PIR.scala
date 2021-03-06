package spatial.codegen.pirgen

import scala.collection.mutable

// TODO: This is VERY redundant with PIR
trait PIR {
  type Expr
  def isConstant(x: Expr): Boolean
  def extractConstant(x: Expr): ConstReg[AnyVal]
  def str(x: Expr): String

  // --- Memory controller modes
  sealed abstract class OffchipMemoryMode
  case object MemLoad extends OffchipMemoryMode { override def toString = "TileLoad" }
  case object MemStore extends OffchipMemoryMode { override def toString = "TileStore" }
  case object MemGather extends OffchipMemoryMode { override def toString = "Gather" }
  case object MemScatter extends OffchipMemoryMode { override def toString = "Scatter" }


  // --- Local memory banking
  sealed abstract class SRAMBanking
  case class Strided(stride: Int) extends SRAMBanking
  case class Diagonal(stride1: Int, stride2: Int) extends SRAMBanking
  case object NoBanks extends SRAMBanking { override def toString = "NoBanking()" }
  case object Duplicated extends SRAMBanking { override def toString = "Duplicated()" }
  //case object Fanout extends SRAMBanking { override def toString = "Fanout()" }


  // --- Compute types
  sealed abstract class CUStyle
  case object PipeCU extends CUStyle
  case object SequentialCU extends CUStyle
  case object MetaPipeCU extends CUStyle
  case object StreamCU extends CUStyle
  case class MemoryCU(reader:Expr) extends CUStyle // i: dispatch number
  case class FringeCU(dram:OffChip, mode:OffchipMemoryMode) extends CUStyle

  // --- Local memory modes
  sealed abstract class LocalMemoryMode
  case object SRAMMode extends LocalMemoryMode
  case object VectorFIFOMode extends LocalMemoryMode
  case object ScalarFIFOMode extends LocalMemoryMode
  case object ScalarBufferMode extends LocalMemoryMode

  case object FIFOOnWriteMode extends LocalMemoryMode


  // --- Global buses
  sealed abstract class GlobalComponent(val name: String)
  case class OffChip(override val name: String) extends GlobalComponent(name)
  case class MemoryController(override val name: String, dram: OffChip, mode: OffchipMemoryMode, parent: Expr) extends GlobalComponent(name)

  sealed abstract class GlobalBus(override val name: String) extends GlobalComponent(name)
  sealed abstract class VectorBus(override val name: String) extends GlobalBus(name)
  sealed abstract class ScalarBus(override val name: String) extends GlobalBus(name)

  case class CUVector(override val name: String) extends VectorBus(name) {
    override def toString = s"v$name"
  }
  case class CUScalar(override val name: String) extends ScalarBus(name) {
    override def toString = s"s$name"
  }

  case object LocalVectorBus extends VectorBus("LocalVector")
  case class LocalReadBus(mem:CUMemory) extends VectorBus(s"$mem.localRead")
  case class InputArg(override val name: String) extends ScalarBus(name) {
    override def toString = s"ain$name"
  }
  case class OutputArg(override val name: String) extends ScalarBus(name) {
    override def toString = s"aout$name"
  }

  trait PIRDRAMBus
  case class PIRDRAMOffset(mc: MemoryController) extends ScalarBus(mc.name + "_ofs") with PIRDRAMBus {
    override def toString = s"s$name"
  }
  case class PIRDRAMLength(mc: MemoryController) extends ScalarBus(mc.name + "_len") with PIRDRAMBus {
    override def toString = s"s$name"
  }
  case class PIRDRAMAddress(mc: MemoryController) extends VectorBus(mc.name + "_addr") with PIRDRAMBus {
    override def toString = s"v$name"
  }
  case class PIRDRAMDataOut(mc: MemoryController) extends VectorBus(mc.name + "_dataOut") with PIRDRAMBus {
    override def toString = s"v$name"
  }
  case class PIRDRAMDataIn(mc: MemoryController) extends VectorBus(mc.name + "_dataIn") with PIRDRAMBus {
    override def toString = s"v$name"
  }

  case class BusGroups(args: Iterable[ScalarBus], scalars: Iterable[ScalarBus], vectors: Iterable[VectorBus])

  def groupBuses(x: Iterable[GlobalBus]) = {
    val args    = x.collect{case arg:InputArg => arg}
    val scalars = x.collect{case b:ScalarBus if !b.isInstanceOf[InputArg] => b}
    val vectors = x.collect{case b:VectorBus if !b.isInstanceOf[LocalReadBus] => b}
    //val scalarMems = x.collect{case bus@LocalReadBus(mem) if mem.mode == ScalarFIFOMode || mem.mode == ScalarBufferMode => bus }
    //val vectorMems = x.collect{case bus@LocalReadBus(mem) if mem.mode == SRAMMode || mem.mode == FIFOOnWriteMode || mem.mode == VectorFIFOMode => bus }
    BusGroups(args, scalars, vectors)
  }


  // --- Local registers / wires
  sealed abstract class LocalComponent { final val id = {LocalComponent.id += 1; LocalComponent.id} }
  object LocalComponent { var id = 0 }

  // Locally accessible scalar should be all from ScalarFIFO or ScalarBuffer.
  sealed trait LocalScalar extends LocalComponent 
  sealed trait Addr extends LocalComponent

  sealed abstract class LocalMem[T<:LocalComponent] extends LocalComponent {
    def eql(that: T): Boolean = this.id == that.id
    def canEqual(that: Any): Boolean
    override final def equals(a: Any) = a match {
      case that: LocalMem[_] if this.canEqual(that) => eql(that.asInstanceOf[T])
      case _ => false
    }
  }

  case class ConstReg[T<:AnyVal](const: T) extends LocalMem[ConstReg[T]] with LocalScalar with Addr {
    override def eql(that: ConstReg[T]) = this.const == that.const
    override def toString = const.toString
  }
  case class CounterReg(cchain: CUCChain, idx: Int) extends LocalMem[CounterReg] with Addr {
    override def eql(that: CounterReg) = this.cchain == that.cchain && this.idx == that.idx
    override def toString = cchain+s"($idx)"
  }

  case class ControlReg() extends LocalMem[ControlReg] {
    override def toString = s"cr$id"
  }
  case class ValidReg(cchain: CUCChain, idx: Int) extends LocalMem[ValidReg] {
    override def eql(that: ValidReg) = this.cchain == that.cchain && this.idx == that.idx
    override def toString = cchain.name+s"($idx).valid"
  }

  sealed abstract class SRAMPort[T<:LocalComponent] extends LocalMem[T]
  case class ReadAddrWire(mem: CUMemory) extends SRAMPort[ReadAddrWire] with Addr {
    override def eql(that: ReadAddrWire) = this.mem == that.mem
    override def toString = mem.name + ".readAddr"
  }
  case class WriteAddrWire(mem: CUMemory) extends SRAMPort[WriteAddrWire] with Addr {
    override def eql(that: WriteAddrWire) = this.mem == that.mem
    override def toString = mem.name + ".writeAddr"
  }
  case class FeedbackAddrReg(mem: CUMemory) extends SRAMPort[FeedbackAddrReg] with Addr {
    override def eql(that: FeedbackAddrReg) = this.mem == that.mem
    override def toString = mem.name + ".feedbackAddr"
  }
  case class FeedbackDataReg(mem: CUMemory) extends SRAMPort[FeedbackDataReg] {
    override def eql(that: FeedbackDataReg) = this.mem == that.mem
    override def toString = mem.name + ".feedbackData"
  }
  case class MemLoadReg(mem: CUMemory) extends SRAMPort[MemLoadReg] with LocalScalar {
    override def eql(that: MemLoadReg) = this.mem == that.mem
    override def toString = mem.name + ".readPort"
  }

  sealed abstract class ReduceMem[T<:LocalComponent] extends LocalMem[T]
  case class ReduceReg() extends ReduceMem[ReduceReg] {
    override def toString = s"rr$id"
  }
  case class AccumReg(init: ConstReg[_<:AnyVal]) extends ReduceMem[AccumReg] {
    override def toString = s"ar$id"
  }

  case class TempReg(x:Expr) extends LocalMem[TempReg] {
    override def toString = s"$x"
  }

  sealed abstract class LocalPort[T<:LocalComponent] extends LocalMem[T] {
    def bus: GlobalBus
  }
  case class ScalarIn(bus: ScalarBus) extends LocalPort[ScalarIn] {
    override def eql(that: ScalarIn) = this.bus == that.bus
    override def toString = bus.toString + ".sIn"
  }
  case class ScalarOut(bus: ScalarBus) extends LocalPort[ScalarOut] {
    override def eql(that: ScalarOut) = this.bus == that.bus
    override def toString = bus.toString + ".sOut"
  }
  case class VectorIn(bus: VectorBus) extends LocalPort[VectorIn] {
    override def eql(that: VectorIn) = this.bus == that.bus
    override def toString = bus.toString + ".vIn"
  }
  case class VectorOut(bus: VectorBus) extends LocalPort[VectorOut] {
    override def eql(that: VectorOut) = this.bus == that.bus
    override def toString = bus.toString + ".vOut"
  }

  // --- Counter chains
  case class CUCounter(var start: LocalScalar, var end: LocalScalar, var stride: LocalScalar, var par:Int) {
    val name = s"ctr${CUCounter.nextId()}"
  }
  object CUCounter { 
    var id: Int = 0;
    def nextId(): Int = {id += 1; id}
  }

  sealed abstract class CUCChain(val name: String) { def longString: String }
  case class CChainInstance(override val name: String, sym:Expr, counters: Seq[CUCounter]) extends CUCChain(name) {
    override def toString = name
    def longString: String = s"$name (" + counters.mkString(", ") + ")"
  }
  case class CChainCopy(override val name: String, inst: CUCChain, var owner: AbstractComputeUnit) extends CUCChain(name) {
    override def toString = s"$owner.copy($name)"
    def longString: String = this.toString
  }
  case class UnitCChain(override val name: String) extends CUCChain(name) {
    override def toString = name
    def longString: String = this.toString + " [unit]"
  }


  // --- Compute unit memories
  case class CUMemory(name: String, mem: Expr, reader: Expr, cu:AbstractComputeUnit) {
    var mode: LocalMemoryMode = SRAMMode
    var bufferDepth: Int = 1
    var banking: Option[SRAMBanking] = None
    var size = 1

    // writePort either from bus or for sram can be from a vector FIFO
    var writePort: Option[GlobalBus] = None 
    var readPort: Option[GlobalBus] = None
    var readAddr: Option[Addr] = None
    var writeAddr: Option[Addr] = None

    var writeStart: Option[LocalScalar] = None
    var writeEnd: Option[LocalScalar] = None

    var producer:Option[PseudoComputeUnit] = None
    var consumer:Option[PseudoComputeUnit] = None

    override def toString = name

    def copyMem(name: String) = {
      val copy = CUMemory(name, mem, reader, cu)
      copy.mode = this.mode
      copy.bufferDepth = this.bufferDepth
      copy.banking = this.banking
      copy.size = this.size
      copy.writePort = this.writePort 
      copy.readPort = this.readPort 
      copy.readAddr = this.readAddr
      copy.writeAddr = this.writeAddr
      copy.writeStart = this.writeStart
      copy.producer = this.producer
      copy.consumer = this.consumer
      copy
    }
  }


  // --- Pre-scheduling stages
  sealed abstract class PseudoStage { def output: Option[Expr] }
  case class DefStage(op: Expr, isReduce: Boolean = false) extends PseudoStage {
    def output = Some(op)
    override def toString = s"DefStage(${str(op)}" + (if (isReduce) " [REDUCE]" else "") + ")"
  }
  case class OpStage(op: PIROp, inputs: List[Expr], out: Expr, isReduce: Boolean = false) extends PseudoStage {
    def output = Some(out)
  }
  case class AddrStage(mem: Expr, addr: Expr) extends PseudoStage { def output = None }
  case class FifoOnWriteStage(mem: Expr, start: Option[Expr], end: Option[Expr]) extends PseudoStage { def output = None }


  // --- Scheduled stages
  case class LocalRef(stage: Int, reg: LocalComponent)

  sealed abstract class Stage {
    def inputMems: Seq[LocalComponent]
    def outputMems: Seq[LocalComponent]
    def inputRefs: Seq[LocalRef]
    def outputRefs: Seq[LocalRef]
  }
  case class MapStage(op: PIROp, var ins: Seq[LocalRef], var outs: Seq[LocalRef]) extends Stage {
    def inputMems = ins.map(_.reg)
    def outputMems = outs.map(_.reg)
    def inputRefs = ins
    def outputRefs = outs
  }
  case class ReduceStage(op: PIROp, init: ConstReg[_<:AnyVal], in: LocalRef, acc: ReduceReg) extends Stage {
    def inputMems = List(in.reg, acc)
    def outputMems = List(acc)
    def inputRefs = List(in)
    def outputRefs = Nil
  }

  // --- Compute units
  type ACU = AbstractComputeUnit
  abstract class AbstractComputeUnit {
    val name: String
    val pipe: Expr
    var style: CUStyle
    var parent: Option[AbstractComputeUnit] = None
    var innerPar: Option[Int] = None
    /*def isUnit = style match { // TODO: remove this. This should no longer be used
      case MemoryCU(i) => throw new Exception(s"isUnit is not defined on MemoryCU")
      case FringeCU(dram, mode) => throw new Exception(s"isUnit is not defined on FringeCU")
      case _ => innerPar == Some(1)
    }*/

    var cchains: Set[CUCChain] = Set.empty
    val memMap: mutable.Map[Any, CUMemory] = mutable.Map.empty
    var regs: Set[LocalComponent] = Set.empty
    var deps: Set[AbstractComputeUnit] = Set.empty

    val regTable = mutable.HashMap[Expr, LocalComponent]()
    val expTable = mutable.HashMap[LocalComponent, List[Expr]]()

    def iterators = regTable.iterator.collect{case (exp, reg: CounterReg) => (exp,reg) }
    def valids    = regTable.iterator.collect{case (exp, reg: ValidReg) => (exp,reg) }

    def mems:Set[CUMemory] = memMap.values.toSet
    def srams:Set[CUMemory] = mems.filter {_.mode==SRAMMode}
    def fifos:Set[CUMemory] = mems.filter { mem => mem.mode==VectorFIFOMode || mem.mode==ScalarFIFOMode}

    val fringeVectors = mutable.Map[String, GlobalBus]()

    def innermostIter(cc: CUCChain) = {
      val iters = iterators.flatMap{case (e,CounterReg(`cc`,i)) => Some((e,i)); case _ => None}
      if (iters.isEmpty) None  else Some(iters.reduce{(a,b) => if (a._2 > b._2) a else b}._1)
    }

    def addReg(exp: Expr, reg: LocalComponent) {
      regs += reg
      regTable += exp -> reg
      if (expTable.contains(reg)) expTable += reg -> (expTable(reg) :+ exp)
      else                        expTable += reg -> List(exp)
    }
    def get(x: Expr): Option[LocalComponent] = {
      if (regTable.contains(x)) regTable.get(x)
      else if (isConstant(x)) {
        val c = extractConstant(x)
        addReg(x, c)
        Some(c)
      }
      else None
    }
    def getOrElseUpdate(x: Expr)(func: => LocalComponent):LocalComponent = this.get(x) match {
      case Some(reg) if regs.contains(reg) => reg // On return this mapping if it is valid
      case _ =>
        val reg = func
        addReg(x, reg)
        reg
    }
  }

  type CU = ComputeUnit
  case class ComputeUnit(name: String, pipe: Expr, var style: CUStyle) extends AbstractComputeUnit {
    val writeStages   = mutable.HashMap[Seq[CUMemory], mutable.ArrayBuffer[Stage]]()
    val readStages    = mutable.HashMap[Seq[CUMemory], mutable.ArrayBuffer[Stage]]()
    val computeStages = mutable.ArrayBuffer[Stage]()
    val controlStages = mutable.ArrayBuffer[Stage]()

    def parentCU: Option[CU] = parent.flatMap{case cu: CU => Some(cu); case _ => None}

    def allStages: Iterator[Stage] = writeStages.valuesIterator.flatMap(_.iterator) ++ 
                                     readStages.valuesIterator.flatMap(_.iterator) ++
                                     computeStages.iterator ++
                                     controlStages.iterator
    var isDummy: Boolean = false
    def lanes: Int = style match {
      case _:MemoryCU => 1
      case _:FringeCU => 0
      case _ => if (innerPar.isDefined) innerPar.get else 1
    }
    def allParents: Iterable[CU] = parentCU ++ parentCU.map(_.allParents).getOrElse(Nil)
    def isPMU = style.isInstanceOf[MemoryCU]
    def isPCU = !isPMU && !style.isInstanceOf[FringeCU]
  }

  type PCU = PseudoComputeUnit
  case class PseudoComputeUnit(name: String, pipe: Expr, var style: CUStyle) extends AbstractComputeUnit {
    val writeStages = mutable.HashMap[Seq[CUMemory], (Expr, Seq[PseudoStage])]() // List(mem) -> (writerPipe, List[Stages])
    val readStages = mutable.HashMap[Seq[CUMemory], (Expr, Seq[PseudoStage])]() // List(mem) -> (readerPipe, List[Stages])
    val computeStages = mutable.ArrayBuffer[PseudoStage]()
    val remoteReadStages = mutable.Set[Expr]() // reg read, fifo deq
    val remoteWriteStages = mutable.Set[Expr]() // reg write, fifo enq

    def sram = {
      assert(style.isInstanceOf[MemoryCU], s"Only MemoryCU has sram. cu:$this")
      val srams = mems.filter{ _.mode == SRAMMode }
      assert(srams.size==1, s"Each MemoryCU should only has one sram, srams:[${srams.mkString(",")}]")
      srams.head
    }

    def copyToConcrete(): ComputeUnit = {
      val cu = ComputeUnit(name, pipe, style)
      cu.innerPar = this.innerPar
      cu.parent = this.parent
      cu.cchains ++= this.cchains
      cu.memMap ++= this.memMap
      cu.regs ++= this.regs
      cu.deps ++= this.deps
      cu.regTable ++= this.regTable
      cu.expTable ++= this.expTable
      cu
    }
  }


  sealed abstract class PIROp
  case object PIRALUMux  extends PIROp { override def toString = "Mux"    }
  case object PIRBypass  extends PIROp { override def toString = "Bypass" }
  case object PIRFixAdd  extends PIROp { override def toString = "FixAdd" }
  case object PIRFixSub  extends PIROp { override def toString = "FixSub" }
  case object PIRFixMul  extends PIROp { override def toString = "FixMul" }
  case object PIRFixDiv  extends PIROp { override def toString = "FixDiv" }
  case object PIRFixMod  extends PIROp { override def toString = "FixMod" }
  case object PIRFixLt   extends PIROp { override def toString = "FixLt"  }
  case object PIRFixLeq  extends PIROp { override def toString = "FixLeq" }
  case object PIRFixEql  extends PIROp { override def toString = "FixEql" }
  case object PIRFixNeq  extends PIROp { override def toString = "FixNeq" }
  case object PIRFixMin  extends PIROp { override def toString = "FixMin" }
  case object PIRFixMax  extends PIROp { override def toString = "FixMax" }
  case object PIRFixNeg  extends PIROp { override def toString = "FixNeg" }
                                                                        
  case object PIRFltAdd  extends PIROp { override def toString = "FltAdd" }
  case object PIRFltSub  extends PIROp { override def toString = "FltSub" }
  case object PIRFltMul  extends PIROp { override def toString = "FltMul" }
  case object PIRFltDiv  extends PIROp { override def toString = "FltDiv" }
  case object PIRFltLt   extends PIROp { override def toString = "FltLt"  }
  case object PIRFltLeq  extends PIROp { override def toString = "FltLeq" }
  case object PIRFltEql  extends PIROp { override def toString = "FltEql" }
  case object PIRFltNeq  extends PIROp { override def toString = "FltNeq" }
  case object PIRFltExp  extends PIROp { override def toString = "FltExp" }
  case object PIRFltLog  extends PIROp { override def toString = "FltLog" }
  case object PIRFltSqrt extends PIROp { override def toString = "FltSqr" }
  case object PIRFltAbs  extends PIROp { override def toString = "FltAbs" }
  case object PIRFltMin  extends PIROp { override def toString = "FltMin" }
  case object PIRFltMax  extends PIROp { override def toString = "FltMax" }
  case object PIRFltNeg  extends PIROp { override def toString = "FltNeg" }
                                                                        
  case object PIRBitAnd  extends PIROp { override def toString = "BitAnd" }
  case object PIRBitOr   extends PIROp { override def toString = "BitOr"  }
}
