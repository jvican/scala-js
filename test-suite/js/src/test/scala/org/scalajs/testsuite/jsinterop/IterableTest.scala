/*                     __                                               *\
**     ________ ___   / /  ___      __ ____  Scala.js Test Suite        **
**    / __/ __// _ | / /  / _ | __ / // __/  (c) 2013-2017, LAMP/EPFL   **
**  __\ \/ /__/ __ |/ /__/ __ |/_// /_\ \    http://scala-js.org/       **
** /____/\___/_/ |_/____/_/ | |__/ /____/                               **
**                          |/____/                                     **
\*                                                                      */
package org.scalajs.testsuite.jsinterop

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSName, ScalaJSDefined}

import org.junit.Assert._
import org.junit.Assume._
import org.junit.{BeforeClass, Test}

object IterableTest {
  @BeforeClass def assumeSymbolsAreSupported(): Unit = {
    assumeTrue("Assuming JavaScript symbols are supported",
        org.scalajs.testsuite.utils.Platform.areJSSymbolsSupported)
  }

  private class CounterIterable(val max: Int) extends js.Iterable[Int] {
    @JSName(js.Symbol.iterator)
    def jsIterator(): js.Iterator[Int] = new CounterIterator(max)
  }

  private class CounterIterator(val max: Int) extends js.Iterator[Int] {
    private[this] var nextNum = 0

    def next(): js.Iterator.Entry[Int] = {
      if (nextNum < max) {
        val res = new js.Iterator.Entry[Int] {
          val done: Boolean = false
          val value: Int = nextNum
        }

        nextNum +=1

        res
      } else {
        new js.Iterator.Entry[Int] {
          val done: Boolean = true
          val value: Int = 0
        }
      }
    }
  }
}

class IterableTest {
  import IterableTest._

  def assertValue[T](expected: T, entry: js.Iterator.Entry[T]): Unit = {
    assertFalse(entry.done)
    assertEquals(expected, entry.value)
  }

  @Test def jsArrayIsIterable(): Unit = {
    val iterable: js.Iterable[Int] = js.Array(1, 2, 3)
    val it = iterable.jsIterator()

    assertValue(1, it.next())
    assertValue(2, it.next())
    assertValue(3, it.next())
    assertTrue(it.next().done)
    assertTrue(it.next().done)
  }

  @Test def iterableToScala(): Unit = {
    val jsIterable: js.Iterable[Int] = new CounterIterable(5)
    val iterable: Iterable[Int] = jsIterable
    assertEquals(List(0, 1, 2, 3, 4), iterable.toList)
  }

  @Test def iteratorToScala(): Unit = {
    val jsIterator: js.Iterator[Int] = new CounterIterator(5)
    val iterator: Iterator[Int] = jsIterator.toIterator
    assertEquals(List(0, 1, 2, 3, 4), iterator.toList)
  }

  @Test def jsArrayToScalaViaIterable(): Unit = {
    val jsIterable: js.Iterable[Int] = js.Array(1, 2, 3)
    assertEquals(List(1, 2, 3), jsIterable.toList)
  }

  @Test def scalaToIterable(): Unit = {
    import js.JSConverters._

    val jsIterable: js.Iterable[String] = Iterable("a", "b", "c").toJSIterable
    val it = jsIterable.jsIterator()

    assertValue("a", it.next())
    assertValue("b", it.next())
    assertValue("c", it.next())
    assertTrue(it.next().done)
    assertTrue(it.next().done)
  }

  @Test def scalaToIterator(): Unit = {
    import js.JSConverters._

    val it = Iterator("a", "b", "c").toJSIterator

    assertValue("a", it.next())
    assertValue("b", it.next())
    assertValue("c", it.next())
    assertTrue(it.next().done)
    assertTrue(it.next().done)
  }

}
