package pl.ostrzyciel.superfast_jellyfish.benchmark

import org.apache.commons.io.output.{CountingOutputStream, NullOutputStream}

import java.io.ByteArrayOutputStream

class DebugOutputStream(storeBuffer: Boolean = false)
  extends CountingOutputStream(NullOutputStream.NULL_OUTPUT_STREAM):

  if storeBuffer then
    out = new ByteArrayOutputStream()

  def print(): Unit = out match
    case s: ByteArrayOutputStream =>
      println(s.toString)
    case _ =>
