// See LICENSE.txt for license details.
package templates

import chisel3._

//A n-stage Parallel controller
class Parallel(val n: Int, val isFSM: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val input = new Bundle {
      val enable       = Input(Bool())
      val stageDone    = Vec(n, Input(Bool()))
      val forever      = Input(Bool())
      val rst          = Input(Bool())
      val hasStreamIns = Input(Bool()) // Not used, here for codegen compatibility

      // FSM signals
      val nextState = Input(UInt(32.W))
    }
    val output = new Bundle {
      val done        = Output(Bool())
      val stageEnable = Vec(n, Output(Bool()))
      val rst_en      = Output(Bool())
      val ctr_inc     = Output(Bool())
      // FSM signals
      val state = Output(UInt(32.W))
    }
  })

  // 0: INIT, 1: Separate reset from enables, 2 stages enabled, 3: DONE
  // Name the universal states
  val initState    = 0
  val bufferState  = initState + 1
  val runningState = bufferState + 1
  val doneState    = runningState + 1

  // Create FF for holding state
  val stateFF = Module(new FF(2))
  stateFF.io.input.enable := true.B
  stateFF.io.input.init := 0.U
  stateFF.io.input.reset := io.input.rst
  val state = stateFF.io.output.data

  // Create vector of registers for holding stage dones
  val doneFF = List.tabulate(n) { i =>
    val ff = Module(new SRFF())
    ff.io.input.set := io.input.stageDone(i)
    ff.io.input.asyn_reset := false.B
    ff.io.input.reset := state === doneState.U
    ff
  }
  val doneMask = doneFF.map { _.io.output.data }

  // // Provide default value for enable and doneClear
  // io.output.stageEnable.foreach { _ := Bool(false) }

  io.output.rst_en := false.B

  io.output.stageEnable.foreach { s =>
    s := false.B
  }
  // State Machine
  when(io.input.enable) {
    when(state === initState.U) { // INIT -> RESET
      stateFF.io.input.data := bufferState.U
      io.output.rst_en := true.B
    }.elsewhen(state === bufferState.U) { // Not sure if this state is needed for stream
        io.output.rst_en := false.B
        stateFF.io.input.data := runningState.U
      }
      .elsewhen(state === runningState.U) { // STEADY
        (0 until n).foreach { i =>
          io.output.stageEnable(i) := Mux(io.input.forever,
                                          true.B,
                                          ~doneMask(i))
        }

        val doneTree = doneMask.reduce { _ & _ }
        when(doneTree === 1.U) {
          stateFF.io.input.data := Mux(io.input.forever,
                                       runningState.U,
                                       doneState.U)
        }.otherwise {
          stateFF.io.input.data := state
        }
      }
      .elsewhen(state === doneState.U) { // DONE
        stateFF.io.input.data := initState.U
      }
      .otherwise {
        stateFF.io.input.data := state
      }
  }.otherwise {
    stateFF.io.input.data := initState.U
    (0 until n).foreach { i =>
      io.output.stageEnable(i) := false.B
    }
  }

  // Output logic
  io.output.done := state === doneState.U
  io.output.ctr_inc := false.B // No counters for parallels (BUT MAYBE NEEDED FOR STREAMPIPES)
}
// class ParallelTests(c: Parallel) extends PlasticineTester(c) {
//   val numIter = 5
//   val stageIterCount = List.tabulate(c.numInputs) { i => math.abs(rnd.nextInt) % 10 }
//   println(s"stageIterCount: $stageIterCount")

//   def executeStages(s: List[Int]) {
//     val numCycles = s.map { stageIterCount(_) }
//     var elapsed = 0
//     var done: Int = 0
//     while (done != s.size) {
//       c.io.input.stageDone.foreach { poke(_, 0) }
//       step(1)
//       elapsed += 1
//       for (i <- 0 until s.size) {
//         if (numCycles(i) == elapsed) {
//           println(s"[Stage ${s(i)} Finished execution at $elapsed")
//           poke(c.io.input.stageDone(s(i)), 1)
//           done += 1
//         }
//       }
//     }
//     c.io.input.stageDone.foreach { poke(_, 1) }
//   }

//   def handleStageEnables = {
//     val stageEnables = c.io.output.stageEnable.map { peek(_).toInt }
//     val activeStage = stageEnables.zipWithIndex.filter { _._1 == 1 }.map { _._2 }
//     executeStages(activeStage.toList)
//   }

//   // Start
//   poke(c.io.input.enable, 1)

//   var done = peek(c.io.done).toInt
//   var numCycles = 0
//   while ((done != 1) & (numCycles < 100)) {
//     handleStageEnables
//     done = peek(c.io.done).toInt
//     step(1)
//     numCycles += 1
//   }
// }
// object ParallelTest {

//   def main(args: Array[String]): Unit = {
//     val (appArgs, chiselArgs) = args.splitAt(args.indexOf("end"))

//     val numInputs = 2
//     chiselMainTest(chiselArgs, () => Module(new Parallel(numInputs))) {
//       c => new ParallelTests(c)
//     }
//   }
// }
