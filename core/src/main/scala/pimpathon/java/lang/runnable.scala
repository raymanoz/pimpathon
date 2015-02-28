package pimpathon.java.lang

import scala.language.implicitConversions


object runnable {
  implicit def runnableFromThunk[Discarded](thunk: () ⇒ Discarded): Runnable = create(thunk())

  def create(action: ⇒ Unit): Runnable = new Runnable {
    override def run(): Unit = action
  }
}