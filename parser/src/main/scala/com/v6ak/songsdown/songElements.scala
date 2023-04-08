package com.v6ak.songsdown

sealed abstract class SongElement:
  def text: String
  def simpleText: String
  def unchorded: SongElement
  def toLaTeX(config: LaTeXConfig): String

case object ChorusRepetition extends SongElement:
  override def toLaTeX(config: LaTeXConfig) = "\\textnote{R:}"
  override def unchorded: SongElement = this
  override def text: String = "R:"
  override def simpleText: String = "R:"

abstract sealed class RhymedSongElement extends SongElement:
  def rhymes: IndexedSeq[RhymeLike]
  def hasChords = rhymes.exists(_.hasChords)
  def innerRhymesToLaTeX(config: LaTeXConfig): String = {
    val linePrefix = if(hasChords) "" else config.noChords
    val linePrefixWithSpace = if(linePrefix == "") "" else linePrefix + " "
    rhymes.map(r =>
      s"$linePrefixWithSpace${r.toLaTeX(config).replace("|", s"$linePrefix\\brk ")}"
    ).mkString("\n")
  }
  override def simpleText: String = rhymes.map(_.text).mkString("\n")

final case class Chorus(rhymes: IndexedSeq[RhymeLike]) extends RhymedSongElement:
  override def toLaTeX(config: LaTeXConfig) = s"\\beginchorus\n${innerRhymesToLaTeX(config)}\n\\endchorus"
  override def unchorded: SongElement = copy(rhymes = rhymes.map(_.unchorded))
  override def text: String = "R: "+rhymes.map(_.text).mkString("\n")

final case class Verse(rhymes: IndexedSeq[RhymeLike]) extends RhymedSongElement:
  override def toLaTeX(config: LaTeXConfig) = s"\\beginverse\n${innerRhymesToLaTeX(config)}\n\\endverse"
  override def unchorded: SongElement = copy(rhymes = rhymes.map(_.unchorded))
  override def text: String = rhymes.map(_.text).mkString("\n")

final case class UnnumberedVerse(rhymes: IndexedSeq[RhymeLike]) extends RhymedSongElement:
  override def toLaTeX(config: LaTeXConfig) = s"\\beginverse*\n${innerRhymesToLaTeX(config)}\n\\endverse"
  override def unchorded: SongElement = copy(rhymes = rhymes.map(_.unchorded))
  override def text: String = "*"+rhymes.map(_.text).mkString("\n")
