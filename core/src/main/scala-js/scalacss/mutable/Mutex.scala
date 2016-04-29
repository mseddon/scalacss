package scalacss.mutable

// ================
// ====        ====
// ====   JS   ====
// ====        ====
// ================


final class Mutex {
   def apply[A](f: => A): A = f
}

object Mutex {
  implicit val mutex = new Mutex
}
