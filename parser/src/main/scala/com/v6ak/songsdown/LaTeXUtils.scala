package com.v6ak.songsdown

object LaTeXUtils:

  val EscapableByBackslash = Set('&', '%', '$', '#', '_', '{', '}')

  val EscapableBySpecialSequence = Map(
    '~'  -> "\\textasciitilde",
    '^'  -> "\\textasciicircum",
    '\\' -> "\\textbackslash",
    '"'  -> "\\textquotedbl" // Needed in some cases in order to prevent collisions with Babel :(
  )

  val Escapes = (
    EscapableBySpecialSequence.view.mapValues(s => s"{$s}") ++
      EscapableByBackslash.map{c => c -> s"\\$c"}
  ).toMap

  def escape(s: String): String = s.flatMap(Escapes.withDefault(_.toString).apply)

  def escapeExcept(exceptions: Char*)(s: String): String = {
    val limitedEscapes = Escapes -- exceptions
    s.flatMap(limitedEscapes.withDefault(_.toString).apply)
  }

  def escapeIncluding(additions: (Char, String)*)(s: String): String = {
    val customEscapes = Escapes ++ additions
    s.flatMap(customEscapes.withDefault(_.toString).apply)
  }
