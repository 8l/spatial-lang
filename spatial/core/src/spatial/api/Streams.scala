package spatial.api

import argon.core.Staging
import spatial.{SpatialApi, SpatialExp}
import forge._

trait StreamApi extends StreamExp { this: SpatialApi =>

  /** Static methods **/
  @api def StreamIn[T: Meta: Bits](bus: Bus): StreamIn[T] = {
    bus_check[T](bus)
    StreamIn(stream_in[T](bus))
  }

  @api def StreamOut[T: Meta: Bits](bus: Bus): StreamOut[T] = {
    bus_check[T](bus)
    StreamOut(stream_out[T](bus))
  }

  @api implicit def readStream[T](stream: StreamIn[T]): T = stream.value
}

trait StreamExp { this: SpatialExp =>

  case class StreamIn[T: Meta: Bits](s: Exp[StreamIn[T]])
      extends Template[StreamIn[T]] {
    @api def value(): T         = this.value(true)
    @api def value(en: Bool): T = wrap(stream_read(s, en.s)) // Needed?
  }

  case class StreamOut[T: Meta: Bits](s: Exp[StreamOut[T]])
      extends Template[StreamOut[T]] {
    @api def :=(value: T): Void = this := (value, true)
    @api def :=(value: T, en: Bool): Void =
      Void(stream_write(s, value.s, en.s))
  }

  /** Type classes **/
  // --- Staged
  case class StreamInType[T: Bits](child: Meta[T]) extends Meta[StreamIn[T]] {
    override def wrapped(x: Exp[StreamIn[T]]) = StreamIn(x)(child, bits[T])
    override def typeArguments                = List(child)
    override def stagedClass                  = classOf[StreamIn[T]]
    override def isPrimitive                  = false
  }
  implicit def streamInType[T: Meta: Bits]: Meta[StreamIn[T]] =
    StreamInType(meta[T])

  case class StreamOutType[T: Bits](child: Meta[T])
      extends Meta[StreamOut[T]] {
    override def wrapped(x: Exp[StreamOut[T]]) = StreamOut(x)(child, bits[T])
    override def typeArguments                 = List(child)
    override def stagedClass                   = classOf[StreamOut[T]]
    override def isPrimitive                   = false
  }
  implicit def streamOutType[T: Meta: Bits]: Meta[StreamOut[T]] =
    StreamOutType(meta[T])

  /** IR Nodes **/
  case class StreamInNew[T: Type: Bits](bus: Bus) extends Op[StreamIn[T]] {
    def mirror(f: Tx) = stream_in[T](bus)
    val mT            = typ[T]
  }

  case class StreamOutNew[T: Type: Bits](bus: Bus) extends Op[StreamOut[T]] {
    def mirror(f: Tx) = stream_out[T](bus)
    val mT            = typ[T]
  }

  case class StreamRead[T: Type: Bits](stream: Exp[StreamIn[T]], en: Exp[Bool])
      extends EnabledOp[T](en) {
    def mirror(f: Tx) = stream_read(f(stream), f(en))
    val mT            = typ[T]
    val bT            = bits[T]
  }

  case class StreamWrite[T: Type: Bits](stream: Exp[StreamOut[T]],
                                        data: Exp[T],
                                        en: Exp[Bool])
      extends EnabledOp[Void](en) {
    def mirror(f: Tx) = stream_write(f(stream), f(data), f(en))
    val mT            = typ[T]
    val bT            = bits[T]
  }

  /** Constructors **/
  @internal def stream_in[T: Type: Bits](bus: Bus): Exp[StreamIn[T]] = {
    stageMutable(StreamInNew[T](bus))(ctx)
  }

  @internal def stream_out[T: Type: Bits](bus: Bus): Exp[StreamOut[T]] = {
    stageMutable(StreamOutNew[T](bus))(ctx)
  }

  @internal
  def stream_read[T: Type: Bits](stream: Exp[StreamIn[T]], en: Exp[Bool]) = {
    stageWrite(stream)(StreamRead(stream, en))(ctx)
  }

  @internal
  def stream_write[T: Type: Bits](stream: Exp[StreamOut[T]],
                                  data: Exp[T],
                                  en: Exp[Bool]) = {
    stageWrite(stream)(StreamWrite(stream, data, en))(ctx)
  }

  /** Internals **/
  @internal def bus_check[T: Type: Bits](bus: Bus): Unit = {
    if (bits[T].length < bus.length) {
      warn(
        ctx,
        s"Bus length is greater than size of StreamIn type - will use first ${bits[T].length} bits in the bus")
      warn(ctx)
    } else if (bits[T].length > bus.length) {
      error(ctx, s"Bus length is smaller than size of StreamIn type")
      error(ctx)
    }
  }

}
