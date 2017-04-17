// See LICENSE.txt for license details.
package templates

import chisel3._
import chisel3.util.{log2Ceil, isPow2}
import chisel3.internal.sourceinfo._
import types._

object ops {
  implicit class UIntOps(val b: UInt) {
    // Define number so that we can be compatible with FixedPoint type
    def number = {
      b
    }

    // override def connect (rawop: Data)(implicit sourceInfo: SourceInfo, connectionCompileOptions: chisel3.core.CompileOptions): Unit = {
    //   rawop match {
    //     case op: FixedPoint =>
    //       b := op.number
    //     case op: UInt =>
    //       b := op
    //   }
    // }

    def <(c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) < c
    }

    def >(c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) > c
    }

    def ===(c: FixedPoint): Bool = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) === c
    }

    def -(c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) - c
    }

    def +(c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) + c
    }

    def *(c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) * c
    }

    def /(c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) / c
    }

    def %(c: FixedPoint): FixedPoint = {
      Utils.FixedPoint(c.s, b.getWidth max c.d, c.f, b) % c
    }

    def FP(s: Boolean, d: Int, f: Int): FixedPoint = {
      Utils.FixedPoint(s, d, f, b)
    }

  }
  implicit class IntOps(val b: Int) {
    def FP(s: Boolean, d: Int, f: Int): FixedPoint = {
      Utils.FixedPoint(s, d, f, b)
    }
    def FP(s: Int, d: Int, f: Int): FixedPoint = {
      Utils.FixedPoint(s, d, f, b)
    }
  }
}

object Utils {
  def delay[T <: chisel3.core.Data](sig: T, length: Int): T = {
    if (length == 0) {
      sig
    } else {
      val regs = (0 until length).map { i =>
        RegInit(0.U)
      } // TODO: Make this type T
      sig match {
        case s: Bool =>
          regs(0) := Mux(s, 1.U, 0.U)
          (length - 1 until 0 by -1).map { i =>
            regs(i) := regs(i - 1)
          }
          (regs(length - 1) === 1.U).asInstanceOf[T]
        case s: UInt =>
          regs(0) := s
          (length - 1 until 0 by -1).map { i =>
            regs(i) := regs(i - 1)
          }
          (regs(length - 1)).asInstanceOf[T]
      }
    }
  }

  // Helper for making fixedpt when you know the value at creation time
  def FixedPoint[T](s: Int, d: Int, f: Int, init: T): types.FixedPoint = {
    FixedPoint(s > 0, d, f, init)
  }
  def FixedPoint[T](s: Boolean, d: Int, f: Int, init: T): types.FixedPoint = {
    val cst = Wire(new types.FixedPoint(s, d, f))
    init match {
      case i: Double =>
        cst.number := (i * scala.math.pow(2, f)).toLong
          .S((d + f + 1).W)
          .asUInt()
      case i: UInt       => cst.number := i
      case i: FixedPoint => cst.number := i.number
      case i: Int =>
        cst.number := (i * scala.math.pow(2, f)).toLong
          .S((d + f + 1).W)
          .asUInt()
    }
    cst
  }

  def Cat[T1 <: chisel3.core.Data, T2 <: chisel3.core.Data](x1: T1,
                                                            x2: T2): UInt = {
    val raw_x1 = x1 match {
      case x: UInt       => x
      case x: FixedPoint => x.number
    }
    val raw_x2 = x2 match {
      case x: UInt       => x
      case x: FixedPoint => x.number
    }

    util.Cat(raw_x1, raw_x2)
  }

  def Cat[T1 <: chisel3.core.Data,
          T2 <: chisel3.core.Data,
          T3 <: chisel3.core.Data](x1: T1, x2: T2, x3: T3): UInt = {
    val raw_x1 = x1 match {
      case x: UInt       => x
      case x: FixedPoint => x.number
    }
    val raw_x2 = x2 match {
      case x: UInt       => x
      case x: FixedPoint => x.number
    }
    val raw_x3 = x3 match {
      case x: UInt       => x
      case x: FixedPoint => x.number
    }

    util.Cat(raw_x1, raw_x2, raw_x3)
  }

  def mux[T1 <: chisel3.core.Data, T2 <: chisel3.core.Data](cond: T1,
                                                            op1: T2,
                                                            op2: T2): T2 = {
    val bool_cond = cond match {
      case x: Bool => x
      case x: UInt => x(0)
    }
    Mux(bool_cond, op1, op2)
  }

  def min[T <: chisel3.core.Data](a: T, b: T): T = {
    (a, b) match {
      case (aa: UInt, bb: UInt) => Mux(aa < bb, a, b)
      case (_, _)               => a // TODO: implement for other types
    }
  }

  def max[T <: chisel3.core.Data](a: T, b: T): T = {
    (a, b) match {
      case (aa: UInt, bb: UInt) => Mux(aa > bb, a, b)
      case (_, _)               => a // TODO: implement for other types
    }
  }

  def log2Up[T](number: T): Int = {
    number match {
      case n: Int => 1 max log2Ceil(1 max n)
      case n: scala.math.BigInt =>
        1 max log2Ceil(1.asInstanceOf[scala.math.BigInt] max n)
    }
  }
  // def toFix[T <: chisel3.core.Data](a: T): FixedPoint = {
  //   a match {
  //     case aa: FixedPoint => Mux(aa > bb, a, b)
  //     case a => a // TODO: implement for other types
  //   }
  // }
}
