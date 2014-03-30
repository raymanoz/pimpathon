package stacycurl.scala.pimpathon


object list {
  implicit class ListOps[A](list: List[A]) {
    def uncons[B](empty: => B, nonEmpty: List[A] => B): B = if (list.isEmpty) empty else nonEmpty(list)
  }
}
