package spatial.codegen.scalagen

import spatial.SpatialExp

trait ScalaGenStream extends ScalaGenMemories {
  val IR: SpatialExp
  import IR._

  var streamIns: List[Exp[_]]  = Nil
  var streamOuts: List[Exp[_]] = Nil

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: StreamInType[_] =>
      src"scala.collection.mutable.Queue[${tp.child}]"
    case tp: StreamOutType[_] =>
      src"scala.collection.mutable.Queue[${tp.child}]"
    case _ => super.remap(tp)
  }

  // HACK
  def bitsFromString(lhs: String, line: String, tp: Type[_]): Unit = tp match {
    case FixPtType(s, i, f) =>
      emit(s"val $lhs = Number($line, FixedPoint($s,$i,$f))")
    case FltPtType(g, e) =>
      emit(s"val $lhs = Number($line, FloatPoint($g,$e))")
    case BoolType() => emit(s"val $lhs = Bit($line.toBoolean, true)")
    case tp: VectorType[_] =>
      open(s"""val $lhs = $line.split(",").map(_.trim).map{elem => """)
      bitsFromString("out", "elem", tp.child)
      emit("out")
      close("}.toArray")
    case tp: StructType[_] =>
      emit(s"""val tokens = $line.split(";")""")
      tp.fields.zipWithIndex.foreach {
        case (field, i) =>
          bitsFromString(s"field$i", s"tokens($i)", field._2)
      }
      emit(
        src"val $lhs = $tp(" + List
          .tabulate(tp.fields.length) { i =>
            s"field$i"
          }
          .mkString(", ") + ")")

    case _ => throw new Exception(c"Cannot create Stream with type $tp")
  }

  def bitsToString(lhs: String, elem: String, tp: Type[_]): Unit = tp match {
    case FixPtType(s, i, f) => emit(s"val $lhs = $elem.toString")
    case FltPtType(g, e)    => emit(s"val $lhs = $elem.toString")
    case BoolType()         => emit(s"val $lhs = $elem.toString")
    case tp: VectorType[_] =>
      open(s"""val $lhs = $elem.map{elem => """)
      bitsToString("out", "elem", tp.child)
      emit("out")
      close("""}.mkString(", ")""")
    case tp: StructType[_] =>
      tp.fields.zipWithIndex.foreach {
        case (field, i) =>
          emit(s"val field$i = $elem.${field._1}")
          bitsToString(s"fieldStr$i", s"field$i", field._2)
      }
      emit(
        s"val $lhs = List(" + List
          .tabulate(tp.fields.length) { i =>
            s"fieldStr$i"
          }
          .mkString(", ") + s""").mkString("; ")""")
  }

  override protected def emitNode(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op @ StreamInNew(bus) =>
      streamIns :+= lhs

      emit(src"val $lhs = new scala.collection.mutable.Queue[${op.mT}]")
      if (!bus.isInstanceOf[DRAMBus[_]]) {
        val name = nameOf(lhs)
          .map(_ + " (" + lhs.ctx + ")")
          .getOrElse("defined at " + lhs.ctx)
        open(src"def populate_$lhs() = {")
        emit(src"""print("Enter name of file to use for StreamIn $name: ")""")
        emit(src"val filename = Console.readLine()")
        open(src"try {")
        emit(src"val source = scala.io.Source.fromFile(filename)")
        open(src"source.getLines.foreach{line => ")
        bitsFromString("elem", "line", op.mT)
        emit(src"$lhs.enqueue(elem)")
        close("}")
        close("}")
        open(src"catch {case e: Throwable => ")
        emit(
          src"""println("There was a problem while opening the specified file for reading.")""")
        emit(src"""println(e.getMessage)""")
        emit(src"sys.exit(1)")
        close("}")
        close("}")
        emit(src"populate_$lhs()")
      }

    case op @ StreamOutNew(bus) =>
      streamOuts :+= lhs

      emit(src"val $lhs = new scala.collection.mutable.Queue[${op.mT}]")

      if (!bus.isInstanceOf[DRAMBus[_]]) {
        val name = nameOf(lhs)
          .map(_ + " (" + lhs.ctx + ")")
          .getOrElse("defined at " + lhs.ctx)

        emit(src"""print("Enter name of file to use for StreamOut $name: ")""")
        emit(src"var ${lhs}_writer: java.io.PrintWriter = null")
        open(src"try {")
        emit(src"val filename = Console.readLine()")
        emit(
          src"${lhs}_writer = new java.io.PrintWriter(new java.io.File(filename))")
        close("}")
        open("catch{ case e: Throwable => ")
        emit(
          src"""println("There was a problem while opening the specified file for writing.")""")
        emit(src"""println(e.getMessage)""")
        emit(src"sys.exit(1)")
        close("}")

        open(src"def print_$lhs(): Unit = {")
        open(src"$lhs.foreach{elem => ")
        bitsToString("line", "elem", op.mT)
        emit(src"${lhs}_writer.println(line)")
        close("}")
        emit(src"${lhs}_writer.close()")
        close("}")
      }

    case op @ StreamWrite(strm, data, en) =>
      emit(src"val $lhs = if ($en) $strm.enqueue($data)")
    case op @ StreamRead(strm, en) =>
      emit(
        src"val $lhs = if ($en && $strm.nonEmpty) $strm.dequeue() else ${invalid(op.mT)}")
    case _ => super.emitNode(lhs, rhs)
  }

}
