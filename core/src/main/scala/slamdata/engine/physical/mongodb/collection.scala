package slamdata.engine.physical.mongodb

import scalaz._
import Scalaz._

import slamdata.engine.{RenderTree, Terminal}
import slamdata.engine.fp._
import slamdata.engine.fs._

import scala.util.parsing.combinator._
import scala.util.parsing.combinator.lexical._
import scala.util.parsing.combinator.syntactical._
import scala.util.parsing.combinator.token._

case class Collection(databaseName: String, collectionName: String) {
  def asPath: Path = Path(databaseName + '/' + Collection.PathUnparser(collectionName))
}
object Collection {
  def fromPath(path: Path): PathError \/ Collection = PathParser(path.pathname).map((Collection.apply _).tupled)

  object PathParser extends RegexParsers {
    override def skipWhitespace = false

    def path: Parser[(String, String)] =
      "/" ~> rel | "./" ~> rel

    def rel: Parser[(String, String)] =
      seg ~ "/" ~ repsep(seg, "/") ^^ {
        case db ~ _ ~ collSegs => (db, collSegs.mkString("."))
      }

    def seg: Parser[String] =
      segChar.* ^^ { _.mkString }

    def segChar: Parser[String] =
      "."  ^^ κ("\\.") |
      "$"  ^^ κ("\\d") |
      "\\" ^^ κ("\\\\") |
      "[^/]".r

    def apply(input: String): PathError \/ (String, String) = parseAll(path, input) match {
      case Success(result, _) =>
        if (result._2.length > 120)
          -\/(PathError(Some("collection name too long (> 120 bytes): " + result)))
        else \/-(result)
      case failure : NoSuccess => -\/(PathError(Some("failed to parse ‘" + input + "’: " + failure.msg)))
    }
  }

  object PathUnparser extends RegexParsers {
    override def skipWhitespace = false

    def name = nameChar.* ^^ { _.mkString }

    def nameChar =
      "\\."  ^^ κ(".") |
      "\\d"  ^^ κ("$") |
      "\\\\" ^^ κ("\\") |
      "."    ^^ κ("/") |
      ".".r

    def apply(input: String): String = parseAll(name, input) match {
      case Success(result, _) => result
      case failure : NoSuccess => scala.sys.error("doesn't happen")
    }
  }

  implicit val CollectionRenderTree = new RenderTree[Collection] {
    def render(v: Collection) = Terminal(v.databaseName + "; " + v.collectionName, List("Collection"))
  }
}
