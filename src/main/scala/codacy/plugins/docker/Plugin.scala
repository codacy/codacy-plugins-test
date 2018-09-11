package codacy.plugins.docker

import com.codacy.plugins.api.results.{Result => ToolResult}
import codacy.plugins.traits.JsonEnumeration
import play.api.libs.json.{Format, JsValue, Json}

case class PluginRequest(directory: String, files: Seq[String], configuration: PluginConfiguration)

object PluginRequest {
  implicit val patternFmt: Format[Pattern] = Json.format[Pattern]
  implicit val configurationFmt: Format[PluginConfiguration] = Json.format[PluginConfiguration]
  implicit val requestFmt: Format[PluginRequest] = Json.format[PluginRequest]
}

case class Pattern(patternIdentifier: String, parameters: Option[Map[String, JsValue]])

case class PluginConfiguration(patterns: Seq[Pattern])

case class Result(patternIdentifier: String, filename: String, line: Int, message: String, level: ToolResult.Level)

case class PluginResult(results: Seq[Result], failedFiles: Seq[String])

case object Language extends Enumeration with JsonEnumeration {
  val Javascript, Scala, CSS, LESS, SASS, PHP, C, CPP, ObjectiveC, Python, Ruby, Perl, Java, CSharp, VisualBasic, Go, Elixir, Clojure,
  CoffeeScript, Rust, Swift, Haskell, React, Shell, TypeScript, Jade, Stylus, XML, Dockerfile, PLSQL, JSON,
  Apex, Velocity, JSP, Visualforce, R, Powershell, Solidity, Kotlin = Value

  def getExtensions(value: Value): Seq[String] = {
    value match {
      case Javascript => List(".js", ".jsx", ".jsm", ".vue")
      case Scala => List(".scala")
      case CSS => List(".css")
      case LESS => List(".less")
      case SASS => List(".scss")
      case PHP => List(".php")
      case C => List(".c", ".h")
      case CPP => List(".cpp", ".hpp")
      case ObjectiveC => List(".m")
      case Python => List(".py")
      case Ruby => List(".rb")
      case Perl => List(".pl")
      case Java => List(".java")
      case CSharp => List(".cs")
      case VisualBasic => List(".vb")
      case Go => List(".go")
      case Elixir => List(".ex", ".exs")
      case Clojure => List(".clj", ".cljs", ".cljc", ".edn")
      case CoffeeScript => List(".coffee")
      case Rust => List(".rs", ".rlib")
      case Swift => List(".swift")
      case Haskell => List(".hs", ".lhs")
      case Shell => List(".sh")
      case Kotlin => List(".kt", ".kts")
      case TypeScript => List(".ts")
      case Jade => Seq(".jade")
      case Stylus => Seq(".styl", ".stylus")
      case XML => Seq(".xml", ".xsl", ".wsdl", ".pom")
      case Dockerfile => Seq(".dockerfile")
      case PLSQL => Seq(
        ".sql", // Normal SQL
        ".trg", // Triggers
        ".prc", ".fnc", // Standalone Procedures and Functions
        ".pld", // Oracle*Forms
        ".pls", ".plh", ".plb", // Packages
        ".pck", ".pks", ".pkh", ".pkb", // Packages
        ".typ", ".tyb", // Object Types
        ".tps", ".tpb" // Object Types
      )
      case JSON => Seq(".json")
      case Apex => Seq(".cls")
      case Velocity => Seq(".vm")
      case Visualforce => Seq(".page", ".component")
      case JSP => Seq(".jsp")
      case R => Seq(".R")
      case Powershell => List(".ps1", ".psc1", ".psd1", ".psm1", ".ps1xml", ".pssc", ".cdxml", ".clixml")
      case Solidity => List(".sol")
      case _ => Seq.empty
    }
  }
}

object CategoryType extends Enumeration with JsonEnumeration {
  val Security, CodeStyle, ErrorProne, Performance, Compatibility, Documentation, UnusedCode,

  //Deprecated
  Complexity, BestPractice, Comprehensibility, Duplication = Value

  def fromString(s: String): Option[Value] =
    values.find(_.toString == s)
}
