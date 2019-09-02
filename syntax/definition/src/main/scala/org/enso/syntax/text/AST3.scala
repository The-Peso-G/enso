package org.enso.syntax.text

import java.util.UUID

import cats.Functor
import cats.derived._
import cats.implicits._
import org.enso.data.List1._
import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.data.Tree
import org.enso.syntax.text.ast.Repr.R
import org.enso.syntax.text.ast.Repr
import org.enso.syntax.text.ast.opr

import scala.annotation.tailrec
import scala.reflect.ClassTag

object AST {

  //////////////////////////////////////////////////////////////////////////////
  //// Reexports ///////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Assoc = opr.Assoc

  val Assoc = opr.Assoc
  val Prec  = opr.Prec

  //////////////////////////////////////////////////////////////////////////////
  //// Definition //////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Structure ////

  type AST_Type    = ASTOf[ShapeOf] // FIXME: Compatibility mode
  type ASTOf[T[_]] = Node[T, ShapeOf]
//  type AST         = ASTOf[ShapeOf]
  type Shape = ShapeOf[AST]

  //// Aliases ////

  type SAST        = Shifted[AST]
  type StreamOf[T] = List[Shifted[T]]
  type _Stream1[T] = List1[Shifted[T]]
  type Stream      = StreamOf[AST]
  type Stream1     = _Stream1[AST]
  type ID          = UUID

  //// API ////

  object implicits extends implicits
  trait implicits
      extends Conversions.implicits
      with TopLevel.implicits
      with Ident.implicits
      with Invalid.implicits
      with Literal.implicits
      with App.implicits
      with Block.implicits
      with Node.implicits
      with Comment.implicits
      with Import.implicits
      with Mixfix.implicits
      with Group.implicits
      with Def.implicits
      with Foreign.implicits
      with Module.implicits
  import implicits._

  object TopLevel {
    object implicits extends implicits
    trait implicits {

      implicit def offZipStream[T: Repr]: OffsetZip[StreamOf, T] = { stream =>
        var off = 0
        stream.map { t =>
          off += t.off
          val out = t.map((off, _))
          off += Repr(t.el).span
          out
        }
      }
//
//      implicit class ASTOps(ast: AST) {
//        def shape: ShapeOf[AST] =
//          ast.unFix.shape
//
////        def map(f: AST => AST): AST = {
////          val tshape  = ast.unFix
////          val tshape2 = tshape.copy(shape = ftorShape.map(tshape.shape)(f))
////          fix(tshape2)
////        }
//      }

//      implicit class ShapeOps[T[_]](ast: ASTOf[T]) {
////        def repr:     Repr.Builder = ast.repr
////        def span:     Int          = repr.span
////        def byteSpan: Int          = repr.byteSpan
//
//        def repr(t: T)(implicit ev: Repr[T]): Repr.Builder = Repr(t)
//
//        def map(f: AST => AST)(
//          implicit
//          ftorT: Functor[T],
//          reprT: Repr[T[AST]]
//        ): ASTOf[T] = Functor[T].map(ast)(f)
//
////        def mapWithOff(f: (Int, AST) => AST)(
////          implicit
////          ftorT: Functor[T],
////          offZipT: OffsetZip[T, AST],
////          reprT: Repr[T[AST]]
////        ): T[AST] = Functor[T].map(OffsetZip(ast))(f.tupled)
////
////        def traverseWithOff(f: (Int, AST) => AST)(
////          implicit
////          ftorT: Functor[T],
////          offZipT: OffsetZip[T, AST],
////          reprT: Repr[T[AST]]
////        ): T[AST] = ??? //{
////        //      def go(i: Int, t: AST): AST = {
////        //        val newStruct = mapWithOff { (j, ast) =>
////        //          val off = i + j
////        //          go(off, f(off, ast))
////        //        }
////        //        t.copy(struct = newStruct)
////        //      }
////        //      mapWithOff { (off, ast) =>
////        //        go(off, f(off, ast))
////        //      }
////        //    }
//      }
    }

  }

  object Conversions {
    object implicits extends implicits
    trait implicits extends Ident.IndirectConversions {
      implicit def stringToAST(str: String): AST = {
        if (str == "") throw new Error("Empty literal")
        if (str == "_") Blank()
        else if (str.head.isLower) Var(str)
        else if (str.head.isUpper) Cons(str)
        else Opr(str)
      }
    }
  }

  def tokenize(ast: AST): Shifted.List1[AST] = {
    @tailrec
    def go(ast: AST, out: AST.Stream): Shifted.List1[AST] = ast match {
      case App.Prefix.any(t) => go(t.fn, Shifted(t.off, t.arg) :: out)
      case _                 => Shifted.List1(ast, out)
    }
    go(ast, List())
  }

  ////////////////////////////////////
  //// Apply / Unapply Generators ////
  ////////////////////////////////////

  sealed trait Unapply[T] {
    type In
    def run[Out](f: In => Out)(t: AST): Option[Out]
  }
  object Unapply {
    def apply[T](implicit t: Unapply[T]): Unapply[T] { type In = t.In } = t
    implicit def inst[T[_]](
      implicit ev: ClassTag[T[AST]]
    ): Unapply[Node[T, ShapeOf]] { type In = T[AST] } =
      new Unapply[Node[T, ShapeOf]] {
        type In = T[AST]
        val ct                              = implicitly[ClassTag[T[AST]]]
        def run[Out](fn: In => Out)(t: AST) = ct.unapply(t.unFix).map(fn)
      }
  }

  sealed trait UnapplyByType[T] {
    def unapply(t: AST): Option[T]
  }
  object UnapplyByType {
    def apply[T](implicit ev: UnapplyByType[T]) = ev
    implicit def instance[T[_]](
      implicit ct: ClassTag[T[_]]
    ): UnapplyByType[Node[T, ShapeOf]] =
      new UnapplyByType[Node[T, ShapeOf]] {
        def unapply(t: AST) =
          ct.unapply(t.unFix).map(_ => t.asInstanceOf[Node[T, ShapeOf]])
      }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// OffsetZip ///////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  trait OffsetZip[F[A], A] {
    def zipWithOffset(t: F[A]): F[(Int, A)]
  }
  object OffsetZip {
    def apply[F[A], A](implicit ev: OffsetZip[F, A]): OffsetZip[F, A] = ev
    def apply[F[A], A](t: F[A])(implicit ev: OffsetZip[F, A]): F[(Int, A)] =
      OffsetZip[F, A].zipWithOffset(t)
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Catamorphism ////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Wrapper ////

  case class Node[+H[_], F[_]](unFix: H[Node[F, F]], id: Option[ID] = None)(
    implicit ops: NodeOps[H[Node[F, F]]]
  ) {
    val repr: Repr.Builder = ops.repr(unFix)
    def show():           String     = repr.build()
    def setID(newID: ID): Node[H, F] = copy(id = Some(newID))
    def withNewID() = copy(id = Some(UUID.randomUUID()))
    def map(f: AST => AST): Node[H, F] = copy(unFix = ops.map(unFix)(f))
    def mapWithOff(f: (Int, AST) => AST): Node[H, F] =
      copy(unFix = ops.mapWithOff(unFix)(f))
    def traverseWithOff(f: (Int, AST) => AST): Node[H, F] =
      copy(unFix = ops.traverseWithOff(unFix)(f))
  }
  object Node {
    object implicits extends implicits
    trait implicits {
      implicit def toNode[T[_]](t: T[AST])(
        implicit ev: NodeOps[T[AST]]
      ):                                        ASTOf[T]         = Node(t)
      implicit def fromNode[T[_]](t: ASTOf[T]): T[AST]           = t.unFix
      implicit def reprNode[H[_], T[_]]:        Repr[Node[H, T]] = _.repr
    }
  }

  //// Ops ////

  trait NodeOps[T] {
    def repr(t: T): Repr.Builder
    def map(t: T)(f: AST => AST): T
    def mapWithOff(t: T)(f: (Int, AST) => AST): T
    def traverseWithOff(t: T)(f: (Int, AST) => AST): T
  }
  object NodeOps {
    def apply[T: NodeOps]: NodeOps[T] = implicitly[NodeOps[T]]
    implicit def instance[T[_]](
      implicit
      evRepr: Repr[T[AST]],
      evFtor: Functor[T],
      evOZip: OffsetZip[T, AST]
    ): NodeOps[T[AST]] =
      new NodeOps[T[AST]] {
        def repr(t: T[AST]):               Repr.Builder = evRepr.repr(t)
        def map(t: T[AST])(f: AST => AST): T[AST]       = Functor[T].map(t)(f)
        def mapWithOff(t: T[AST])(f: (Int, AST) => AST): T[AST] =
          Functor[T].map(OffsetZip(t))(f.tupled)

        def traverseWithOff(t: T[AST])(f: (Int, AST) => AST): T[AST] = {
          def go(i: Int, x: AST): AST = {
            x.mapWithOff { (j, ast) =>
              val off = i + j
              go(off, f(off, ast))
            }
          }
          mapWithOff(t) { (off, ast) =>
            go(off, f(off, ast))
          }
        }
      }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Shape ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** [[Shape]] defines the shape of an AST node. It contains information about
    * the layout of elements and spacing between them.
    *
    * @tparam T The type of elements.
    */
  sealed trait ShapeOf[T]

  //////////////////////////////////////////////////////////////////////////////
  //// Phantom /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** Phantom type. Use with care, as Scala cannot prove its proper usage. When
    * a type is phantom, then its last type argument is not used and we can
    * safely coerce it to something else.
    */
  trait Phantom
  implicit class PhantomOps[T[_] <: Phantom](ident: T[_]) {
    def coerce[S]: T[S] = ident.asInstanceOf[T[S]]
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Invalid /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Invalid = ASTOf[InvalidOf]
  sealed trait InvalidOf[T] extends ShapeOf[T]
  object Invalid {

    //// Types ////
    type Unrecognized = ASTOf[UnrecognizedOf]
    type Unexpected   = ASTOf[UnexpectedOf]

    case class UnrecognizedOf[T](str: String) extends InvalidOf[T] with Phantom
    case class UnexpectedOf[T](msg: String, stream: StreamOf[T])
        extends InvalidOf[T]

    //// Implicits ////

    object implicits extends implicits
    trait implicits {
      implicit def ftorInvalid:         Functor[InvalidOf]      = semi.functor
      implicit def ftorUnexpected:      Functor[UnexpectedOf]   = semi.functor
      implicit def ftorUnrecognized:    Functor[UnrecognizedOf] = semi.functor
      implicit def reprUnrecognized[T]: Repr[UnrecognizedOf[T]] = _.str
      implicit def reprUnexpected[T: Repr]: Repr[UnexpectedOf[T]] =
        t => Repr(t.stream)
      implicit def offZipUnrecognized[T]: OffsetZip[UnrecognizedOf, T] =
        t => t.coerce
      implicit def offZipUnexpected[T: Repr]: OffsetZip[UnexpectedOf, T] =
        t => t.copy(stream = OffsetZip(t.stream))
    }
    import implicits._

    //// Smart Constructors ////

    object Unrecognized {
      def apply(str: String): Unrecognized = UnrecognizedOf[AST](str)
    }

    object Unexpected {
      def apply(msg: String, stream: Stream): Unexpected =
        UnexpectedOf(msg, stream)
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Ident ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Ident = ASTOf[IdentOf]
  sealed trait IdentOf[T] extends ShapeOf[T] with Phantom { val name: String }

  // FIXME: Compatibility mode
  type Blank = Ident.Blank
  type Var   = Ident.Var
  type Cons  = Ident.Cons
  type Opr   = Ident.Opr
  type Mod   = Ident.Mod

  // FIXME: Compatibility mode
  val Blank = Ident.Blank
  val Var   = Ident.Var
  val Cons  = Ident.Cons
  val Opr   = Ident.Opr
  val Mod   = Ident.Mod

  object Ident {

    //// Definition ////

    type Blank = ASTOf[BlankOf]
    type Var   = ASTOf[VarOf]
    type Cons  = ASTOf[ConsOf]
    type Opr   = ASTOf[OprOf]
    type Mod   = ASTOf[ModOf]

    case class BlankOf[T]()            extends IdentOf[T] { val name = "_" }
    case class VarOf[T](name: String)  extends IdentOf[T]
    case class ConsOf[T](name: String) extends IdentOf[T]
    case class ModOf[T](name: String)  extends IdentOf[T]
    case class OprOf[T](name: String) extends IdentOf[T] {
      val (prec, assoc) = opr.Info.of(name)
    }

    //// Instances ////

    trait DirectConversions {
      implicit def strToVar(str: String):  Var  = Var(str)
      implicit def strToCons(str: String): Cons = Cons(str)
      implicit def strToOpr(str: String):  Opr  = Opr(str)
      implicit def strToMod(str: String):  Mod  = Mod(str)
    }

    trait IndirectConversions extends DirectConversions {
      implicit def stringToIdent(str: String): Ident = {
        if (str == "") throw new Error("Empty literal")
        if (str == "_") Blank()
        else if (str.head.isLower) Var(str)
        else if (str.head.isUpper) Cons(str)
        else Opr(str)
      }
    }

    object implicits extends implicits
    trait implicits {
      implicit def reprBlank[T]:   Repr[BlankOf[T]]      = _.name
      implicit def reprVar[T]:     Repr[VarOf[T]]        = _.name
      implicit def reprCons[T]:    Repr[ConsOf[T]]       = _.name
      implicit def reprOpr[T]:     Repr[OprOf[T]]        = _.name
      implicit def reprMod[T]:     Repr[ModOf[T]]        = R + _.name + "="
      implicit def ftorIdent:      Functor[IdentOf]      = semi.functor
      implicit def ftorBlank:      Functor[BlankOf]      = semi.functor
      implicit def ftorVar:        Functor[VarOf]        = semi.functor
      implicit def ftorCons:       Functor[ConsOf]       = semi.functor
      implicit def ftorOpr:        Functor[OprOf]        = semi.functor
      implicit def ftorMod:        Functor[ModOf]        = semi.functor
      implicit def offZipBlank[T]: OffsetZip[BlankOf, T] = t => t.coerce
      implicit def offZipVar[T]:   OffsetZip[VarOf, T]   = t => t.coerce
      implicit def offZipCons[T]:  OffsetZip[ConsOf, T]  = t => t.coerce
      implicit def offZipOpr[T]:   OffsetZip[OprOf, T]   = t => t.coerce
      implicit def offZipMod[T]:   OffsetZip[ModOf, T]   = t => t.coerce
    }
    import implicits._

    //// Smart Constructors ////

    val any = UnapplyByType[Ident]

    object Blank {
      val any             = UnapplyByType[Blank]
      def unapply(t: AST) = Unapply[Blank].run(_ => true)(t)
      def apply(): Blank = BlankOf[AST]()
    }

    object Var {
      val any             = UnapplyByType[Var]
      def unapply(t: AST) = Unapply[Var].run(_.name)(t)
      def apply(name: String): Var = VarOf[AST](name)
    }

    object Cons {
      val any             = UnapplyByType[Cons]
      def unapply(t: AST) = Unapply[Cons].run(_.name)(t)
      def apply(name: String): Cons = ConsOf[AST](name)
    }

    object Mod {
      val any             = UnapplyByType[Mod]
      def unapply(t: AST) = Unapply[Mod].run(_.name)(t)
      def apply(name: String): Mod = ModOf[AST](name)
    }

    object Opr {
      val app             = Opr(" ")
      val any             = UnapplyByType[Opr]
      def unapply(t: AST) = Unapply[Opr].run(_.name)(t)
      def apply(name: String): Opr = OprOf[AST](name)

      type Mod = Ident.Mod // FIXME: Compatibility mode
      val Mod = Ident.Mod // FIXME: Compatibility mode
    }

    ///////////////////////
    //// InvalidSuffix ////
    ///////////////////////

    type InvalidSuffix = ASTOf[InvalidSuffixOf]
    case class InvalidSuffixOf[T](elem: Ident, suffix: String)
        extends InvalidOf[T]
        with Phantom
    object InvalidSuffixOf {
      implicit def ftor:      Functor[InvalidSuffixOf]      = semi.functor
      implicit def offZip[T]: OffsetZip[InvalidSuffixOf, T] = t => t.coerce
      implicit def repr[T]: Repr[InvalidSuffixOf[T]] =
        t => R + t.elem + t.suffix
    }
    object InvalidSuffix {
      def apply(elem: Ident, suffix: String): InvalidSuffix =
        InvalidSuffixOf[AST](elem, suffix)
    }

  }

  //////////////////////////////////////////////////////////////////////////////
  //// Literal /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  // FIXME: Compatibility mode
  type Number = Literal.Number
  val Number = Literal.Number

  // FIXME: Compatibility mode
  type Text = Literal.Text
  val Text = Literal.Text

  type Literal = LiteralOf[AST]
  sealed trait LiteralOf[T] extends ShapeOf[T]
  object Literal {

    object implicits extends implicits
    trait implicits extends Number.implicits with Text.implicits {
      implicit def ftorLiteral: Functor[LiteralOf] = semi.functor
    }

    ////////////////
    //// Number ////
    ////////////////

    type Number = ASTOf[NumberOf]
    case class NumberOf[T](base: Option[String], int: String)
        extends LiteralOf[T]
        with Phantom

    object Number {

      //// DanglingBase ////

      type DanglingBase = DanglingBaseOf[AST]
      case class DanglingBaseOf[T](base: String)
          extends AST.InvalidOf[T]
          with Phantom

      object DanglingBase {
        def apply(base: String): DanglingBase = DanglingBaseOf(base)
      }

      //// Instances ////

      object implicits extends implicits
      trait implicits {
        implicit def ftorNum:      Functor[NumberOf]       = semi.functor
        implicit def ftorNumDang:  Functor[DanglingBaseOf] = semi.functor
        implicit def offZipNum[T]: OffsetZip[NumberOf, T]  = t => t.coerce
        implicit def offZipNumDang[T]: OffsetZip[DanglingBaseOf, T] =
          t => t.coerce
        implicit def reprNum[T]: Repr[NumberOf[T]] =
          t => t.base.map(_ + "_").getOrElse("") + t.int
        implicit def reprNumDang[T]: Repr[DanglingBaseOf[T]] = R + _.base + '_'
      }
      import implicits._

      //// Smart Constructors ////

      def apply(i: String):            Number = Number(None, i)
      def apply(b: String, i: String): Number = Number(Some(b), i)
      def apply(i: Int):               Number = Number(i.toString)
      def apply(b: Int, i: String):    Number = Number(b.toString, i)
      def apply(b: String, i: Int):    Number = Number(b, i.toString)
      def apply(b: Int, i: Int):       Number = Number(b.toString, i.toString)
      def apply(b: Option[String], i: String): Number =
        NumberOf[AST](b, i)
    }

    //////////////
    //// Text ////
    //////////////

    type Text = ASTOf[TextOf]
    sealed trait TextOf[T] extends ShapeOf[T]
    object Text {

      //// Definition ////

      type Line[T]  = List[T]
      type Block[T] = List1[Line[T]]

      type Raw          = ASTOf[RawOf]
      type Interpolated = ASTOf[InterpolatedOf]
      type Unclosed     = ASTOf[UnclosedOf]

      case class RawOf[T](quote: Quote, lines: Raw.Block[T]) extends TextOf[T] {
        val quoteChar: Char = '"'
      }
      case class InterpolatedOf[T](quote: Quote, lines: Interpolated.Block[T])
          extends TextOf[T] {
        val quoteChar: Char = '\''
      }
      case class UnclosedOf[T](text: TextOf[T]) extends AST.InvalidOf[T]

      object Raw {
        type Segment[T] = Text.Segment._Raw[T]
        type Line[T]    = Text.Line[Segment[T]]
        type Block[T]   = Text.Block[Segment[T]]
      }

      object Interpolated {
        type Segment[T] = Text.Segment._Interpolated[T]
        type Line[T]    = Text.Line[Segment[T]]
        type Block[T]   = Text.Block[Segment[T]]
      }

      // Instances ////

      object implicits extends implicits
      trait implicits extends Segment.implicits {
        implicit def reprTextRaw[T]:      Repr[RawOf[T]]          = _ => ???
        implicit def reprTextInt[T]:      Repr[InterpolatedOf[T]] = _ => ???
        implicit def reprTextUnclosed[T]: Repr[UnclosedOf[T]]     = _ => ???

        implicit def ftorTextRaw[T]: Functor[RawOf] =
          semi.functor
        implicit def ftorTextInterpolated[T]: Functor[InterpolatedOf] =
          semi.functor
        implicit def ftorTextUnclosed[T]: Functor[UnclosedOf] =
          semi.functor
        implicit def offZipTextRaw[T]: OffsetZip[RawOf, T] =
          t =>
            t.copy(lines = t.lines.map(_.map(offZipTxtSRaw.zipWithOffset(_))))
        implicit def offZipTextInt[T]: OffsetZip[InterpolatedOf, T] =
          t =>
            t.copy(lines = t.lines.map(_.map(offZipTxtSInt.zipWithOffset(_))))
        implicit def offZipUnclosed[T]: OffsetZip[UnclosedOf, T] =
          t => t.copy(text = OffsetZip(t.text))
        implicit def offZipText[T]: OffsetZip[TextOf, T] = {
          case t: RawOf[T]          => OffsetZip(t)
          case t: InterpolatedOf[T] => OffsetZip(t)
        }
      }

      ///////////////
      //// Quote ////
      ///////////////

      sealed trait Quote { val asInt: Int }
      object Quote {
        final case object Single extends Quote { val asInt = 1 }
        final case object Triple extends Quote { val asInt = 3 }
      }

      /////////////////
      //// Segment ////
      /////////////////

      sealed trait Segment[T]
      object Segment {

        // FIXME: Compatibility mode
        trait Escape

        // FIXME: Compatibility mode
        case class EOL()

        //// Definition ////

        type Interpolated = _Interpolated[AST]
        type Raw          = _Raw[AST]
        sealed trait _Interpolated[T] extends Segment[T]
        sealed trait _Raw[T]          extends _Interpolated[T]

        type Plain = _Plain[AST]
        type Expr  = _Expr[AST]
        case class _Plain[T](value: String)   extends _Raw[T] with Phantom
        case class _Expr[T](value: Option[T]) extends _Interpolated[T]

        //// Instances ////

        object implicits extends implicits
        trait implicits {
          implicit def reprTxtSPlain[T]: Repr[_Plain[T]] = _.value
          implicit def reprTxtSExpr[T: Repr]: Repr[_Expr[T]] =
            R + '`' + _.value + '`'
          implicit def ftorTxtSPlain[T]:   Functor[_Plain]      = semi.functor
          implicit def ftorTxtSExpr[T]:    Functor[_Expr]       = semi.functor
          implicit def offZipTxtSExpr[T]:  OffsetZip[_Expr, T]  = _.map((0, _))
          implicit def offZipTxtSPlain[T]: OffsetZip[_Plain, T] = t => t.coerce
          implicit def reprTxtSRaw[T]: Repr[_Raw[T]] = {
            case t: _Plain[T] => Repr(t)
          }
          implicit def reprTxtSInt[T: Repr]: Repr[_Interpolated[T]] = {
            case t: _Plain[T] => Repr(t)
            case t: _Expr[T]  => Repr(t)
          }
          implicit def ftorTxtSRaw[T]: Functor[_Raw]          = semi.functor
          implicit def ftorTxtSInt[T]: Functor[_Interpolated] = semi.functor
          implicit def offZipTxtSRaw[T]: OffsetZip[_Raw, T] = {
            case t: _Plain[T] => OffsetZip(t)
          }
          implicit def offZipTxtSInt[T]: OffsetZip[_Interpolated, T] = {
            case t: _Plain[T] => OffsetZip(t)
            case t: _Expr[T]  => OffsetZip(t)
          }
          implicit def txtFromString[T](str: String): _Plain[T] = _Plain(str)
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// App /////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Definition ////

  type App = AppOf[AST]
  sealed trait AppOf[T] extends ShapeOf[T]
  object App {

    //// Constructors ////

    type Prefix = ASTOf[PrefixOf]
    type Infix  = ASTOf[InfixOf]
    case class PrefixOf[T](fn: T, off: Int, arg: T) extends AppOf[T]
    case class InfixOf[T](larg: T, loff: Int, opr: Opr, roff: Int, rarg: T)
        extends AppOf[T]

    //// Instances ////

    object implicits extends implicits
    trait implicits extends Section.implicits {
      implicit def reprPrefix[T: Repr]: Repr[PrefixOf[T]] =
        t => R + t.fn + t.off + t.arg
      implicit def reprInfix[T: Repr]: Repr[InfixOf[T]] =
        t => R + t.larg + t.loff + t.opr + t.roff + t.rarg
      implicit def ftorPrefix: Functor[PrefixOf] = semi.functor
      implicit def ftorInfix:  Functor[InfixOf]  = semi.functor
      implicit def offZipPrefix[T: Repr]: OffsetZip[PrefixOf, T] =
        t => t.copy(fn = (0, t.fn), arg = (Repr(t.fn).span + t.off, t.arg))
      implicit def offZipInfix[T: Repr]: OffsetZip[InfixOf, T] = t => {
        val rargSpan = (R + t.larg + t.loff + t.opr + t.roff).span
        t.copy(larg = (0, t.larg), rarg = (rargSpan, t.rarg))
      }
    }
    import implicits._

    //// Smart Constructors ////

    object Prefix {
      val any             = UnapplyByType[Prefix]
      def unapply(t: AST) = Unapply[Prefix].run(t => (t.fn, t.arg))(t)
      def apply(fn: AST, off: Int, arg: AST): Prefix = PrefixOf(fn, off, arg)
      def apply(fn: AST, arg: AST):           Prefix = Prefix(fn, 1, arg)
    }

    object Infix {
      val any             = UnapplyByType[Infix]
      def unapply(t: AST) = Unapply[Infix].run(t => (t.larg, t.opr, t.rarg))(t)
      def apply(larg: AST, loff: Int, opr: Opr, roff: Int, rarg: AST): Infix =
        InfixOf(larg, loff, opr, roff, rarg)
      def apply(larg: AST, loff: Int, opr: Opr, rarg: AST): Infix =
        Infix(larg, loff, opr, 1, rarg)
      def apply(larg: AST, opr: Opr, roff: Int, rarg: AST): Infix =
        Infix(larg, 1, opr, roff, rarg)
      def apply(larg: AST, opr: Opr, rarg: AST): Infix =
        Infix(larg, 1, opr, 1, rarg)
    }

    /////////////////
    //// Section ////
    /////////////////

    type Section = SectionOf[AST]
    sealed trait SectionOf[T] extends AppOf[T]
    object Section {

      //// Constructors ////

      type Left  = ASTOf[LeftOf]
      type Right = ASTOf[RightOf]
      type Sides = ASTOf[SidesOf]

      case class LeftOf[T](arg: T, off: Int, opr: Opr)  extends SectionOf[T]
      case class RightOf[T](opr: Opr, off: Int, arg: T) extends SectionOf[T]
      case class SidesOf[T](opr: Opr)                   extends SectionOf[T] with Phantom

      //// Instances ////

      object implicits extends implicits
      trait implicits {
        implicit def ftorLeft:  Functor[LeftOf]  = semi.functor
        implicit def ftorRight: Functor[RightOf] = semi.functor
        implicit def ftorSides: Functor[SidesOf] = semi.functor
        implicit def reprLeft[T: Repr]: Repr[LeftOf[T]] =
          t => R + t.arg + t.off + t.opr
        implicit def reprRight[T: Repr]: Repr[RightOf[T]] =
          t => R + t.opr + t.off + t.arg
        implicit def reprSides[T: Repr]: Repr[SidesOf[T]] =
          t => R + t.opr
        implicit def offZipLeft[T]: OffsetZip[LeftOf, T] =
          t => t.copy(arg = (0, t.arg))
        implicit def offZipRight[T]: OffsetZip[RightOf, T] =
          t => t.copy(arg = (Repr(t.opr).span + t.off, t.arg))
        implicit def offZipSides[T]: OffsetZip[SidesOf, T] =
          t => t.coerce
      }
      import implicits._

      //// Smart Constructors ////

      object Left {
        val any             = UnapplyByType[Left]
        def unapply(t: AST) = Unapply[Left].run(t => (t.arg, t.opr))(t)
        def apply(arg: AST, off: Int, opr: Opr): Left = LeftOf(arg, off, opr)
        def apply(arg: AST, opr: Opr):           Left = Left(arg, 1, opr)
      }

      object Right {
        val any             = UnapplyByType[Right]
        def unapply(t: AST) = Unapply[Right].run(t => (t.opr, t.arg))(t)
        def apply(opr: Opr, off: Int, arg: AST): Right = RightOf(opr, off, arg)
        def apply(opr: Opr, arg: AST):           Right = Right(opr, 1, arg)
      }

      object Sides {
        val any             = UnapplyByType[Sides]
        def unapply(t: AST) = Unapply[Sides].run(_.opr)(t)
        def apply(opr: Opr): Sides = SidesOf[AST](opr)
      }

    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Block ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  val newline = R + '\n'

  type Block = ASTOf[BlockOf]
  case class BlockOf[T](
    typ: Block.Type,
    indent: Int,
    emptyLines: List[Int],
    firstLine: Block.LineOf[T],
    lines: List[Block.LineOf[Option[T]]]
  ) extends ShapeOf[T] {

    // FIXME: Compatibility mode
    def replaceType(ntyp: Block.Type): BlockOf[T] = copy(typ = ntyp)
  }

  object Block {
    sealed trait Type
    final case object Continuous    extends Type
    final case object Discontinuous extends Type

    //// Instances ////

    object implicits extends implicits
    trait implicits {
      implicit def ftorBlock: Functor[BlockOf] = semi.functor
      implicit def reprBlock[T: Repr]: Repr[BlockOf[T]] =
        t => {
          val emptyLinesRepr = t.emptyLines.map(R + _ + newline)
          val firstLineRepr  = R + t.indent + t.firstLine
          val linesRepr = t.lines.map { line =>
            newline + line.elem.map(_ => t.indent) + line
          }
          R + emptyLinesRepr + firstLineRepr + linesRepr
        }
      implicit def offZipBlock[T]: OffsetZip[BlockOf, T] = _.map((0, _))
    }
    import implicits._

    //// Smart Constructors ////

    // FIXME: Compatibility mode
    def apply(
      isOrphan: Boolean,
      typ: Type,
      indent: Int,
      emptyLines: List[Int],
      firstLine: LineOf[AST],
      lines: List[LineOf[Option[AST]]]
    ): Block = BlockOf(typ, indent, emptyLines, firstLine, lines)

    def apply(
      typ: Type,
      indent: Int,
      emptyLines: List[Int],
      firstLine: LineOf[AST],
      lines: List[LineOf[Option[AST]]]
    ): Block = BlockOf(typ, indent, emptyLines, firstLine, lines)

    def apply(
      typ: Type,
      indent: Int,
      firstLine: LineOf[AST],
      lines: List[LineOf[Option[AST]]]
    ): Block = Block(typ, indent, List(), firstLine, lines)

    val any = UnapplyByType[Block]
    def unapply(t: AST) =
      Unapply[Block].run(t => (t.typ, t.indent, t.firstLine, t.lines))(t)

    //// Line ////

    type Line         = LineOf[AST]
    type OptLineOf[T] = LineOf[Option[T]]
    type OptLine      = OptLineOf[AST]
    case class LineOf[+T](elem: T, off: Int) {
      // FIXME: Compatibility mode
      def toOptional: LineOf[Option[T]] = copy(elem = Some(elem))
    }
    object LineOf {
      implicit def reprLine[T: Repr]: Repr[LineOf[T]] = t => R + t.elem + t.off
    }
    object Line {
      // FIXME: Compatibility mode
      type NonEmpty = Line
      val Required                    = Line
      def apply[T](elem: T, off: Int) = LineOf(elem, off)
      def apply[T](elem: T): LineOf[T] = LineOf(elem, 0)
    }
    object OptLine {
      def apply():          OptLine = Line(None, 0)
      def apply(elem: AST): OptLine = Line(Some(elem))
      def apply(off: Int):  OptLine = Line(None, off)
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Module //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Module = ASTOf[ModuleOf]
  case class ModuleOf[T](lines: List1[Block.OptLineOf[T]]) extends ShapeOf[T]

  object Module {
    import Block._

    //// Instances ////

    object implicits extends implicits
    trait implicits {
      implicit def ftorModule: Functor[ModuleOf] = semi.functor
      implicit def reprModule[T: Repr]: Repr[ModuleOf[T]] =
        t => R + t.lines.head + t.lines.tail.map(newline + _)
      implicit def offZipModule[T]: OffsetZip[ModuleOf, T] = _.map((0, _))
    }
    import implicits._

    //// Smart Constructors ////

    def apply(ls: List1[OptLine]):            Module = ModuleOf(ls)
    def apply(l: OptLine):                    Module = Module(List1(l))
    def apply(l: OptLine, ls: OptLine*):      Module = Module(List1(l, ls.to[List]))
    def apply(l: OptLine, ls: List[OptLine]): Module = Module(List1(l, ls))
  }

  /////////////////////////////////////////////////
  /////////////////////////////////////////////////
  /////////////////////////////////////////////////
  /////////////////////////////////////////////////

  def main() {

    import implicits._

    val fff1 = AST.Ident.BlankOf[AST](): Ident.BlankOf[AST]
    val fff3 = Node(fff1): Blank
    val fff4 = fff3: AST

    object TT {
      def fooTest(t: (AST, Int)): Int = 8
      def fooTest(t: Int):        Int = t
    }
    val xr1 = TT.fooTest((fff3, 5))

    println(fff3)

    val v1   = Ident.Var("foo")
    val opr1 = Ident.Opr("+")
    val v2   = App.Prefix(Var("x"), 10, Var("z"))

    println(v1.name)
    println(opr1.assoc)

    val str1 = "foo": AST

    val fff = fff4.map(a => a)
    val vx  = v2: AST
    vx match {
      case Ident.Blank.any(v) => println(s"blank: $v")
      case Ident.Var.any(v)   => println(s"var: $v")
      case App.Prefix.any(v)  => println(s"app.prefix: $v")
    }

    println(vx.repr)

    v2.mapWithOff {
      case (i, t) =>
        println(s"> $i = $t")
        t
    }

//
//    val foo = Var("foo")
//    val bar = Var("foo")
////    val plus = Opr("+")
//
////    foo.map()
////    val ttt2   = foo: Shape
//    val fooAST = foo: AST
////    val fooAST = foo: Shape
//
////    val foox = foo: Shape
//
//    //    val foo    = Var("foo")
//    //    val foo2 = fix2(foo): FixedAST
//
//    //    println(foox.withNewID())
//    val v1 = Var("foo"): AST
//    val v2 = App.Prefix(v1, 2, v1): AST
////    val tfoo2 = Fix.implicits.fixDeep(tfoo): AST
//
//    v1 match {
//      case Var.any(v)        => println("VAR")
//      case App.Prefix.any(v) => println("PREFIX")
//      case Var(_)            => println("!!!!")
//      case Blank()           => println("DD")
//    }
//
//    v2 match {
//      case Var.any(v)        => println("VAR")
//      case App.Prefix.any(v) => println("PREFIX")
//      case Var(_)            => println("!!!!")
//      case Blank()           => println("DD")
//    }
//
//    v2 match {
//      case Var.any(v)       => println("VAR")
//      case App.Prefix(x, y) => println(s"PREFIX ${x}, ${y}")
//      case Var(_)           => println("!!!!")
//      case Blank()          => println("DD")
//    }
//
//    println("..........")
////    println(tfoo2)
////    println(ttt3.repr)
//
//    App.Prefix(fooAST, 0, bar)
//
  }
//
  ////////////////////////////////////////////////////////////////////////////
  //// Macro ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Macro = ASTOf[MacroOf]
  sealed trait MacroOf[T] extends ShapeOf[T]
  object Macro {

    import org.enso.syntax.text.ast.meta.Pattern

    //// Matched ////

    type Match = ASTOf[MatchOf]
    final case class MatchOf[T](
      pfx: Option[Pattern.Match],
      segs: Shifted.List1[Match.Segment],
      resolved: AST
    ) extends MacroOf[T] {
      def path(): List1[AST] = segs.toList1().map(_.el.head)
    }

    object MatchOf {
      implicit def ftor:      Functor[MatchOf]      = semi.functor
      implicit def offZip[T]: OffsetZip[MatchOf, T] = _.map((0, _))
      implicit def repr[T]: Repr[MatchOf[T]] = t => {
        val pfxStream = t.pfx.map(_.toStream.reverse).getOrElse(List())
        val pfxRepr   = pfxStream.map(t => R + t.el + t.off)
        val segsRepr  = t.segs.map(_.repr)
        R + pfxRepr + segsRepr
      }
    }

    object Match {
      val any = UnapplyByType[Match]
      def apply(
        pfx: Option[Pattern.Match],
        segs: Shifted.List1[Match.Segment],
        resolved: AST
      ): Match = MatchOf[AST](pfx, segs, resolved)

      final case class Segment(head: Ident, body: Pattern.Match) {
        val repr = R + head + body
        def toStream: AST.Stream = ??? // Shifted(head) :: body.toStream
        def isValid:  Boolean    = body.isValid
        def map(f: Pattern.Match => Pattern.Match): Segment =
          copy(body = f(body))
      }
      object Segment {
        def apply(head: Ident): Segment = Segment(head, Pattern.Match.Nothing())
      }
    }

    //// Ambiguous ////

    type Ambiguous = ASTOf[AmbiguousOf]
    final case class AmbiguousOf[T](
      segs: Shifted.List1[Ambiguous.Segment],
      paths: Tree[AST, Unit]
    ) extends MacroOf[T]
    object Ambiguous {
      def apply(
        segs: Shifted.List1[Ambiguous.Segment],
        paths: Tree[AST, Unit]
      ): Ambiguous = ??? // AmbiguousOf(segs, paths)

      final case class Segment(head: AST, body: Option[SAST])
      object Segment {
        def apply(head: AST): Segment = Segment(head, None)
      }
    }

    //// Resolver ////

    type Resolver = Resolver.Context => AST
    object Resolver {
      final case class Context(
        prefix: Option[Pattern.Match],
        body: List[Macro.Match.Segment],
        id: ID
      )
    }

    //// Definition ////

    type Definition = __Definition__
    final case class __Definition__(
      back: Option[Pattern],
      init: List[Definition.Segment],
      last: Definition.LastSegment,
      resolver: Resolver
    ) {
      def path: List1[AST] = init.map(_.head) +: List1(last.head)
      def fwdPats: List1[Pattern] =
        init.map(_.pattern) +: List1(last.pattern.getOrElse(Pattern.Nothing()))
    }
    object Definition {
      import Pattern._

      final case class Segment(head: AST, pattern: Pattern) {
        def map(f: Pattern => Pattern): Segment = copy(pattern = f(pattern))
      }
      object Segment {
        type Tup = (AST, Pattern)
        def apply(t: Tup): Segment = Segment(t._1, t._2)
      }

      final case class LastSegment(head: AST, pattern: Option[Pattern]) {
        def map(f: Pattern => Pattern): LastSegment =
          copy(pattern = pattern.map(f))
      }
      object LastSegment {
        type Tup = (AST, Option[Pattern])
        def apply(t: Tup): LastSegment = LastSegment(t._1, t._2)
      }

      def apply(back: Option[Pattern], t1: Segment.Tup, ts: List[Segment.Tup])(
        fin: Resolver
      ): Definition = {
        val segs    = List1(t1, ts)
        val init    = segs.init
        val lastTup = segs.last
        val last    = (lastTup._1, Some(lastTup._2))
        Definition(back, init, last, fin)
      }

      def apply(back: Option[Pattern], t1: Segment.Tup, ts: Segment.Tup*)(
        fin: Resolver
      ): Definition = Definition(back, t1, ts.toList)(fin)

      def apply(t1: Segment.Tup, t2_ : Segment.Tup*)(
        fin: Resolver
      ): Definition = Definition(None, t1, t2_.toList)(fin)

      def apply(initTups: List[Segment.Tup], lastHead: AST)(
        fin: Resolver
      ): Definition =
        Definition(None, initTups, (lastHead, None), fin)

      def apply(t1: Segment.Tup, last: AST)(fin: Resolver): Definition =
        Definition(List(t1), last)(fin)

      def apply(
        back: Option[Pattern],
        initTups: List[Segment.Tup],
        lastTup: LastSegment.Tup,
        resolver: Resolver
      ): Definition = {
        type PP = Pattern => Pattern
        val applyValidChecker: PP     = _ | ErrTillEnd("unmatched pattern")
        val applyFullChecker: PP      = _ :: ErrUnmatched("unmatched tokens")
        val applyDummyFullChecker: PP = _ :: Nothing()

        val unapplyValidChecker: Pattern.Match => Pattern.Match = {
          case Pattern.Match.Or(_, Left(tgt)) => tgt
          case _                              => throw new Error("Internal error")
        }
        val unapplyFullChecker: Pattern.Match => Pattern.Match = {
          case Pattern.Match.Seq(_, (tgt, _)) => tgt
          case _                              => throw new Error("Internal error")
        }
        val applySegInitCheckers: List[Segment] => List[Segment] =
          _.map(_.map(p => applyFullChecker(applyValidChecker(p))))

        val applySegLastCheckers: LastSegment => LastSegment =
          _.map(p => applyDummyFullChecker(applyValidChecker(p)))

        val unapplySegCheckers
          : List[AST.Macro.Match.Segment] => List[AST.Macro.Match.Segment] =
          _.map(_.map({
            case m @ Pattern.Match.Nothing(_) => m
            case m =>
              unapplyValidChecker(unapplyFullChecker(m))
          }))

        val initSegs           = initTups.map(Segment(_))
        val lastSeg            = LastSegment(lastTup)
        val backPatWithCheck   = back.map(applyValidChecker)
        val initSegsWithChecks = applySegInitCheckers(initSegs)
        val lastSegWithChecks  = applySegLastCheckers(lastSeg)

        def unexpected(ctx: Resolver.Context, msg: String): AST = {
          val pfxStream  = ctx.prefix.map(_.toStream).getOrElse(List())
          val segsStream = ctx.body.flatMap(_.toStream)
          val stream     = pfxStream ++ segsStream
//          AST.Unexpected(msg, stream)
          ???
        }

        def resolverWithChecks(ctx: Resolver.Context) = {
          val pfxFail  = !ctx.prefix.forall(_.isValid)
          val segsFail = !ctx.body.forall(_.isValid)
          if (pfxFail || segsFail) unexpected(ctx, "invalid statement")
          else {
            val ctx2 = ctx.copy(
              prefix = ctx.prefix.map(unapplyValidChecker),
              body   = unapplySegCheckers(ctx.body)
            )
            try resolver(ctx2)
            catch {
              case _: Throwable =>
                unexpected(ctx, "exception during macro resolution")
            }
          }
        }
        __Definition__(
          backPatWithCheck,
          initSegsWithChecks,
          lastSegWithChecks,
          resolverWithChecks
        )
      }

    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////
  //// Space - unaware AST /////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  sealed trait SpacelessASTOf[T] extends ShapeOf[T]

  implicit def ftorSlessAST[T]:   Functor[SpacelessASTOf]      = semi.functor
  implicit def offZipSlessAST[T]: OffsetZip[SpacelessASTOf, T] = _.map((0, _))

  //////////////////////////////////////////////////////////////////////////////
  /// Comment //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Comment = ASTOf[CommentOf]
  sealed trait CommentOf[T] extends SpacelessASTOf[T] with Phantom
  object Comment {
    val symbol = "#"

    type Disable    = DisableOf[AST]
    type SingleLine = SingleLineOf[AST]
    type MultiLine  = MultiLineOf[AST]

    case class DisableOf[T](ast: T)          extends CommentOf[T]
    case class SingleLineOf[T](text: String) extends CommentOf[T]
    case class MultiLineOf[T](off: Int, lines: List[String])
        extends CommentOf[T]

    // FIXME: Compatibility mode
    def SingleLine(t: String):              Comment = ???
    def MultiLine(i: Int, t: List[String]): Comment = ???
    def Disable(t: AST):                    Comment = ???

    //// Instances ////

    object implicits extends implicits
    trait implicits {
      implicit def ftorCmmDis[T]: Functor[DisableOf]    = semi.functor
      implicit def ftorCmmSL[T]:  Functor[SingleLineOf] = semi.functor
      implicit def ftorCmmML[T]:  Functor[MultiLineOf]  = semi.functor

      implicit def reprCmmDis[T: Repr]: Repr[DisableOf[T]] =
        R + symbol + " " + _.ast
      implicit def reprCmmSL[T]: Repr[SingleLineOf[T]] =
        R + symbol + symbol + _.text
      implicit def reprCmmML[T]: Repr[MultiLineOf[T]] = t => {
        val commentBlock = t.lines match {
          case Nil => Nil
          case line +: lines =>
            val indentedLines = lines.map { s =>
              if (s.forall(_ == ' ')) newline + s
              else newline + 1 + t.off + s
            }
            (R + line) +: indentedLines
        }
        R + symbol + symbol + commentBlock
      }

      // FIXME: How to make it automatic for non-spaced AST?
      implicit def offZipCmmDis[T]: OffsetZip[DisableOf, T]    = _.map((0, _))
      implicit def offZipCmmSL[T]:  OffsetZip[SingleLineOf, T] = _.map((0, _))
      implicit def offZipCmmML[T]:  OffsetZip[MultiLineOf, T]  = _.map((0, _))
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Import //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Import = ASTOf[ImportOf]
  case class ImportOf[T](path: List1[Cons]) extends SpacelessASTOf[T]
  object Import {

    //// Instances ////

    object implicits extends implicits
    trait implicits {
      implicit def ftorImport[T]: Functor[ImportOf] = semi.functor
      implicit def reprImport[T]: Repr[ImportOf[T]] =
        t => R + ("import " + t.path.map(_.repr.build()).toList.mkString("."))

      // FIXME: How to make it automatic for non-spaced AST?
      implicit def offZipImport[T]: OffsetZip[ImportOf, T] = _.map((0, _))
    }
    import implicits._

    //// Smart Constructors ////

    def apply(path: List1[Cons]):            Import = ImportOf[AST](path)
    def apply(head: Cons):                   Import = Import(head, List())
    def apply(head: Cons, tail: List[Cons]): Import = Import(List1(head, tail))
    def apply(head: Cons, tail: Cons*):      Import = Import(head, tail.toList)
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Mixfix //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Mixfix = MixfixOf[AST]
  case class MixfixOf[T](name: List1[Ident], args: List1[T])
      extends SpacelessASTOf[T]

  object Mixfix {
    def apply(name: List1[Ident], args: List1[AST]): Mixfix = ???
    object implicits extends implicits
    trait implicits {
      implicit def ftorMixfix[T]: Functor[MixfixOf] = semi.functor
      implicit def reprMixfix[T: Repr]: Repr[MixfixOf[T]] = t => {
        val lastRepr = if (t.name.length == t.args.length) List() else List(R)
        val argsRepr = t.args.toList.map(R + " " + _) ++ lastRepr
        val nameRepr = t.name.toList.map(Repr(_))
        R + (nameRepr, argsRepr).zipped.map(_ + _)
      }
      // FIXME: How to make it automatic for non-spaced AST?
      implicit def offZipMixfix[T]: OffsetZip[MixfixOf, T] = _.map((0, _))
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Group ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Group = ASTOf[GroupOf]
  case class GroupOf[T](body: Option[T]) extends SpacelessASTOf[T]
  object Group {

    //// Instances ////

    object implicits extends implicits
    trait implicits {
      implicit def ftorGrp[T]: Functor[GroupOf] = semi.functor
      implicit def reprGrp[T: Repr]: Repr[GroupOf[T]] =
        R + "(" + _.body + ")"
      // FIXME: How to make it automatic for non-spaced AST?
      implicit def offZipGroup[T]: OffsetZip[GroupOf, T] = _.map((0, _))
    }
    import implicits._

    //// Smart Constructors ////

    val any             = UnapplyByType[Group]
    def unapply(t: AST) = Unapply[Group].run(_.body)(t)
    def apply(body: Option[AST]): Group = Group(body)
    def apply(body: AST):         Group = Group(Some(body))
    def apply(body: SAST):        Group = Group(body.el)
    def apply():                  Group = Group(None)

  }

  //////////////////////////////////////////////////////////////////////////////
  //// Def /////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Def = ASTOf[DefOf]
  case class DefOf[T](name: Cons, args: List[T], body: Option[T])
      extends SpacelessASTOf[T]
  object Def {
    val symbol = "def"

    //// Instances ////

    object implicits extends implicits
    trait implicits {
      implicit def ftorDef[T]: Functor[DefOf] = semi.functor
      implicit def reprDef[T: Repr]: Repr[DefOf[T]] =
        t => R + symbol ++ t.name + t.args.map(R ++ _) + t.body
      // FIXME: How to make it automatic for non-spaced AST?
      implicit def offZipDef[T]: OffsetZip[DefOf, T] = _.map((0, _))
    }
    import implicits._

    //// Smart Constructors ////

    def apply(name: Cons):                  Def = Def(name, List())
    def apply(name: Cons, args: List[AST]): Def = Def(name, args, None)
    def apply(name: Cons, args: List[AST], body: Option[AST]): Def =
      DefOf(name, args, body)
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Foreign /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Foreign = ForeignOf[AST]
  case class ForeignOf[T](indent: Int, lang: String, code: List[String])
      extends SpacelessASTOf[T]

  object Foreign {

    //// Instances ////
    object implicits extends implicits
    trait implicits {
      implicit def ftorForeign[T]: Functor[ForeignOf] = semi.functor
      implicit def reprForeign[T: Repr]: Repr[ForeignOf[T]] = t => {
        val code2 = t.code.map(R + t.indent + _).mkString("\n")
        R + "foreign " + t.lang + "\n" + code2
      }
      // FIXME: How to make it automatic for non-spaced AST?
      implicit def offZipForeign[T]: OffsetZip[ForeignOf, T] = _.map((0, _))
    }
    import implicits._

    //// Smart Constructors ////

    def apply(indent: Int, lang: String, code: List[String]): Foreign =
      ForeignOf(indent, lang, code)
  }
}
