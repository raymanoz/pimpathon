package pimpathon

import scala.language.{higherKinds, implicitConversions, reflectiveCalls}

import scala.{PartialFunction ⇒ ~>}
import scala.annotation.tailrec
import scala.collection.{breakOut, mutable ⇒ M, GenTraversable, GenTraversableLike}
import scala.collection.generic.CanBuildFrom

import pimpathon.any._
import pimpathon.boolean._
import pimpathon.either._
import pimpathon.function._
import pimpathon.map._
import pimpathon.multiMap._
import pimpathon.tuple._


object genTraversableLike extends genTraversableLike[({ type CC[A] = GenTraversableLike[A, GenTraversable[A]] })#CC] {
  protected def toGTL[A](gtl: GenTraversableLike[A, GenTraversable[A]]): GenTraversableLike[A, GenTraversable[A]] = gtl
}

abstract class genTraversableLike[CC[A]] {
  implicit def genTraversableLikePimps[A](cc: CC[A]): GenTraversableLikePimps[A] =
    new GenTraversableLikePimps[A](toGTL(cc))

  implicit def genTraversableLikeOfEitherPimps[L, R, Repr](gtl: GenTraversableLike[Either[L, R], Repr])
    : GenTraversableLikeOfEitherPimps[L, R, Repr] = new GenTraversableLikeOfEitherPimps[L, R, Repr](gtl)

  implicit def genTraversableLikeOfTuple2[K, V, Repr](gtl: GenTraversableLike[(K, V), Repr])
    : GenTraversableLikeOfTuple2[K, V, Repr] = new GenTraversableLikeOfTuple2[K, V, Repr](gtl)

  class GenTraversableLikePimps[A](gtl: GenTraversableLike[A, GenTraversable[A]]) {
    def asMap: GenTraversableLikeCapturer[A, Map, Map] = as[Map]

    def attributeCounts[B](f: A ⇒ B): Map[B, Int] =
      asMultiMap.withKeys(f).mapValues(_.size)

    def collectAttributeCounts[B](pf: A ~> B): Map[B, Int] =
      optAttributeCounts(pf.lift)

    def optAttributeCounts[B](f: A ⇒ Option[B]): Map[B, Int] =
      asMultiMap.withSomeKeys(f).mapValues(_.size)

    def asMultiMap[F[_]]: GenTraversableLikeCapturer[A, ({ type MM[K, V] = MultiMap[F, K, V] })#MM, Map] =
      GenTraversableLikeCapturer[A, ({ type MM[K, V] = MultiMap[F, K, V] })#MM, Map](gtl)

    def as[F[_, _]]: GenTraversableLikeCapturer[A, F, F] = GenTraversableLikeCapturer[A, F, F](gtl)

    def ungroupBy[B](f: A ⇒ B)(implicit inner: CCBF[A, CC], outer: CCBF[CC[A], CC]): CC[CC[A]] =
      gtl.foldLeft(UngroupBy[A, B, CC](Map(), Map())) { case (ungroupBy, item) ⇒ ungroupBy.add(item, f(item)) }.values

    def partitionByPF[B](pf: A ~> B)
      (implicit eab: CCBF[Either[A, B], CC], a: CCBF[A, CC], b: CCBF[B, CC]): (CC[A], CC[B]) = pf.partition[CC](gtl)

    def none(a: A): Boolean = gtl.forall(_ != a)
    def all(a: A): Boolean  = gtl.forall(_ == a)

    def seqMap[B, To](f: A ⇒ Option[B])(implicit cbf: CanBuildFrom[Nothing, B, To]): Option[To] =
      seqFold[M.Builder[B, To]](cbf())((builder, a) ⇒ f(a).map(builder += _)).map(_.result())

    def seqFold[B](z: B)(op: (B, A) ⇒ Option[B]): Option[B] = // similar to scalaz' GTL.foldLeftM[Option, B, A]
      apoFold[B, B](z)((b, a) ⇒ op(b, a).toRight(b)).right.toOption

    def apoFold[B, C](z: B)(op: (B, A) ⇒ Either[C, B]): Either[C, B] = {
      @tailrec def recurse(cur: GenTraversableLike[A, GenTraversable[A]], acc: B): Either[C, B] = cur.headOption match {
        case None ⇒ Right(acc)
        case Some(a) ⇒ op(acc, a) match {
          case Right(b) ⇒ recurse(cur.tail, b)
          case done     ⇒ done
        }
      }

      recurse(gtl, z)
    }
  }

  class GenTraversableLikeOfEitherPimps[L, R, Repr](gtl: GenTraversableLike[Either[L, R], Repr]) {
    def partitionEithers[That[_]](implicit lcbf: CCBF[L, That], rcbf: CCBF[R, That]): (That[L], That[R]) =
      (lcbf.apply(), rcbf.apply()).tap(l ⇒ r ⇒ gtl.foreach(_.addTo(l, r))).tmap(_.result(), _.result())
  }

  class GenTraversableLikeOfTuple2[K, V, Repr](gtl: GenTraversableLike[(K, V), Repr]) {
    def toMultiMap[F[_]](implicit fcbf: CCBF[V, F]): MultiMap[F, K, V] = gtl.map(kv ⇒ kv)(breakOut)
  }

  protected def toGTL[A](cc: CC[A]): GenTraversableLike[A, GenTraversable[A]]
}

case class GenTraversableLikeCapturer[A, F[_, _], G[_, _]](private val gtl: GenTraversableLike[A, GenTraversable[A]]) {
  import pimpathon.genTraversableLike._
  type CBF[K, V] = CanBuildFrom[Nothing, (K, V), F[K, V]]
  type GBF[K, V] = CanBuildFrom[Nothing, (K, V), G[K, V]]

  def withKeys[K](f: A ⇒ K)(implicit cbf: CBF[K, A]): F[K, A]    = withEntries(a ⇒ (f(a), a))
  def withValues[V](f: A ⇒ V)(implicit cbf: CBF[A, V]): F[A, V]  = withEntries(a ⇒ (a, f(a)))
  def withConstValue[V](v: V)(implicit  cbf: CBF[A, V]): F[A, V] = withEntries(a ⇒ (a, v))


  def withEntries[K1, K2, K3, K4, V](fk1: A ⇒ K1, fk2: A ⇒ K2, fk3: A ⇒ K3, fk4: A ⇒ K4, fv: A ⇒ V)(implicit
    k4: CBF[K4, V], k3: GBF[K3, F[K4, V]], k2: GBF[K2, G[K3, F[K4, V]]], k1: GBF[K1, G[K2, G[K3, F[K4, V]]]]
  ): G[K1, G[K2, G[K3, F[K4, V]]]] = groupBy(fk1, _.withEntries(fk2, fk3, fk4, fv))

  def withEntries[K1, K2, K3, V](fk1: A ⇒ K1, fk2: A ⇒ K2, fk3: A ⇒ K3, fv: A ⇒ V)(implicit
    k3: CBF[K3, V], k2: GBF[K2, F[K3, V]], k1: GBF[K1, G[K2, F[K3, V]]]
  ): G[K1, G[K2, F[K3, V]]] = groupBy(fk1, _.withEntries(fk2, fk3, fv))

  def withEntries[K1, K2, V](fk1: A ⇒ K1, fk2: A ⇒ K2, fv: A ⇒ V)(implicit
    k2: CBF[K2, V], k1: GBF[K1, F[K2, V]]
  ): G[K1, F[K2, V]] = groupBy(fk1, _.withEntries(fk2, fv))

  def withEntries[K, V](k: A ⇒ K, v: A ⇒ V)(implicit cbf: CBF[K, V]): F[K, V] = gtl.map(a ⇒ (k(a), v(a)))(breakOut)
  def withEntries[K, V](f: A ⇒ ((K, V)))(implicit cbf: CBF[K, V]): F[K, V] = gtl.map(f)(breakOut)


  def withSomeKeys[K](f: A ⇒ Option[K])(implicit cbf: CBF[K, A]): F[K, A]   = withSomeEntries(a ⇒ f(a).map(_ → a))
  def withSomeValues[V](f: A ⇒ Option[V])(implicit cbf: CBF[A, V]): F[A, V] = withSomeEntries(a ⇒ f(a).map(a → _))
  def withSomeEntries[K, V](fk: A ⇒ Option[K], fv: A ⇒ Option[V])(implicit cbf: CBF[K, V]): F[K, V] = withSomeEntries(zip(fk, fv))
  def withSomeEntries[K, V](f: A ⇒ Option[(K, V)])(implicit cbf: CBF[K, V]): F[K, V] = gtl.flatMap(a ⇒ f(a))(breakOut)

  def withPFKeys[K](pf: A ~> K)(implicit cbf: CBF[K, A]): F[K, A]   = withPFEntries(pf &&& identityPF[A])
  def withPFValues[V](pf: A ~> V)(implicit cbf: CBF[A, V]): F[A, V] = withPFEntries(identityPF[A] &&& pf)
  def withPFEntries[K, V](k: A ~> K, v: A ~> V)(implicit cbf: CBF[K, V]): F[K, V] = withPFEntries(k &&& v)
  def withPFEntries[K, V](pf: A ~> (K, V))(implicit cbf: CBF[K, V]): F[K, V] = gtl.collect(pf)(breakOut)

  def withManyKeys[K](f: A ⇒ List[K])(implicit cbf: CBF[K, A]): F[K, A] =
    gtl.flatMap(a ⇒ f(a).map(_ → a))(breakOut)

  def withUniqueKeys[K](f: A ⇒ K)(implicit cbf: CBF[K, A]): Option[F[K, A]] = {
    gtl.seqFold[(Set[K], M.Builder[(K, A), F[K, A]])](Set.empty[K], cbf()) {
      case ((ks, builder), a) ⇒ f(a).calc(k ⇒ (!ks.contains(k)).option(ks + k, builder += ((k, a))))
    }.map { case (_, builder) ⇒ builder.result() }
  }

  private def groupBy[K, V](fk: A ⇒ K, f: GenTraversableLikeCapturer[A, F, G] ⇒ V)(
    implicit cbf: CanBuildFrom[Nothing, (K, V), G[K, V]]
  ): G[K, V] = gtl.asMultiMap[List].withKeys(fk).map { case (k,as) ⇒ (k, f(copy(as))) }(breakOut)

  private def zip[K, V](fk: A ⇒ Option[K], fv: A ⇒ Option[V])(a: A): Option[(K, V)] = for { k ← fk(a); v ← fv(a) } yield (k, v)
}

case class UngroupBy[A, B, CC[_]](ungrouped: Map[Int, M.Builder[A, CC[A]]], counts: Map[B, Int])(
  implicit inner: CCBF[A, CC], outer: CCBF[CC[A], CC]) {

  def add(a: A, b: B): UngroupBy[A, B, CC] = copy(ungrouped + entry(count(b), a), counts + ((b, count(b))))
  def values: CC[CC[A]] = ungrouped.sorted.values.map(_.result())(breakOut(outer))

  private def entry(count: Int, a: A) = (count, ungrouped.getOrElse(count, inner.apply()) += a)
  private def count(b: B) = counts.getOrElse(b, 0) + 1
}