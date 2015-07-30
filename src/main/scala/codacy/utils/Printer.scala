package codacy.utils

object Printer {

  def white(str: String) = println(s"${Console.WHITE} $str ${Console.WHITE}")

  def yellow(str: String) = println(s"${Console.YELLOW} $str ${Console.WHITE}")

  def red(str: String) = println(s"${Console.RED} $str ${Console.WHITE}")

  def green(str: String) = println(s"${Console.GREEN} $str ${Console.WHITE}")

}
