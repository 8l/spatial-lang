package spatial.codegen.chiselgen

import argon.codegen.chiselgen.ChiselCodegen
import spatial.api.{ControllerExp, CounterExp, UnrolledExp}
import spatial.SpatialConfig
import spatial.analysis.SpatialMetadataExp 
import spatial.SpatialExp
import scala.collection.mutable.HashMap
import spatial.targets.DE1._

trait ChiselGenStream extends ChiselGenSRAM {
  val IR: SpatialExp
  import IR._

  var streamIns: List[Sym[Reg[_]]] = List()
  var streamOuts: List[Sym[Reg[_]]] = List()

  override def quote(s: Exp[_]): String = {
    s match {
      case b: Bound[_] => super.quote(s)
      case _ => super.quote(s)

    }
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case StreamInNew(bus) =>
      emitGlobalWire(src"val ${lhs}_ready_options = Wire(Vec(${readersOf(lhs).length}, Bool()))", forceful = true)
      emitGlobalWire(src"val ${lhs}_ready = ${lhs}_ready_options.reduce{_|_}", forceful = true)
      emitGlobalWire(src"""val ${lhs}_now_valid = Wire(Bool())""", forceful = true)
      emitGlobalWire(src"val ${lhs}_valid = Wire(Bool())", forceful = true)
      emitGlobalWire(src"val ${lhs} = Wire(${newWire(readersOf(lhs).head.node.tp)})", forceful = true)
      bus match {
        case BurstDataBus() =>
        case BurstAckBus =>
        case ScatterAckBus =>
        case GatherDataBus() =>
        case VideoCamera => 
          // emit(src"// video in camera, node = $lhs", forceful=true)
          // emit(src"// reset and output logic for video camera", forceful=true)
          // emit(src"when (reset) {", forceful=true)
          // emit(src"  io.stream_out_data           := 0.U ", forceful=true)
          // emit(src"  io.stream_out_startofpacket  := 0.U ", forceful=true)
          // emit(src"  io.stream_out_endofpacket    := 0.U ", forceful=true)
          // emit(src"  io.stream_out_empty          := 0.U ", forceful=true)
          // emit(src"} .otherwise /*(io.stream_out_ready | ~io.stream_out_valid)*/ { ", forceful=true)
          // emit(src"  io.stream_out_data           := converted_data ", forceful=true)
          // emit(src"  io.stream_out_startofpacket  := stream_out_startofpacket ", forceful=true)
          // emit(src"  io.stream_out_endofpacket    := stream_out_endofpacket ", forceful=true)
          // emit(src"  io.stream_out_empty          := io.stream_in_empty  ", forceful=true)
          // emit(src"} ", forceful=true) 

          // emit(src"io.led_stream_out_data := io.stream_in_ready", forceful=true)
          emit(src"io.stream_in_ready := ${lhs}_ready", forceful=true)
          emit(src"""${lhs}_valid := io.stream_in_valid // .. Or is io.stream_in_valid not connected?!""", forceful=true)
          // emit(src"io.led_stream_out_data := true.B | (${lhs}_valid << 1) | (${lhs}_ready << 2)", forceful=true)


          // emit(src"${lhs}_valid := io.stream_in_valid", forceful=true)

        case SliderSwitch =>
          // emit(src"// switch, node = $lhs", forceful=true)
          // emit(src"// reset and output logic for switch", forceful=true)
          // emit(src"when (reset) {", forceful=true)
          // emit(src"  io.stream_out_data           := 0.U ", forceful=true)
          // emit(src"  io.stream_out_startofpacket  := 0.U ", forceful=true)
          // emit(src"  io.stream_out_endofpacket    := 0.U ", forceful=true)
          // emit(src"  io.stream_out_empty          := 0.U ", forceful=true)
          // emit(src"} .elsewhen (io.stream_out_ready | ~io.stream_out_valid) { ", forceful=true)
          // emit(src"  io.stream_out_data           := converted_data ", forceful=true)
          // emit(src"  io.stream_out_startofpacket  := io.stream_in_startofpacket ", forceful=true)
          // emit(src"  io.stream_out_endofpacket    := io.stream_in_endofpacket ", forceful=true)
          // emit(src"  io.stream_out_empty          := io.stream_in_empty  ", forceful=true)
          // emit(src"} ", forceful=true) 
          // emit(src"io.stream_in_ready := ${lhs}_ready", forceful=true)
          // emit(src"${lhs}_valid := io.stream_in_valid", forceful=true)

        case GPInput1 =>
          emit(src"// switch, node = $lhs", forceful=true)
          emit(src"${lhs}_valid := 1.U", forceful=true)

        case GPInput2 =>
          emit(src"// switch, node = $lhs", forceful=true)
          emit(src"${lhs}_valid := 1.U", forceful=true)

        case _ =>
          streamIns = streamIns :+ lhs.asInstanceOf[Sym[Reg[_]]]
      }

    case StreamOutNew(bus) =>
      emitGlobalWire(src"val ${lhs}_valid_options = Wire(Vec(${writersOf(lhs).length}, Bool()))", forceful = true)
      emitGlobalWire(src"val ${lhs}_valid = Wire(Bool())", forceful = true)
      emitGlobalWire(src"${lhs}_valid := ${lhs}_valid_options.reduce{_|_}", forceful = true)
      writersOf(lhs).head.node match {
        case Def(e@ParStreamWrite(_, data, ens)) => 
          emitGlobalWire(src"val ${lhs}_data_options = Wire(Vec(${ens.length}*${writersOf(lhs).length}, ${newWire(data.head.tp)}))")
          emitGlobalWire(src"""val ${lhs} = Vec((0 until ${ens.length}).map{i => 
            val ${lhs}_slice_options = (0 until ${writersOf(lhs).length}).map{j => ${lhs}_data_options(i*${writersOf(lhs).length}+j)}
            Mux1H(${lhs}_valid_options, ${lhs}_slice_options)
          }.toList)""")
        case Def(e@StreamWrite(_, data, _)) => 
          emitGlobalWire(src"val ${lhs}_data_options = Wire(Vec(${writersOf(lhs).length}, ${newWire(data.tp)}))", forceful = true)
          emitGlobalWire(src"val ${lhs} = Mux1H(${lhs}_valid_options, ${lhs}_data_options)", forceful = true)
      }

      emitGlobalWire(src"val ${lhs}_ready = Wire(Bool())", forceful = true)
      // emitGlobalWire(src"val ${lhs} = Wire(${wireType})")
      bus match {
        case BurstFullDataBus() =>
        case BurstCmdBus =>
        case GatherAddrBus =>
        case ScatterCmdBus() => 
        case VGA =>
          emitGlobalWire(src"""val stream_out_startofpacket = Wire(Bool())""")
          emitGlobalWire(src"""val stream_out_endofpacket = Wire(Bool())""")
          throw new Exception("Please use BufferedOut instead of StreamOut for a VGA!")
          emit(src"// video in camera, node = $lhs", forceful=true)
          emit(src"// reset and output logic for video camera", forceful=true)
          emit(src"when (reset) {", forceful=true)
          emit(src"  io.stream_out_data           := 0.U ", forceful=true)
          emit(src"  io.stream_out_startofpacket  := 0.U ", forceful=true)
          emit(src"  io.stream_out_endofpacket    := 0.U ", forceful=true)
          emit(src"  io.stream_out_empty          := 0.U ", forceful=true)
          emit(src"} .otherwise /*(io.stream_out_ready | ~io.stream_out_valid)*/ { ", forceful=true)
          emit(src"  io.stream_out_data           := ${lhs} ", forceful=true)
          emit(src"  io.stream_out_startofpacket  := stream_out_startofpacket ", forceful=true)
          emit(src"  io.stream_out_endofpacket    := stream_out_endofpacket ", forceful=true)
          emit(src"  io.stream_out_empty          := io.stream_in_empty  ", forceful=true)
          emit(src"} ", forceful=true) 

          emit(src"// EMITTING FOR VGA; in OUTPUT REGISTERS, Output Register section $lhs", forceful=true)
          emit(src"io.stream_out_valid := ${lhs}_valid", forceful=true)
          emit(src"${lhs}_ready := io.stream_out_ready", forceful=true)
        case LEDR =>
          emit(src"// LEDR, node = $lhs", forceful=true)
          emit(src"${lhs}_ready := 1.U", forceful=true)
          emit(src"${lhs}_valid := 1.U", forceful=true)
          emit(src"""io.led_stream_out_data := ${lhs}""")

        case GPOutput1 => 
          emit(src"// GPOutput1, node = $lhs", forceful=true)
          emit(src"${lhs}_ready := 1.U", forceful=true)
          emit(src"${lhs}_valid := 1.U", forceful=true)
        case GPOutput2 => 
          emit(src"// GPOutput2, node = $lhs", forceful=true)
          emit(src"${lhs}_ready := 1.U", forceful=true)
          emit(src"${lhs}_valid := 1.U", forceful=true)
        case _ =>
          streamOuts = streamOuts :+ lhs.asInstanceOf[Sym[Reg[_]]]
      }
    
    case BufferedOutNew(_, bus) => 
      bus match {
        case VGA => 
          emit (src"// EMITTING FOR BUFFEREDOUT ON VGA $lhs", forceful=true)
          emit(src"io.buffout_address := ${lhs}_address", forceful=true)
          emit(src"io.buffout_write := ${lhs}_write", forceful=true)
          emit(src"io.buffout_writedata := ${lhs}_writedata", forceful=true)
          emit(src"${lhs}_waitrequest := io.buffout_waitrequest", forceful=true)
          emitGlobalWire(src"""// Emit to global at BUFFEROUT node""", forceful=true)
          emitGlobalWire(src"""val ${lhs}_address = Wire(UInt(32.W))""", forceful=true)
          emitGlobalWire(src"""val ${lhs}_write = Wire(UInt(1.W))""", forceful=true)
          emitGlobalWire(src"""val ${lhs}_writedata = Wire(UInt(16.W))""", forceful=true)
          emitGlobalWire(src"""val ${lhs}_waitrequest = Wire(Bool())""", forceful=true)
          emitGlobalWire(src"""val ${lhs}_hAddr = Wire(UInt(8.W))""", forceful=true)
          emitGlobalWire(src"""val ${lhs}_wAddr = Wire(UInt(9.W))""", forceful=true)
      }

    case BufferedOutWrite(buffer, data, is, en) => 
//      case Def(BufferedOutNew(Seq(lift(320), lift(240)), bus)) => bus match {
      buffer match {
        case Def(BufferedOutNew(_, bus)) => bus match {
          case VGA =>
            emit (s"// EMITTING FOR BUFFEREDOUT WRITE ON VGA $buffer, $data, $is, $en")
              is.zipWithIndex.foreach{ case(ind, j) =>
                emit (src"""// EMITTING FOR BUFFEREDOUT WRITE ON VGA ${lhs}_$j = ${ind}""")
              }
            emit(s"// default buffer address: 134217728")
            emit(s"")
            
            emit(src"when(true.B /*~${buffer}_waitrequest*/) {")
            emit(src"  ${buffer}_write := 1.U")
            emit(src"  ${buffer}_writedata := ${data}.r")
            emit(src"  ${buffer}_hAddr := ${is(0)}.raw")
            emit(src"  ${buffer}_wAddr := ${is(1)}.raw")
            emit(src"  ${buffer}_address := 134217728.U + chisel3.util.Cat(${buffer}_hAddr, ${buffer}_wAddr, false.B)")
            emit(src"} .otherwise {")
            emit(src"  ${buffer}_write := 0.U")
            emit(src"  ${buffer}_writedata := 0.U")
            emit(src"  ${buffer}_hAddr := 0.U")
            emit(src"  ${buffer}_wAddr := 0.U")
            emit(src"  ${buffer}_address := 134217728.U")
            emit(src"}")
            // emit(src"io.led_stream_out_data := true.B | (~${buffer}_waitrequest << 1) | (${buffer}_waitrequest << 2)")
        }
      }

    case StreamRead(stream, en) =>
      val isAck = stream match {
        case Def(StreamInNew(bus)) => bus match {
          case BurstAckBus => true
          case ScatterAckBus => true
          case _ => false
        }
        case _ => false
      }
      val parent = parentOf(lhs).get
      emit(src"""val ${lhs}_rId = getStreamInLane("$stream")""")
      emit(src"""${stream}_ready_options(${lhs}_rId) := ${en} & (${parent}_datapath_en & ~${parent}_inhibitor).D(0 /*${symDelay(lhs)}*/ ) // Do not delay ready because datapath includes a delayed _valid already """)
      if (!isAck) {
        stream match {
          case Def(StreamInNew(bus)) => bus match {
            case VideoCamera => 
              emit(src"""val $lhs = io.stream_in_data""")  // Ignores enable for now
            case SliderSwitch => 
              emit(src"""val $lhs = io.switch_stream_in_data""")
            case GPInput1 => 
              emit(src"""val $lhs = io.gpi1_streamin_readdata""")
            case GPInput2 => 
              emit(src"""val $lhs = io.gpi2_streamin_readdata""")
            case BurstDataBus() => 
              emit(src"""val $lhs = (0 until 1).map{ i => ${stream}(i) }""")

            case _ =>
              val id = argMapping(stream)._1
              Predef.assert(id != -1, s"Stream ${quote(stream)} not present in streamIns")
              emit(src"""val ${quote(lhs)} = io.genericStreams.ins($id).bits.data """)  // Ignores enable for now
          }
        }
      } else {
        emit(src"""// read is of burstAck on $stream""")
      }

    case StreamWrite(stream, data, en) =>
      val parent = parentOf(lhs).get
      emit(src"""val ${lhs}_wId = getStreamOutLane("$stream")""")
      emit(src"""${stream}_valid_options(${lhs}_wId) := (${parent}_datapath_en & ~${parent}_inhibitor).D(${symDelay(lhs)}, rr) & $en""")
      emit(src"""${stream}_data_options(${lhs}_wId) := $data""")
      stream match {
        case Def(StreamOutNew(bus)) => bus match {
            case VGA => 
              emitGlobalWire(src"""// EMITTING VGA GLOBAL""")
              // emitGlobalWire(src"""val ${stream} = Wire(UInt(16.W))""")
              // emitGlobalWire(src"""val converted_data = Wire(UInt(16.W))""")
              emit(src"""// emiiting data for stream ${stream}""")
              // emit(src"""${stream} := $data""")
              // emit(src"""converted_data := ${stream}""")
              val sources = lhs.collectDeps{case Def(StreamRead(strm,_)) => strm}
              sources.find{ _ match {
                case Def(StreamInNew(strm)) => 
                  strm == VideoCamera
              }}
              if (sources.length > 0) {
                emit(src"""stream_out_startofpacket := io.stream_in_startofpacket""")
                emit(src"""stream_out_endofpacket := io.stream_in_endofpacket""")                
              } else {
                emit(src"""stream_out_startofpacket := Utils.risingEdge(${parent}_datapath_en)""")
                emit(src"""stream_out_endofpacket := ${parent}_done""")
              }
              // emit(src"""${stream}_valid := ${en} & ShiftRegister(${parent}_datapath_en & ~${parent}_inhibitor,${symDelay(lhs)})""")
            case LEDR =>
              // emitGlobalWire(src"""val ${stream} = Wire(UInt(32.W))""")
        //      emitGlobalWire(src"""val converted_data = Wire(UInt(32.W))""")
              // emit(src"""${stream} := $data""")
              // emit(src"""io.led_stream_out_data := ${stream}""")
            case GPOutput1 =>
              // emitGlobalWire(src"""val ${stream} = Wire(UInt(32.W))""")
              // emit(src"""${stream} := $data""")
              emit(src"""io.gpo1_streamout_writedata := ${stream}""")
            case GPOutput2 =>
              // emitGlobalWire(src"""val ${stream} = Wire(UInt(32.W))""")
              // emit(src"""${stream} := $data""")
              emit(src"""io.gpo2_streamout_writedata := ${stream}""")
            case BurstFullDataBus() =>
              emit(src"""${stream}_valid := $en & (${parent}_datapath_en & ~${parent}_inhibitor).D(${symDelay(lhs)}, rr) // Do not delay ready because datapath includes a delayed _valid already """)
              // emit(src"""${stream} := $data""")

            case BurstCmdBus =>  
              emit(src"""${stream}_valid := $en & (${parent}_datapath_en & ~${parent}_inhibitor).D(${symDelay(lhs)}, rr) // Do not delay ready because datapath includes a delayed _valid already """)
              // emit(src"""${stream} := $data""")

            case _ => 
              // emit(src"""${stream}_valid := ShiftRegister(${parent}_datapath_en & ~${parent}_inhibitor,${symDelay(lhs)}) & $en""")
              val id = argMapping(stream)._1
              Predef.assert(id != -1, s"Stream ${quote(stream)} not present in streamOuts")
              emit(src"""io.genericStreams.outs($id).bits.data := ${quote(data)}.number """)  // Ignores enable for now
              emit(src"""io.genericStreams.outs($id).valid := ${stream}_valid""")
        }
      }
    case _ => super.emitNode(lhs, rhs)
  }

  override protected def emitFileFooter() {

    val insList = (0 until streamIns.length).map{ i => s"StreamParInfo(32, 1)" }.mkString(",")
    val outsList = (0 until streamOuts.length).map{ i => s"StreamParInfo(32, 1)" }.mkString(",")

    withStream(getStream("IOModule")) {
      emit(src"// Non-memory Streams")
      emit(s"""val io_streamInsInfo = List(${insList})""")
      emit(s"""val io_streamOutsInfo = List(${outsList})""")
    }

    withStream(getStream("Instantiator")) {
      emit(src"// Non-memory Streams")
      emit(s"""val streamInsInfo = List(${insList})""")
      emit(s"""val streamOutsInfo = List(${outsList})""")
    }

    super.emitFileFooter()
  }
}
