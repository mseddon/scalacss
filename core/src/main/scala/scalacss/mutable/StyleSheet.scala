package scalacss.mutable

import shapeless.HList
import shapeless.ops.hlist.Mapper
import scalacss._
import DslBase.{DslCond, ToStyle}
import StyleC.MkUsage

/**
 * Mutable StyleSheets provide a context in which many styles can be created using a DSL.
 *
 * They are mutable because they maintain a list of registered styles, meaning you can declare each style one at a time
 * instead of having to create a list of styles in a single expression.
 *
 * Each style itself is immutable.
 */
object StyleSheet {

  /**
   * Classes defined in the REPL appear like this:
   *   $line8.$read.$iw.$iw.$iw.$iw.$iw.$iw.$iw.$iw.$iw.$iw.$iw.$iw.MyStyles
   */
  private def fixRepl(s: String): String =
    s.replaceFirst("""^\$line.+[.$]\$iw[.$]""", "")

  abstract class Base {
    protected def register: Register

    protected implicit val classNameHint: ClassNameHint =
      ClassNameHint(
        fixRepl(getClass.getName)
          .replaceFirst("\\$+$", "")
          .replaceFirst("""^(?:.+\.)(.+?)$""", "$1")
          .replaceAll("\\$+", "_"))

    protected object dsl extends DslBase {
      override def styleS(t: ToStyle*)(implicit c: Compose) = Dsl.style(t: _*)

       final def ^ = Literal

       final def Color(literal: String) = scalacss.Color(literal)

       implicit final def toCondOps[C <% Cond](x: C) = new CondOps(x)
      final class CondOps(val cond: Cond) {
         def - = new DslCond(cond, dsl)
      }
    }

    final def css(implicit env: Env): Css =
      register.css

    final def styles: Vector[StyleA] =
      register.styles

    /**
     * Render registered styles into some format, usually a String of plain CSS.
     *
     * @param env The target environment in which the styles are to be used.
     *            Allows customisation of required CSS.
     */
    final def render[Out](implicit r: Renderer[Out], env: Env): Out =
      register.render

    /**
     * Render registered styles into some format, usually a String of plain CSS.
     *
     * The `A` suffix stands for ''absolute'', in that it doesn't perform any environment customisation, and as such
     * an [[Env]] isn't required.
     */
    final def renderA[Out](implicit r: Renderer[Out]): Out =
      render(r, Env.empty)
  }


  /**
   * A standalone stylesheet has the following properties:
   *
   *   - Intent is to generate static CSS for external consumption.
   *   - It is comparable to SCSS/LESS.
   *   - Styles go into a pool of registered styles when they are declared and return `Unit`.
   *   - Style class names / CSS selectors must be provided.
   *   - Only static styles ([[StyleS]]) are usable.
   */
  abstract class Standalone(protected implicit val register: Register) extends Base {
    import dsl._

     protected final implicit class RootStringOps(val sel: CssSelector) extends Pseudo.ChainOps[RootStringOps] {
      override protected def addPseudo(p: Pseudo): RootStringOps =
        new RootStringOps(p modSelector sel)

      /**
       * Create a root style.
       *
       * {{{
       *   "div.stuff" - (
       *     ...
       *   )
       * }}}
       */
      def -(t: ToStyle*)(implicit c: Compose): Unit =
        register registerS styleS(unsafeRoot(sel)(t: _*))
    }

    protected final class NestedStringOps(val sel: CssSelector) extends Pseudo.ChainOps[NestedStringOps] {
      override protected def addPseudo(p: Pseudo): NestedStringOps =
        new NestedStringOps(p modSelector sel)

      /** Create a nested style. */
      def -(t: ToStyle*)(implicit c: Compose): StyleS =
        styleS(unsafeChild(sel)(t: _*))
    }

     final protected def & : Cond = Cond.empty

    /** Create a child style. */
     final protected def &(sel: CssSelector): NestedStringOps = new NestedStringOps(sel)
  }


  /**
   * An inline stylesheet has the following properties:
   *
   *   - Intent is to create styles that can be applied directly to HTML in Scala/Scala.JS.
   *   - Each style is stored in a `val` of type `StyleA`.
   *   - Styles are applied to HTML by setting the `class` attribute of the HTML to the class(es) in a `StyleA`.
   *   - Style class names / CSS selectors are automatically generated.
   *   - All style types ([[StyleS]], [[StyleF]], [[StyleC]]) are usable.
   */
  abstract class Inline(protected implicit val register: Register) extends Base with Macros.DslMixin {
    import dsl._

            final protected type Domain[A] = scalacss.Domain[A]
     final protected def  Domain    = scalacss.Domain

    override protected def __macroStyle (name: String) = new MStyle (name)
    override protected def __macroStyleF(name: String) = new MStyleF(name)

    protected class MStyle(name: String) extends DslMacros.MStyle {
      override def apply(t: ToStyle*)(implicit c: Compose): StyleA = {
        val s1 = Dsl.style(t: _*)
        val s2 = register.applyMacroName(name, s1)
        register registerS s2
      }

      override def apply(className: String)(t: ToStyle*)(implicit c: Compose): StyleA =
        register registerS Dsl.style(className)(t: _*)
    }

    protected class MStyleF(name: String) extends DslMacros.MStyleF {
      override protected def create[I](manualName: Option[String], d: Domain[I], f: I => StyleS, classNameSuffix: (I, Int) => String) =
        manualName match {
          case None    => register.registerFM(StyleF(f)(d), name)(classNameSuffix)
          case Some(n) => register.registerF2(StyleF(f)(d), n)(classNameSuffix)
        }
    }

    protected def styleC[M <: HList](s: StyleC)(implicit m: Mapper.Aux[register._registerC.type, s.S, M], u: MkUsage[M]): u.Out =
      register.registerC(s)(implicitly, m, u)

     final protected def & : Cond = Cond.empty

    /**
     * Objects in Scala are lazy. If you put styles in inner objects you need to make sure they're initialised before
     * your styles are rendered.
     * To do so, call this at the end of your stylesheet with one style from each inner object.
     */
    protected def initInnerObjects(a: StyleA*) = ()
  }
}
