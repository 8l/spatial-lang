package spatial.codegen.scalagen

import argon.core.Staging
import argon.ops.FixPtExp
import org.virtualized.SourceContext
import spatial.SpatialExp

trait ScalaGenMemories extends ScalaGenBits {
  val IR: SpatialExp
  import IR._

  var enableMemGen: Boolean = false

  def flattenAddress(dims: Seq[Exp[Index]],
                     indices: Seq[Exp[Index]],
                     ofs: Option[Exp[Index]]): String = {
    val strides = List.tabulate(dims.length) { i =>
      (dims.drop(i + 1).map(quote) :+ "1").mkString("*")
    }
    indices
      .zip(strides)
      .map { case (i, s) => src"$i*$s" }
      .mkString(" + ") + ofs
      .map { o =>
        src" + $o"
      }
      .getOrElse("")
  }

  private def oob(tp: Type[_],
                  mem: Exp[_],
                  lhs: Exp[_],
                  inds: Seq[Exp[_]],
                  pre: String,
                  post: String,
                  isRead: Boolean)(lines: => Unit) = {
    val name = u"$mem"
    val addr =
      if (inds.isEmpty && pre == "" && post == "") "err.getMessage"
      else
        "\"" + pre + "\" + " + "s\"\"\"${" + inds
          .map(quote)
          .map(_ + ".toString")
          .mkString(" + \", \" + ") + "}\"\"\" + \"" + post + "\""

    val op = if (isRead) "read" else "write"

    open(src"try {")
    lines
    close("}")
    open(src"catch {case err: java.lang.ArrayIndexOutOfBoundsException => ")
    emit(
      s"""System.out.println("[warn] ${lhs.ctx} Memory $name: Out of bounds $op at address " + $addr)""")
    if (isRead) emit(src"${invalid(tp)}")
    close("}")
  }

  def oobApply(tp: Type[_],
               mem: Exp[_],
               lhs: Exp[_],
               inds: Seq[Exp[_]],
               pre: String = "",
               post: String = "")(lines: => Unit) = {
    oob(tp, mem, lhs, inds, pre, post, isRead = true)(lines)
  }

  def oobUpdate(tp: Type[_],
                mem: Exp[_],
                lhs: Exp[_],
                inds: Seq[Exp[_]],
                pre: String = "",
                post: String = "")(lines: => Unit) = {
    oob(tp, mem, lhs, inds, pre, post, isRead = false)(lines)
  }

}
