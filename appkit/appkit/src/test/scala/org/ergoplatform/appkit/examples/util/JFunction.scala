package org.ergoplatform.example.util

import java.util.function.Function

object JFunction {
  def instance[A,B](f: A => B): Function[A,B] = new Function[A,B] {
    override def apply(t: A): B = f(t)
  }
}
