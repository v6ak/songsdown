package com.v6ak.songsdown

import scala.io.Source

object Parser:

  private def parseParagraphs(lines: Seq[(String, Int)]): LazyList[Seq[(String, Int)]] = lines match
    case Seq() => LazyList.empty
    case _ =>
      val (firstParagraph, otherParagraphs) = lines.span{case (s, _) => s.trim != ""}
      firstParagraph #:: parseParagraphs(otherParagraphs.drop(1))

  def parseTransposition(t: (String, Int)) = try {
    t._1.toInt
  } catch {
    case _: NumberFormatException =>
      throw FormatException("Bad transposition", Some(t._1), Some(t._2))
  }

  val lineNumbersStream: LazyList[Int] = LazyList from 1

  /*val MajorChordPattern = """^(.)maj$""".r
  val MinorChordPattern = """^(.)(?:mi|♭)$""".r

  val MajorChordModifier = "#"
  val MinorChordModifier = "&"
  val NoChordModifier = ""*/

  def parseChord(s: String): ChordElement = {
    /*val (base, modifier) = s match {
      case MajorChordPattern(b) => (b, MajorChordModifier)
      case MinorChordPattern(b) => (b, MinorChordModifier)
      case b => (b, NoChordModifier)
    }
    ChordElement(base+modifier)*/
    ChordElement(s)
  }

  def parseRhymeText(s: String, line: Int): LazyList[RhymeElement] = {
    s match {
      case "" => LazyList.empty
      case _ =>
        s.span(_ != '[') match {
          case (text, "") => LazyList.apply(TextElement(text))
          case (text, chordAndRest) =>
            val (chord, restWithRSB) = chordAndRest.drop(1).span(_ != ']')
            if(restWithRSB startsWith "]") {
              val rest = restWithRSB.drop(1)
              TextElement(text) #:: parseChord(chord) #:: parseRhymeText(rest, line)
            }else{
              throw new FormatException("Unclosed chord", Some(s), Some(line))
            }
        }
    }
  }

  def parseRhyme(rawRhyme: (String, Int)): RhymeLike = rawRhyme._1 match {
    case LineCommentRegexp(comment) => Comment(comment)
    case _ => RegularRhyme(parseRhymeText(rawRhyme._1, rawRhyme._2).toIndexedSeq)
  }

  def parseRhymes(rawRhymes: Seq[(String, Int)]): Seq[RhymeLike] = rawRhymes.map(parseRhyme)

  private val ChorusStart = """^(?:R|®|Ref):(.*)$""".r

  private val UnnumberedVerseStart = """^\*(.*)$""".r

  def parse(source: Source): Song = {
    val allParagraphs = parseParagraphs(source.getLines().zip(lineNumbersStream.iterator).toSeq)
    val (metaParagraphWithComments, paragraphs) = allParagraphs.toList match
      case metaParagraphWithComments :: paragraphs => (metaParagraphWithComments, paragraphs)
      case _ => throw new FormatException("Empty song", None, None)
    val (superComments, metaParagraphWithOrdinaryComments) = 
      metaParagraphWithComments.partition(isSuperComment)
    val (author, names, transposition) = metaParagraphWithOrdinaryComments.filterNot(isComment) match
      case Seq((authorL, _), (namesL, _)) => (authorL, namesL, 0)
      case Seq((authorL, _), (namesL, _), transpositionL) =>
        (authorL, namesL, parseTransposition(transpositionL))
      case other => throw FormatException(
        s"Expected two or three lines in the first paragraph, got ${other.length}",
        Some(other.map(_._1).mkString("\n")),
        None
      )
    val name :: altNames = names.split('|').map(_.trim).toList
    val meta = superComments
      .map(sc => sc._1.drop(3).trim.span(_ != ':'))
      .groupBy(_._1)
      .view.mapValues { case Seq(one) => one._2.drop(1).trim }
      .toMap
    val spellcheckWhitelist = meta.get("spellcheck-whitelist")
      .map(_.split("[, ]").map(_.trim).toSet)
      .getOrElse(Set())
    val unknownKeys = meta.keySet -- Set("spellcheck-whitelist")
    if (unknownKeys.nonEmpty) {
      throw new FormatException("Unknown meta: "+unknownKeys.mkString(", "), None, None)
    }
    val elements = paragraphs.filterNot(_.isEmpty).map(parseParagraph)
    Song(author, name, altNames, transposition, elements, spellcheckWhitelist)
  }
  
  private def parseParagraph(rhymes: Seq[(String, Int)]) = {
    val firstRhyme = rhymes.head
    
    def specialStart(firstLine: String) = 
      (Seq((firstLine.trim, firstRhyme._2)) ++ rhymes.tail)
        .filterNot(_._1.trim == "")
    
    firstRhyme._1 match {
      case ChorusStart(firstLine) =>
        val newRhymes = specialStart(firstLine)
        newRhymes.size match {
          case 0 => ChorusRepetition
          case _ => Chorus(parseRhymes(newRhymes).toIndexedSeq)
        }
      case UnnumberedVerseStart(firstLine) =>
        val newRhymes = specialStart(firstLine)
        UnnumberedVerse(parseRhymes(newRhymes).toIndexedSeq)
      case _ => Verse(parseRhymes(rhymes).toIndexedSeq)
    }
  }

  val LineCommentRegexp = """^//(.*)$""".r
  val LineSuperCommentRegexp = """^///(.*)$""".r

  def isComment(line: (String, Int)): Boolean = line._1 startsWith "//"
  def isSuperComment(line: (String, Int)): Boolean = line._1 startsWith "///"

