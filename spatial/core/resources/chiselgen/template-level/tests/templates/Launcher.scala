// See LICENSE.txt for license details.
package templates

import types._
import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}
import utils.TemplateRunner
import scala.reflect.runtime._
import scala.reflect.runtime.universe

// Ripped from http://stackoverflow.com/questions/1469958/scala-how-do-i-dynamically-instantiate-an-object-and-invoke-a-method-using-refl
object Inst {

  def apply(className: String, arg: Any) = {
    val runtimeMirror: universe.Mirror =
      universe.runtimeMirror(getClass.getClassLoader)
    val classSymbol: universe.ClassSymbol =
      runtimeMirror.classSymbol(Class.forName(s"templates.${className}"))
    val classMirror: universe.ClassMirror =
      runtimeMirror.reflectClass(classSymbol)
    if (classSymbol.companion.toString() == "<none>") {
      println(s"Info: $className has no companion object")
      val constructors =
        classSymbol.typeSignature.members.filter(_.isConstructor).toList
      if (constructors.length > 1) {
        println(s"Info: $className has several constructors")
      } else {
        val constructorMirror = classMirror.reflectConstructor(
          constructors.head.asMethod) // we can reuse it
        constructorMirror()
      }
    } else {
      val companionSymbol = classSymbol.companion
      println(s"Info: $className has companion object $companionSymbol")
      // TBD
    }
  }
}

// Start args
object Arguments {
  val UIntAccum = List(
    (32, "add"),
    (32, "min"),
    (32, "max")
  )
  val SpecialAccum = List(
    (0, "add", "UInt", List(32)),
    (1, "add", "FixedPoint", List(1, 16, 16))
    // (18,"max","FloatingPoint",List(24,8))
  )
  val FF = List(
    16,
    32,
    64
  )
  val NBufFF = List(
    (3, 32),
    (2, 32),
    (10, 32)
  )
  val FFNoInit = List(
    32
  )
  val FFNoInitNoReset = List(
    32
  )
  val FFNoReset = List(
    32
  )
  val TFF = List(
    "null"
  )
  val SRFF = List(
    "null"
  )
  val MemController = List(
    1
  )
  val FIFO = List(
    (1, 1, 10),
    (2, 2, 30),
    (4, 4, 52),
    (4, 1, 56),
    (1, 4, 56),
    (3, 6, 48),
    (6, 3, 48)
  )
  val SingleCounter = List(
    1,
    3
  )
  val FixedPointTester = List(
    (false, 16, 16)
    // (true, 8, 8)
  )
  val Counter = List(
    List(1, 1, 1),
    List(2, 2, 2),
    List(4, 1, 1)
  )
  val Seqpipe = List(
    1,
    10
  )
  val Metapipe = List(
    1,
    2,
    5,
    8
  )
  val Delayer = List(
    10,
    3,
    1,
    0
  )
  val Mem1D = List(
    50,
    1024
  )
  val MemND = List(
    List(4, 8),
    List(5, 9)
  )
  val SRAM = List( // Contain each set of args in its own list
    (List(1, 16), 32, List(1, 1), List(1, 1), List(1), List(1), BankedMemory),
    (List(1, 16), 32, List(1, 2), List(1, 1), List(1), List(2), BankedMemory),
    (List(16, 16), 32, List(1, 2), List(1, 1), List(2), List(2), BankedMemory),
    (List(16, 16), 32, List(1, 1), List(1, 1), List(1), List(1), BankedMemory)
  )
  val NBufSRAM = List(
    (List(8, 12),
     2,
     32,
     List(1, 1),
     List(1, 1),
     List(1),
     List(1),
     List(1),
     1,
     BankedMemory),
    (List(8, 12),
     3,
     32,
     List(1, 1),
     List(1, 1),
     List(1),
     List(1),
     List(2),
     1,
     BankedMemory),
    (List(8, 12),
     3,
     32,
     List(1, 2),
     List(1, 2),
     List(2),
     List(2),
     List(2),
     2,
     BankedMemory)
  )
  val Innerpipe = List(
    2
  )
  val Parallel = List(
    3
  )
  val ParallelShiftReg = List( // Arguments are (size, stride, parallelism)
                              (5, 2, 5),
                              (3, 1, 3))
  val ParallelShiftRegFile = List( // Arguments are (size, stride)
                                  (1, 6, 1, 1),
                                  (1, 3, 1, 1),
                                  (3, 7, 1, 1))
  val RegFile = List( // Arguments are (size)
    (10))
  val LineBuffer =
    List( // Arguments are (#lines, line size, #extra rows to buffer, COL_PAR, ROW_PAR)
         (3, 10, 1, 1, 1, 1, 3),
         (5, 10, 2, 1, 1, 1, 5))
}
// End args

object Launcher {
  var templates: Map[String, String => Boolean] = Map()

  // Start launcher
  templates = templates ++ Arguments.UIntAccum.zipWithIndex.map {
    case (arg, i) =>
      (s"UIntAccum$i" -> { (backendName: String) =>
        Driver(() => new UIntAccum(arg), "verilator") { (c) =>
          new UIntAccumTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.SpecialAccum.zipWithIndex.map {
    case (arg, i) =>
      (s"SpecialAccum$i" -> { (backendName: String) =>
        Driver(() => new SpecialAccum(arg), "verilator") { (c) =>
          new SpecialAccumTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.FF.zipWithIndex.map {
    case (arg, i) =>
      (s"FF$i" -> { (backendName: String) =>
        Driver(() => new FF(arg), "verilator") { (c) =>
          new FFTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.NBufFF.zipWithIndex.map {
    case (arg, i) =>
      (s"NBufFF$i" -> { (backendName: String) =>
        Driver(() => new NBufFF(arg), "verilator") { (c) =>
          new NBufFFTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.FFNoInit.zipWithIndex.map {
    case (arg, i) =>
      (s"FFNoInit$i" -> { (backendName: String) =>
        Driver(() => new FFNoInit(arg), "verilator") { (c) =>
          new FFNoInitTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.FFNoInitNoReset.zipWithIndex.map {
    case (arg, i) =>
      (s"FFNoInitNoReset$i" -> { (backendName: String) =>
        Driver(() => new FFNoInitNoReset(arg), "verilator") { (c) =>
          new FFNoInitNoResetTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.FFNoReset.zipWithIndex.map {
    case (arg, i) =>
      (s"FFNoReset$i" -> { (backendName: String) =>
        Driver(() => new FFNoReset(arg), "verilator") { (c) =>
          new FFNoResetTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.TFF.zipWithIndex.map {
    case (arg, i) =>
      (s"TFF$i" -> { (backendName: String) =>
        Driver(() => new TFF(arg), "verilator") { (c) =>
          new TFFTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.SRFF.zipWithIndex.map {
    case (arg, i) =>
      (s"SRFF$i" -> { (backendName: String) =>
        Driver(() => new SRFF(arg), "verilator") { (c) =>
          new SRFFTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.MemController.zipWithIndex.map {
    case (arg, i) =>
      (s"MemController$i" -> { (backendName: String) =>
        Driver(() => new MemController(arg), "verilator") { (c) =>
          new MemControllerTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.FIFO.zipWithIndex.map {
    case (arg, i) =>
      (s"FIFO$i" -> { (backendName: String) =>
        Driver(() => new FIFO(arg), "verilator") { (c) =>
          new FIFOTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.SingleCounter.zipWithIndex.map {
    case (arg, i) =>
      (s"SingleCounter$i" -> { (backendName: String) =>
        Driver(() => new SingleCounter(arg), "verilator") { (c) =>
          new SingleCounterTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.FixedPointTester.zipWithIndex.map {
    case (arg, i) =>
      (s"FixedPointTester$i" -> { (backendName: String) =>
        Driver(() => new FixedPointTester(arg), "verilator") { (c) =>
          new FixedPointTesterTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.Counter.zipWithIndex.map {
    case (arg, i) =>
      (s"Counter$i" -> { (backendName: String) =>
        Driver(() => new Counter(arg), "verilator") { (c) =>
          new CounterTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.Seqpipe.zipWithIndex.map {
    case (arg, i) =>
      (s"Seqpipe$i" -> { (backendName: String) =>
        Driver(() => new Seqpipe(arg), "verilator") { (c) =>
          new SeqpipeTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.Metapipe.zipWithIndex.map {
    case (arg, i) =>
      (s"Metapipe$i" -> { (backendName: String) =>
        Driver(() => new Metapipe(arg), "verilator") { (c) =>
          new MetapipeTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.Delayer.zipWithIndex.map {
    case (arg, i) =>
      (s"Delayer$i" -> { (backendName: String) =>
        Driver(() => new Delayer(arg), "verilator") { (c) =>
          new DelayerTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.Mem1D.zipWithIndex.map {
    case (arg, i) =>
      (s"Mem1D$i" -> { (backendName: String) =>
        Driver(() => new Mem1D(arg), "verilator") { (c) =>
          new Mem1DTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.MemND.zipWithIndex.map {
    case (arg, i) =>
      (s"MemND$i" -> { (backendName: String) =>
        Driver(() => new MemND(arg), "verilator") { (c) =>
          new MemNDTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.SRAM.zipWithIndex.map {
    case (arg, i) =>
      (s"SRAM$i" -> { (backendName: String) =>
        Driver(() => new SRAM(arg), "verilator") { (c) =>
          new SRAMTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.NBufSRAM.zipWithIndex.map {
    case (arg, i) =>
      (s"NBufSRAM$i" -> { (backendName: String) =>
        Driver(() => new NBufSRAM(arg), "verilator") { (c) =>
          new NBufSRAMTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.Innerpipe.zipWithIndex.map {
    case (arg, i) =>
      (s"Innerpipe$i" -> { (backendName: String) =>
        Driver(() => new Innerpipe(arg), "verilator") { (c) =>
          new InnerpipeTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.Parallel.zipWithIndex.map {
    case (arg, i) =>
      (s"Parallel$i" -> { (backendName: String) =>
        Driver(() => new Parallel(arg), "verilator") { (c) =>
          new ParallelTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.ParallelShiftReg.zipWithIndex.map {
    case (arg, i) =>
      (s"ParallelShiftReg$i" -> { (backendName: String) =>
        Driver(() => new ParallelShiftReg(arg), "verilator") { (c) =>
          new ParallelShiftRegTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.ParallelShiftRegFile.zipWithIndex.map {
    case (arg, i) =>
      (s"ParallelShiftRegFile$i" -> { (backendName: String) =>
        Driver(() => new ParallelShiftRegFile(arg), "verilator") { (c) =>
          new ParallelShiftRegFileTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.RegFile.zipWithIndex.map {
    case (arg, i) =>
      (s"RegFile$i" -> { (backendName: String) =>
        Driver(() => new RegFile(arg), "verilator") { (c) =>
          new RegFileTests(c)
        }
      })
  }.toMap

  templates = templates ++ Arguments.LineBuffer.zipWithIndex.map {
    case (arg, i) =>
      (s"LineBuffer$i" -> { (backendName: String) =>
        Driver(() => new LineBuffer(arg), "verilator") { (c) =>
          new LineBufferTests(c)
        }
      })
  }.toMap

// End launcher

  def main(args: Array[String]): Unit = {
    TemplateRunner(templates, args)
  }
}

//lastline
