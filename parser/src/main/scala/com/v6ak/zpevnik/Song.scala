package com.v6ak.zpevnik

import LaTeXUtils.{escape => l}

final case class Song(
  author: String,
  name: String,
  altNames: Seq[String],
  transposition: Int,
  elements: Seq[SongElement],
  spellcheckWhitelist: Set[String],
):
  def unchorded = copy(elements = elements.map(_.unchorded))
  def toLaTeX(config: LaTeXConfig): String = toLaTeX(config, None, nameMapping = Map())
  private def songNumLaTeX(songNum: Option[Int]) = songNum.fold("")(n => s"\\setcounter{songnum}{$n}")
  def toLaTeX(config: LaTeXConfig, songNumber: Option[Int], nameMapping: Map[String, String]): String = {
    val songParams = Seq(
      "by" -> nameMapping.getOrElse(author, l(author)),
    ) ++ altNames.map(altName =>
      "ititle" -> l(altName)
    )
    s"${songNumLaTeX(songNumber)}" +
      s"\\beginsong{${l(name)}}[${songParams.map((name, value) => s"$name={$value}").mkString(",")}]" +
      s"\\transpose{$transposition}\n" +
      s"${elements.map(_.toLaTeX(config)).mkString("\n")}\n" +
      s"\\endsong"
  }
  def text: String = s"$author\n$name\n$transposition\n\n"+elements.map(_.text).mkString("\n\n")
