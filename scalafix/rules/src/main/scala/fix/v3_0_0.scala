package fix

import scalafix.v1._

import scala.meta._

class v3_0_0 extends SemanticRule("v3_0_0") {
  /*
  TODO:
   - not found: type ContextShift
   - not found: type Timer
   - not found: type ConcurrentEffect
   - not found: value Blocker
   */

  override def fix(implicit doc: SemanticDocument): Patch = {
    val Bracket_guarantee_M = SymbolMatcher.exact("cats/effect/Bracket#guarantee().")
    val Bracket_uncancelable_M = SymbolMatcher.exact("cats/effect/Bracket#uncancelable().")

    Patch.replaceSymbols(
      "cats/effect/Async#async()." -> "async_",
      "cats/effect/package.BracketThrow." -> "cats/effect/MonadCancelThrow.",
      "cats/effect/Bracket." -> "cats/effect/MonadCancel.",
      "cats/effect/IO.async()." -> "async_",
      "cats/effect/IO.suspend()." -> "defer",
      "cats/effect/Resource.liftF()." -> "eval",
      "cats/effect/concurrent/Deferred." -> "cats/effect/Deferred.",
      "cats/effect/concurrent/Ref." -> "cats/effect/Ref.",
      "cats/effect/concurrent/Semaphore." -> "cats/effect/std/Semaphore."
    ) +
      doc.tree.collect {
        // Bracket#guarantee(a)(b) -> MonadCancel#guarantee(a, b)
        case t @ q"${Bracket_guarantee_M(_)}($a)($b)" =>
          fuseParameterLists(t, a, b)

        // Bracket#uncancelable(a) -> MonadCancel#uncancelable(_ => a)
        case q"${Bracket_uncancelable_M(_)}($a)" =>
          Patch.addLeft(a, "_ => ")
      }.asPatch
  }

  // tree @ f(param1)(param2) -> f(param1, param2)
  private def fuseParameterLists(tree: Tree, param1: Tree, param2: Tree): Patch =
    (param1.tokens.lastOption, param2.tokens.headOption) match {
      case (Some(lastA), Some(firstB)) =>
        val between =
          tree.tokens.dropWhile(_ != lastA).drop(1).dropRightWhile(_ != firstB).dropRight(1)
        val maybeParen1 = between.find(_.is[Token.RightParen])
        val maybeParen2 = between.reverseIterator.find(_.is[Token.LeftParen])
        (maybeParen1, maybeParen2) match {
          case (Some(p1), Some(p2)) =>
            val toAdd = if (lastA.end == p1.start && p1.end == p2.start) ", " else ","
            Patch.replaceToken(p1, toAdd) + Patch.removeToken(p2)
          case _ => Patch.empty
        }
      case _ => Patch.empty
    }
}
