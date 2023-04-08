package com.v6ak.songsdown

import LaTeXUtils.{escape => l, escapeExcept}

abstract sealed class RhymeElement:
  def text: String
  def toLaTeX(config: LaTeXConfig): String

final case class TextElement(text: String) extends RhymeElement:
  override def toLaTeX(config: LaTeXConfig) = l(text)

final case class ChordElement(chord: String) extends RhymeElement:
  override def toLaTeX(config: LaTeXConfig) = s"\\[${escapeExcept('&')(chord)}]"
  def text = s"[$chord]"
