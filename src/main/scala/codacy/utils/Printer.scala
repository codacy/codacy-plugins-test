package codacy.utils

import Console._

object Printer {
  private def removeColors(s: String) = s.replaceAll("\u001b\\[[0-9]+m", "")

  def green(str: String) = println(s"${GREEN}${removeColors(str)}${RESET}")
  
  def red(str: String) = println(s"${RED}${removeColors(str)}${RESET}")
}
