package pimpathon

import org.junit.Test
import scala.collection.{mutable => M}
import scala.collection.generic.CanBuildFrom

import org.junit.Assert._
import pimpathon.builder._
import pimpathon.multiMap._


class MultiMapTest {
  @Test def multiMapCBF(): Unit = {
    val cbf = MultiMap.build[List, Int, String]
    val builder = cbf.apply()

    builder += (1 -> "foo") += (1 -> "bar")
    assertEquals(Map(1 -> List("foo", "bar")), builder.reset())
    assertEquals(Map(), builder.reset())
  }

  @Test def ignoreFromCBF(): Unit = {
    val ucbf = new UnitCanBuildFrom[List[Int], Int]

    assertEquals(UnitBuilder[Int]("apply()"), ucbf.apply())
    assertEquals(UnitBuilder[Int]("apply(List(1, 2, 3))"), ucbf.apply(List(1, 2, 3)))

    val ucbfi = new UnitCanBuildFrom[List[Int], Int] with IgnoreFromCBF[List[Int], Int, Unit]

    assertEquals(UnitBuilder[Int]("apply()"), ucbfi.apply())
    assertEquals(UnitBuilder[Int]("apply()"), ucbfi.apply(List(1, 2, 3)))
  }

  @Test def merge(): Unit = {
    assertEquals(Map(1 -> List(1, 2)), Map(1 -> List(1, 2)).merge(MultiMap.empty[List, Int, Int]))
    assertEquals(Map(1 -> List(1, 2)), MultiMap.empty[List, Int, Int].merge(Map(1 -> List(1, 2))))
    assertEquals(Map(1 -> List(1, 2)), Map(1 -> List(1)).merge(Map(1 -> List(2))))
    assertEquals(Map(1 -> List(1), 2 -> List(2)), Map(1 -> List(1)).merge(Map(2 -> List(2))))
    assertEquals(Map(1 -> Set(1, 2)),  Map(1 -> Set(1)).merge(Map(1 -> Set(2))))
  }

  @Test def select(): Unit = {
    assertEquals(Map(1 -> 2, 2 -> 3), Map(1 -> List(2), 2 -> List(3, 4)).select(_.head))
  }

  @Test def append(): Unit = {
    assertEquals(Map(1 -> List(2, 3)), MultiMap.empty[List, Int, Int].append(1, List(2, 3)))
    assertEquals(Map(1 -> List(2, 3)), Map(1 -> List(2)).append(1, List(3)))
    assertEquals(Map(1 -> List(2, 3)), Map(1 -> List(2, 3)).append(1, Nil))
  }

  @Test def multiMap_head(): Unit = {
    assertEquals(Map(1 -> 10, 2 -> 20), Map(1 -> List(10, 11), 2 -> List(20)).multiMap.head)
    assertEquals(Map(2 -> 20), Map(1 -> Nil, 2 -> List(20)).multiMap.head)
    assertEquals(Map(), Map.empty[Int, List[Int]].multiMap.head)
  }

  @Test def multiMap_tail(): Unit = {
    assertEquals(Map(1 -> List(11)), Map(1 -> List(10, 11), 2 -> List(20)).multiMap.tail)
    assertEquals(Map(), Map(1 -> Nil, 2 -> List(20)).multiMap.tail)
    assertEquals(Map(), Map(1 -> (Nil: List[Int])).multiMap.tail)
    assertEquals(Map(), Map.empty[Int, List[Int]].multiMap.tail)
  }

  @Test def headTailOption(): Unit = {
    assertEquals(Some(Map(1 -> 10, 2 -> 20), Map(1 -> List(11))), Map(1 -> List(10, 11), 2 -> List(20)).headTailOption)
    assertEquals(Some(Map(2 -> 20), Map()), Map(1 -> Nil, 2 -> List(20)).headTailOption)
    assertEquals(None, Map(1 -> (Nil: List[Int])).headTailOption)
    assertEquals(None, Map.empty[Int, List[Int]].headTailOption)
  }

  @Test def multiMap_values(): Unit = {
    assertEquals(List(1, 2, 3), Map(1 -> List(1), 2 -> List(2, 3)).multiMap.values)
    assertEquals( Set(1, 2, 3), Map(1 ->  Set(1), 2 ->  Set(2, 3)).multiMap.values)
  }

  @Test def multiMap_reverse(): Unit = assertEquals(
    Map(2 -> List(1), 3 -> List(1, 2), 4 -> List(2)),
    Map(1 -> List(2, 3), 2 -> List(3, 4)).multiMap.reverse
  )

  @Test def multiMap_mapEntries(): Unit = assertEquals(
    Map(0 -> List(20, 21), 1 -> List(10, 11, 30, 31)),
    Map(1 -> List(10, 11), 2 -> List(20, 21), 3 -> List(30, 31)).multiMap.mapEntries(k => vs => (k % 2, vs))
  )

  @Test def flatMapValues(): Unit = assertEquals(
    Map(0 -> List(1, -1, 2, -2), 1 -> List(2, -2, 3, -3)),
    Map(0 -> List(1, 2), 1 -> List(2, 3)).flatMapValues(v => List(v, -v))
  )

  @Test def pop(): Unit = {
    assertEquals(Map(1 -> List(3), 2 -> List(3)),    Map(1 -> List(2, 3), 2 -> List(3)).pop(1))
    assertEquals(Map(1 -> List(2, 3)),               Map(1 -> List(2, 3), 2 -> List(3)).pop(2))
    assertEquals(Map(1 -> List(2, 3), 2 -> List(3)), Map(1 -> List(2, 3), 2 -> List(3)).pop(3))
  }

  @Test def sequence(): Unit = assertEquals(
    List(Map(1 -> 10, 2 -> 20), Map(1 -> 11, 2 -> 21)),
    Map(1 -> List(10, 11), 2 -> List(20, 21)).sequence
  )

  @Test def sliding(): Unit = assertEquals(
    List(Map(1 -> List(11, 12), 2 -> List(21, 22)), Map(1 -> List(12, 13), 2 -> List(22, 23))),
    Map(1 -> List(11, 12, 13), 2 -> List(21, 22, 23)).multiMap.sliding(2)
  )

  @Test def getOrEmpty(): Unit = {
    assertEquals(List(2), Map(1 → List(2)).getOrEmpty(1))
    assertEquals(Nil,     Map(1 → List(2)).getOrEmpty(2))

    assertEquals(Set(2), Map(1 → Set(2)).getOrEmpty(1))
    assertEquals(Set(), Map(1 → Set(2)).getOrEmpty(2))
  }

  class UnitCanBuildFrom[From, Elem] extends CanBuildFrom[From, Elem, Unit] {
    def apply(): M.Builder[Elem, Unit]           = UnitBuilder[Elem]("apply()")
    def apply(from: From): M.Builder[Elem, Unit] = UnitBuilder[Elem]("apply(%s)".format(from))
  }

  case class UnitBuilder[E](from: String) extends M.Builder[E, Unit] {
    def +=(elem: E): this.type = this
    def clear(): Unit = {}
    def result(): Unit = ()
    override def toString = "UnitBuilder(%s)".format(from)
  }
}
