// /*
//  Copyright (c) 2011, 2012, 2013, 2014 The University of Sydney.
//  All Rights Reserved.  Redistribution and use in source and
//  binary forms, with or without modification, are permitted
//  provided that the following conditions are met:
//     * Redistributions of source code must retain the above
//       copyright notice, this list of conditions and the following
//       two paragraphs of disclaimer.
//     * Redistributions in binary form must reproduce the above
//       copyright notice, this list of conditions and the following
//       two paragraphs of disclaimer in the documentation and/or other materials
//       provided with the distribution.
//     * Neither the name of the Regents nor the names of its contributors
//       may be used to endorse or promote products derived from this
//       software without specific prior written permission.
//  IN NO EVENT SHALL REGENTS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
//  SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
//  ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
//  REGENTS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//  REGENTS SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT
//  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
//  A PARTICULAR PURPOSE. THE SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF
//  ANY, PROVIDED HEREUNDER IS PROVIDED "AS IS". REGENTS HAS NO OBLIGATION
//  TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
//  MODIFICATIONS.
// */

package types

import chisel3._
import templates._
import templates.ops._
import chisel3.util

import scala.language.experimental.macros

import chisel3.internal._
import chisel3.internal.firrtl._
import chisel3.internal.sourceinfo._
import chisel3.internal.firrtl.PrimOp.AsUIntOp

// Raw numbers
class RawBits(b: Int) extends Bundle {
  val raw = UInt(b.W)

  // Conversions
  def storeFix(dst: FixedPoint): Unit = {
    assert(dst.d + dst.f == b)
    dst.number := raw
  }

  // Arithmetic

  override def cloneType =
    (new RawBits(b)).asInstanceOf[this.type] // See chisel3 bug 358
}

// Fixed point numbers
class FixedPoint(val s: Boolean, val d: Int, val f: Int) extends Bundle {
  // Overloaded
  def this(s: Int, d: Int, f: Int) = this(s == 1, d, f)
  def this(tuple: (Boolean, Int, Int)) = this(tuple._1, tuple._2, tuple._3)

  // Properties
  val number         = UInt((d + f).W)
  val debug_overflow = Bool()

  // Conversions
  def storeRaw(dst: RawBits): Unit = {
    dst.raw := number
  }
  def cast(dst: FixedPoint,
           rounding: String = "truncate",
           saturating: String = "lazy"): Unit = {
    val new_width = dst.d + dst.f
    val new_raw   = Wire(UInt(new_width.W))

    // Compute new frac part
    // val new_frac = Wire(UInt(dst.f.W))
    val new_frac = if (dst.f < f) { // shrink decimals
      rounding match {
        case "truncate" =>
          val shave = f - dst.f
          number(shave + dst.f - 1, shave)
        // (0 until dst.f).map{ i => number(shave + i)*scala.math.pow(2,i).toInt.U }.reduce{_+_}
        case "unbiased" =>
          0.U(dst.f.W)
        // TODO: Add rng
        case "biased" =>
          0.U(dst.f.W)
        // TODO: force direction
        case _ =>
          0.U(dst.f.W)
        // TODO: throw error
      }
    } else if (dst.f > f) { // expand decimals
      val expand = dst.f - f
      util.Cat(number(f, 0), 0.U(expand.W))
      // (0 until dst.f).map{ i => if (i < expand) {0.U} else {number(i - expand)*scala.math.pow(2,i).toInt.U}}.reduce{_+_}
    } else { // keep same
      if (dst.f > 0) number(dst.f - 1, 0) else (0.U(1.W))
      // (0 until dst.f).map{ i => number(i)*scala.math.pow(2,i).toInt.U }.reduce{_+_}
    }

    // Compute new dec part
    val new_dec = if (dst.d < d) { // shrink decimals
      saturating match {
        case "lazy" =>
          val shave = d - dst.d
          dst.debug_overflow := (0 until shave)
            .map { i =>
              number(d + f - 1 - i)
            }
            .reduce { _ || _ }
          number(dst.d + f - 1, f)
        // (0 until dst.d).map{i => number(f + i) * scala.math.pow(2,i).toInt.U}.reduce{_+_}
        case "saturation" =>
          // TODO: Do something good
          0.U(dst.d.W)
        case _ =>
          0.U(dst.d.W)
      }
    } else if (dst.d > d) { // expand decimals
      val expand     = dst.d - d
      val sgn_extend = if (s) { number(d + f - 1) } else { 0.U(1.W) }
      util.Cat(util.Fill(expand, sgn_extend), number(f + d - 1, f))
      // (0 until dst.d).map{ i => if (i >= dst.d - expand) {sgn_extend*scala.math.pow(2,i).toInt.U} else {number(i+f)*scala.math.pow(2,i).toInt.U }}.reduce{_+_}
    } else { // keep same
      number(f + d - 1, f)
      // (0 until dst.d).map{ i => number(i + f)*scala.math.pow(2,i).toInt.U }.reduce{_+_}
    }

    val ftest = 0.U(4.W)
    val dtest = 1.U(4.W)
    if (dst.f > 0) {
      dst.number := util.Cat(new_dec, new_frac)
    } else {
      dst.number := new_dec
    }

    // dst.number := util.Cat(new_dec, new_frac) //new_frac + new_dec*(scala.math.pow(2,dst.f).toInt.U)

  }

  // Arithmetic
  override def connect(rawop: Data)(
      implicit sourceInfo: SourceInfo,
      connectionCompileOptions: chisel3.core.CompileOptions): Unit = {
    rawop match {
      case op: FixedPoint =>
        number := op.number
      case op: UInt =>
        number := op
    }
  }

  def +[T](rawop: T): FixedPoint = {
    rawop match {
      case op: FixedPoint =>
        // Compute upcasted type and return type
        val upcasted_type =
          (op.s | s, scala.math.max(op.d, d) + 1, scala.math.max(op.f, f))
        val return_type =
          (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
        // Get upcasted operators
        val full_result = Wire(new FixedPoint(upcasted_type))
        // Do upcasted operation
        full_result.number := this.number + op.number
        // Downcast to result
        val result = Wire(new FixedPoint(return_type))
        full_result.cast(result)
        result
      case op: UInt =>
        val op_cast =
          Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
        this + op_cast
    }
  }

  def -[T](rawop: T): FixedPoint = {
    rawop match {
      case op: FixedPoint =>
        // Compute upcasted type and return type
        val upcasted_type =
          (op.s | s, scala.math.max(op.d, d) + 1, scala.math.max(op.f, f))
        val return_type =
          (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
        // Get upcasted operators
        val full_result = Wire(new FixedPoint(upcasted_type))
        // Do upcasted operation
        full_result.number := this.number - op.number
        // Downcast to result
        val result = Wire(new FixedPoint(return_type))
        full_result.cast(result)
        result
      case op: UInt =>
        val op_cast =
          Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
        this - op_cast

    }
  }

  def *[T](rawop: T): FixedPoint = {
    rawop match {
      case op: FixedPoint =>
        // Compute upcasted type and return type
        val upcasted_type = (op.s | s, op.d + d, op.f + f)
        val return_type =
          (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
        // Get upcasted operators
        val full_result = Wire(new FixedPoint(upcasted_type))
        // Do upcasted operation
        val expanded_self = Utils.Cat(
          Mux(this.isNeg(),
              (scala.math.pow(2, op.d + op.f) - 1).toLong.U((op.d + op.f).W),
              0.U((op.d + op.f).W)),
          this.number)
        val expanded_op = Utils.Cat(
          Mux(op.isNeg(),
              (scala.math.pow(2, d + f) - 1).toLong.U((d + f).W),
              0.U((d + f).W)),
          op.number)
        full_result.number := expanded_self * expanded_op

        // Downcast to result
        val result = Wire(new FixedPoint(return_type))
        full_result.cast(result)
        result
      case op: UInt =>
        val op_cast =
          Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
        this * op_cast
    }
  }

  def /[T](rawop: T): FixedPoint = {
    rawop match {
      case op: FixedPoint =>
        if (op.f + f == 0) {
          (this.number / op.number)
            .FP(op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
        } else {
          // Compute upcasted type and return type
          val upcasted_type = (op.s | s, op.d + d, op.f + f + 1)
          val return_type =
            (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
          // Get upcasted operators
          val full_result = Wire(new FixedPoint(upcasted_type))
          // Do upcasted operation
          if (op.s | s) {
            val numerator   = util.Cat(this.number, 0.U((op.f + f + 1).W)).asSInt
            val denominator = op.number.asSInt
            full_result.number := (numerator / denominator).asUInt
          } else {
            val numerator   = util.Cat(this.number, 0.U((op.f + f + 1).W))
            val denominator = op.number
            full_result.number := (numerator / denominator) // Not sure why we need the +1 in pow2
          }
          // Downcast to result
          val result = Wire(new FixedPoint(return_type))
          full_result.cast(result)
          result
        }
      case op: UInt =>
        val op_cast =
          Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
        this / op_cast
    }
  }

  def %[T](rawop: T): FixedPoint = {
    rawop match {
      case op: FixedPoint =>
        // Compute upcasted type and return type
        val upcasted_type = (op.s | s, op.d + d, op.f + f)
        val return_type =
          (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
        // Get upcasted operators
        val full_result = Wire(new FixedPoint(upcasted_type))
        // Do upcasted operation
        full_result.number := this.number % op.number // Not sure why we need the +1 in pow2
        // Downcast to result
        val result = Wire(new FixedPoint(return_type))
        full_result.cast(result)
        result
      case op: UInt =>
        val op_cast =
          Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
        this % op_cast

    }
  }

  def <[T](rawop: T): Bool = { // TODO: Probably completely wrong for signed fixpts
    rawop match {
      case op: FixedPoint =>
        // Compute upcasted type and return type
        val upcasted_type =
          (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
        // Get upcasted operators
        val lhs = Wire(new FixedPoint(upcasted_type))
        val rhs = Wire(new FixedPoint(upcasted_type))
        this.cast(lhs)
        op.cast(rhs)
        if (op.s | s) { lhs.number.asSInt < rhs.number.asSInt } else {
          lhs.number < rhs.number
        }
      case op: UInt =>
        val op_cast =
          Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
        this < op_cast
    }
  }

  def <=[T](rawop: T): Bool = { // TODO: Probably completely wrong for signed fixpts
    rawop match {
      case op: FixedPoint =>
        // Compute upcasted type and return type
        val upcasted_type =
          (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
        // Get upcasted operators
        val lhs = Wire(new FixedPoint(upcasted_type))
        val rhs = Wire(new FixedPoint(upcasted_type))
        this.cast(lhs)
        op.cast(rhs)
        if (op.s | s) { lhs.number.asSInt <= rhs.number.asSInt } else {
          lhs.number <= rhs.number
        }
      case op: UInt =>
        val op_cast =
          Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
        this <= op_cast
    }
  }

  def >[T](rawop: T): Bool = { // TODO: Probably completely wrong for signed fixpts
    rawop match {
      case op: FixedPoint =>
        // Compute upcasted type and return type
        val upcasted_type =
          (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
        // Get upcasted operators
        val lhs = Wire(new FixedPoint(upcasted_type))
        val rhs = Wire(new FixedPoint(upcasted_type))
        this.cast(lhs)
        op.cast(rhs)
        if (op.s | s) { lhs.number.asSInt > rhs.number.asSInt } else {
          lhs.number > rhs.number
        }
      case op: UInt =>
        val op_cast =
          Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
        this > op_cast
    }
  }

  def >=[T](rawop: T): Bool = { // TODO: Probably completely wrong for signed fixpts
    rawop match {
      case op: FixedPoint =>
        // Compute upcasted type and return type
        val upcasted_type =
          (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
        // Get upcasted operators
        val lhs = Wire(new FixedPoint(upcasted_type))
        val rhs = Wire(new FixedPoint(upcasted_type))
        this.cast(lhs)
        op.cast(rhs)
        if (op.s | s) { lhs.number.asSInt >= rhs.number.asSInt } else {
          lhs.number >= rhs.number
        }
      case op: UInt =>
        val op_cast =
          Utils.FixedPoint(this.s, op.getWidth max this.d, this.f, op)
        this > op_cast
    }
  }

  def ===[T](r: T): Bool = { // TODO: Probably completely wrong for signed fixpts
    r match {
      case op: FixedPoint =>
        // Compute upcasted type and return type
        val upcasted_type =
          (op.s | s, scala.math.max(op.d, d), scala.math.max(op.f, f))
        // Get upcasted operators
        val lhs = Wire(new FixedPoint(upcasted_type))
        val rhs = Wire(new FixedPoint(upcasted_type))
        this.cast(lhs)
        op.cast(rhs)
        lhs.number === rhs.number
      case op: UInt =>
        // Compute upcasted type and return type
        val upcasted_type = (s, d, f)
        // Get upcasted operators
        val rhs = Utils.FixedPoint(s, d, f, op)
        number === rhs.number
    }
  }

  def isNeg(): Bool = {
    Mux(s.B && number(f + d - 1), true.B, false.B)
  }

  def unary_-(): FixedPoint = {
    val neg = Wire(new FixedPoint(s, d, f))
    neg.number := ~number + 1.U
    neg
  }

  // def * (op: FixedPoint): FixedPoint = {
  // 	// Compute upcasted type
  // 	val sign = op.s | s
  // 	val d_prec = op.d + d
  // 	val f_prec = op.f + f
  // 	// Do math on UInts
  // 	val r1 = Wire(new RawBits(d_prec + f_prec))
  // 	this.storeRaw(r1)
  // 	val r2 = Wire(new RawBits(d_prec + f_prec))
  // 	op.storeRaw(r2)
  // 	val rawResult = r1 * r2
  // 	// Store to FixedPoint result
  // 	val result = Wire(new FixedPoint(sign, scala.math.max(op.d, d), scala.math.max(op.f, f)))
  // 	rawResult.storeFix(result)
  // 	result.debug_overflow := Mux(rawResult.raw(0), true.B, false.B)
  // 	result
  // }

  override def cloneType =
    (new FixedPoint(s, d, f)).asInstanceOf[this.type] // See chisel3 bug 358

}

// Testing
class FixedPointTester(val s: Boolean, val d: Int, val f: Int) extends Module {
  def this(tuple: (Boolean, Int, Int)) = this(tuple._1, tuple._2, tuple._3)
  val io = IO(new Bundle {
    val num1 = new RawBits(d + f).asInput
    val num2 = new RawBits(d + f).asInput

    val add_result      = new RawBits(d + f).asOutput
    val prod_result     = new RawBits(d + f).asOutput
    val sub_result      = new RawBits(d + f).asOutput
    val quotient_result = new RawBits(d + f).asOutput
  })

  val fix1 = Wire(new FixedPoint(s, d, f))
  io.num1.storeFix(fix1)
  val fix2 = Wire(new FixedPoint(s, d, f))
  io.num2.storeFix(fix2)
  val sum = fix1 + fix2
  sum.storeRaw(io.add_result)
  val prod = fix1 * fix2
  prod.storeRaw(io.prod_result)
  val sub = fix1 - fix2
  sub.storeRaw(io.sub_result)
  val quotient = fix1 / fix2
  quotient.storeRaw(io.quotient_result)

}
// import Node._
// import ChiselError._

// /** Factory methods for [[Chisel.Fixed Fixed]] */
// object Fixed {
//     /** Convert a double to fixed point with a specified fractional width
//       * @param x Double to convert
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A BigInt representing the bits in the fixed point
//       */
//     def toFixed(x : Double, fracWidth : Int) : BigInt = BigInt(scala.math.round(x*scala.math.pow(2, fracWidth)))
//     /** Convert a Float to fixed point with a specified fractional width
//       * @param x Float to convert
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A BigInt representing the bits in the fixed point
//       */
//     def toFixed(x : Float, fracWidth : Int) : BigInt = BigInt(scala.math.round(x*scala.math.pow(2, fracWidth)))
//     /** Convert an Int to fixed point with a specified fractional width
//       * @param x Double to convert
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A BigInt representing the bits in the fixed point
//       */
//     def toFixed(x : Int, fracWidth : Int) : BigInt = BigInt(scala.math.round(x*scala.math.pow(2, fracWidth)))

//     /** Create a Fixed [[Chisel.Node]] with specified width and fracWidth
//       * @param x An Int to convert to fixed point
//       * @param width the total number of bits to use in the representation
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A fixed node with the specified parameters
//       */
//     def apply(x : Int, width : Int, fracWidth : Int) : Fixed = apply(toFixed(x, fracWidth), width, fracWidth)
//     /** Create a Fixed [[Chisel.Node]] with specified width and fracWidth
//       * @param x An Float to convert to fixed point
//       * @param width the total number of bits to use in the representation
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A fixed node with the specified parameters
//       */
//     def apply(x : Float, width : Int, fracWidth : Int) : Fixed = apply(toFixed(x, fracWidth), width, fracWidth)
//     /** Create a Fixed [[Chisel.Node]] with specified width and fracWidth
//       * @param x An Double to convert to fixed point
//       * @param width the total number of bits to use in the representation
//       * @param fracWidth the integer fractional width to use in the conversion
//       * @return A fixed node with the specified parameters
//       */
//     def apply(x : Double, width : Int, fracWidth : Int) : Fixed = apply(toFixed(x, fracWidth), width, fracWidth)
//     /** Create a Fixed [[Chisel.Node]] with specified width and fracWidth
//       * @param x An BigInt to use literally as the fixed point bits
//       * @param width the total number of bits to use in the representation
//       * @param fracWidth the integer fractional width to use
//       * @return A fixed node with the specified parameters
//       */
//     def apply(x : BigInt, width : Int, fracWidth : Int) : Fixed =  {
//       val res = Lit(x, width){Fixed()}
//       res.fractionalWidth = fracWidth
//       res
//     }

//     /** Create a Fixed I/O [[Chisel.Node]] with specified width and fracWidth
//       * @param dir Direction of I/O for the node, eg) INPUT or OUTPUT
//       * @param width the total number of bits to use in the representation
//       * @param fracWidth the integer fractional width to use
//       * @return A fixed node with the specified parameters
//       */
//     def apply(dir : IODirection = null, width : Int = -1, fracWidth : Int = -1) : Fixed = {
//         val res = new Fixed(fracWidth);
//         res.create(dir, width)
//         res
//     }
// }

// /** A Fixed point data type
//   * @constructor Use [[Chisel.Fixed$ Fixed]] object to create rather than this class directly */
// class Fixed(var fractionalWidth : Int = 0) extends Bits with Num[Fixed] {
//     type T = Fixed

//     /** Convert a Node to a Fixed data type with the same fractional width as this instantiation */
//     override def fromNode(n : Node): this.type = {
//         val res = Fixed(OUTPUT).asTypeFor(n).asInstanceOf[this.type]
//         res.fractionalWidth = this.getFractionalWidth()
//         res
//     }

//     /** Create a Fixed representation from an Int */
//     override def fromInt(x : Int) : this.type = Fixed(x, this.getWidth(), this.getFractionalWidth()).asInstanceOf[this.type]

//     /** clone this Fixed instantiation */
//     override def cloneType: this.type = Fixed(this.dir, this.getWidth(), this.getFractionalWidth()).asInstanceOf[this.type];

//     override protected def colonEquals(that : Bits): Unit = that match {
//       case f: Fixed => {
//         val res = if((f.getWidth() == this.getWidth()*2) && (f.getFractionalWidth() == this.getFractionalWidth()*2)) {
//           truncate(f, this.getFractionalWidth())
//         } else {
//           checkAligned(f)
//           f
//         }
//         super.colonEquals(res)
//       }
//       case _ => illegalAssignment(that)
//     }

//     def getFractionalWidth() : Int = this.fractionalWidth

//     private def truncate(f : Fixed, truncateAmount : Int) : Fixed = fromSInt(f.toSInt >> UInt(truncateAmount))
//     private def truncate(f : SInt, truncateAmount : Int) : SInt = f >> UInt(truncateAmount)

//     /** Ensure two Fixed point data types have the same fractional width, Error if not */
//     private def checkAligned(b : Fixed) {
//       if(this.getFractionalWidth() != b.getFractionalWidth()) ChiselError.error(this.getFractionalWidth() + " Fractional Bits does not match " + b.getFractionalWidth())
//       if(this.getWidth() != b.getWidth()) ChiselError.error(this.getWidth() + " Width does not match " + b.getWidth())
//     }

//     /** Convert a SInt to a Fixed by reinterpreting the Bits */
//     private def fromSInt(s : SInt, width : Int = this.getWidth(), fracWidth : Int = this.getFractionalWidth()) : Fixed = {
//         val res = chiselCast(s){Fixed()}
//         res.fractionalWidth = fracWidth
//         res.width = width
//         res
//     }

//     // Order Operators
//     def > (b : Fixed) : Bool = {
//         checkAligned(b)
//         this.toSInt > b.toSInt
//     }

//     def < (b : Fixed) : Bool = {
//         checkAligned(b)
//         this.toSInt < b.toSInt
//     }

//     def >= (b : Fixed) : Bool = {
//         checkAligned(b)
//         this.toSInt >= b.toSInt
//     }

//     def <= (b : Fixed) : Bool = {
//         checkAligned(b)
//         this.toSInt <= b.toSInt
//     }

//     def === (b : Fixed) : Bool = {
//         checkAligned(b)
//         this.toSInt === b.toSInt
//     }

//     def >> (b : UInt) : Fixed = {
//         fromSInt(this.toSInt >> b)
//     }

//     // Arithmetic Operators
//     def unary_-() : Fixed = Fixed(0, this.getWidth(), this.getFractionalWidth()) - this

//     def + (b : Fixed) : Fixed = {
//         checkAligned(b)
//         fromSInt(this.toSInt + b.toSInt)
//     }

//     def - (b : Fixed) : Fixed = {
//         checkAligned(b)
//         fromSInt(this.toSInt - b.toSInt)
//     }

//     /** Multiply increasing the Bit Width */
//     def * (b : Fixed) : Fixed = {
//         checkAligned(b)
//         val temp = this.toSInt * b.toSInt
//         fromSInt(temp, temp.getWidth(), this.getFractionalWidth()*2)
//     }

//     /** Multiply with one bit of rounding */
//     def *& (b : Fixed) : Fixed = {
//         checkAligned(b)
//         val temp = this.toSInt * b.toSInt
//         val res = temp + ((temp & UInt(1)<<UInt(this.getFractionalWidth()-1))<<UInt(1))
//         fromSInt(truncate(res, this.getFractionalWidth()))
//     }

//     /** Multiply truncating the result to the same Fixed format */
//     def *% (b : Fixed) : Fixed = {
//         checkAligned(b)
//         val temp = this.toSInt * b.toSInt
//         fromSInt(truncate(temp, this.getFractionalWidth()))
//     }

//     def / (b : Fixed) : Fixed = {
//         checkAligned(b)
//         fromSInt((this.toSInt << UInt(this.getFractionalWidth())) / b.toSInt)
//     }

//     /** This is just the modulo of the two fixed point bit representations changed into SInt and operated on */
//     def % (b : Fixed) : Fixed = {
//       checkAligned(b)
//       fromSInt(this.toSInt % b.toSInt)
//     }
// }
