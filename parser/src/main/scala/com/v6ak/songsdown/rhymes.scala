package com.v6ak.songsdown

import LaTeXUtils.{escape => l, escapeExcept}

abstract sealed class RhymeLike:
  def toLaTeX(config: LaTeXConfig): String
  def hasChords: Boolean
  def text: String
  def unchorded: RhymeLike

final case class RegularRhyme(rhymeElements: IndexedSeq[RhymeElement]) extends RhymeLike:
  override def toLaTeX(config: LaTeXConfig) = rhymeElements.map(_.toLaTeX(config)).mkString
  override def hasChords: Boolean = rhymeElements.exists(_.isInstanceOf[ChordElement])
  override def text: String = rhymeElements.map(_.text).mkString("")
  override def unchorded: RhymeLike = copy(
    rhymeElements = rhymeElements.filterNot(_.isInstanceOf[ChordElement])
  )

final case class Comment(comment: String) extends RhymeLike:
  override def hasChords: Boolean = true // Hack for better viewing
  override def toLaTeX(config: LaTeXConfig): String = s"\\hspace*{1cm}\\textnote{${l(comment)}}"
  override def text = comment
  override def unchorded: RhymeLike = this
