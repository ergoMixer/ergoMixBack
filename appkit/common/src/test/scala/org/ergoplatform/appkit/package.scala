package org.ergoplatform

import scala.language.implicitConversions

package object appkit {

  // for scala 2.11
  implicit def toJavaFunction[U, V](f: U => V): java.util.function.Function[U, V] =
    new java.util.function.Function[U, V] {
      override def apply(t: U): V = f(t)
    }

  // for scala 2.11
  implicit def toJavaConsumer[T](f: T => Unit): java.util.function.Consumer[T] =
    new java.util.function.Consumer[T] {
      override def accept(t: T): Unit = f(t)
    }
}
