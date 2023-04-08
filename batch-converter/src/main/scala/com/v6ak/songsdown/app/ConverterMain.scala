package com.v6ak.songsdown.app

import java.io._
import java.nio.file.{Files, Path, Paths}
import java.util.{Comparator, Locale}

import com.v6ak.songsdown._
import monocle.Lens
import monocle.syntax.all._
import monocle.macros.GenLens
import scopt.Read

import scala.jdk.CollectionConverters._
import scala.io.{BufferedSource, Source}
import scala.language.higherKinds
import scala.collection.parallel.CollectionConverters._
import com.v6ak.songsdown.app.Utils._


abstract sealed class Command:
  def run(): Unit


final case class SpellcheckOutputLang(
  noError: String,
  spellcheckTitle: String,
  suggestions: String,
)

val SpellcheckOutputLangEn = SpellcheckOutputLang(
  noError = "Spellcheck has found no issue.",
  spellcheckTitle = "Spellcheck",
  suggestions = "suggestions",
)

val SpellcheckOutputLangs = Map(
  "cs" -> SpellcheckOutputLang(
    noError = "Kontrola překlepů nic nenašla.",
    spellcheckTitle = "Kontrola překlepů",
    suggestions = "návrhy",
  ),
  "en" -> SpellcheckOutputLangEn,
)


final case class LatexCommand(
  inputSources: Seq[String],
  outputFile: Path,
  literalNames: Set[String] = Set(),
  songOrderingOption: Option[Ordering[Song]] = None,
  spellcheckLang: Option[String] = None,
  latexConfig: LaTeXConfig = LaTeXConfig(),
) extends Command:

  def addLiteralNames(path: Path): LatexCommand = try{
    val newNames = Source.fromFile(path.toFile, "utf-8").getLines.map(_.trim).filterNot(_=="").toSet
    copy(
      literalNames = literalNames ++ newNames
    )
  } catch {
    case e =>
      e.printStackTrace()
      throw e
  }

  private def sort(songs: IndexedSeq[(Option[Int], Song)]) = songOrderingOption match
    case Some(songOrdering) => songs.sorted(Ordering.by[(_, Song), Song](_._2)(songOrdering))
    case None => songs

  private def spellcheckSong(song: Song, lang: String, outLang: SpellcheckOutputLang) = {
    val results = spellcheck(song, lang)
    val title = if results.isEmpty
      then outLang.noError
      else s"${outLang.spellcheckTitle} (${results.size}}):"
    song.copy(
      elements = song.elements ++ Seq(UnnumberedVerse(
        IndexedSeq(Comment(title)) ++
          (
            for((word, suggestions) <- results)
            yield RegularRhyme(IndexedSeq(TextElement(
              s"$word (${outLang.suggestions}: $suggestions)"
            )))
          )
      ))
    )
  }

  private def spell(songs: IndexedSeq[(Option[Int], Song)]): IndexedSeq[(Option[Int], Song)] =
    spellcheckLang match
      case Some(lang) =>
        val outLang = SpellcheckOutputLangs.getOrElse(lang, SpellcheckOutputLangEn)
        (
          for ((n, song) <- songs.par)
          yield n -> spellcheckSong(song, lang, outLang)
        ).toIndexedSeq
      case None => songs

  override def run(): Unit ={
    val inputFiles = inputSources.flatMap(allFilesWithNumbers)
    val loadedSongs = for ((num, f) <- inputFiles) yield num -> loadSong(f)
    val songs = spell(sort(loadedSongs.toIndexedSeq))
    val nameMapping = literalNames.map(name =>
      name -> LaTeXUtils.escapeIncluding(' '->"\\ ")(name)
    ).toMap
    val latexSongs = for((n, song) <- songs)
      yield song.toLaTeX(latexConfig, songNumber = n, nameMapping = nameMapping)
    val latex = latexSongs.mkString("\n\n")
    Files.write(outputFile, latex getBytes "utf-8")
  }


final case class SpellCheckCommand(inputSources: Seq[Path], lang: String) extends Command:
  override def run(): Unit = {
    val inputFiles = inputSources.flatMap(allFiles)
    for((f, i) <- inputFiles.zipWithIndex){
      val song = Parser.parse(Source.fromFile(f.toFile, "utf-8"))

      val title = s"(${i+1}/${inputFiles.size}) ${song.name} (${f.toRealPath()})"
      print(title)
      print("...")
      val errors = spellcheck(song, lang)
      print("\b\b\b   \b\b\b")
      if(errors.isEmpty){
        print("\b"*title.length)
        print(" "*title.length)
        print("\b"*title.length)
      }else{
        println()
        println(errors.map("! "+_).mkString("\n"))
      }
    }
  }


final case class LintCommand(inputSources: Seq[Path]) extends Command:
  override def run(): Unit = {
    val inputFiles = inputSources.flatMap(allFiles)
    var ok = true
    for(f <- inputFiles){
      val song = loadSong(f)
      val duplicates = song.elements.filterNot(_ == ChorusRepetition)
        .groupBy(_.unchorded.simpleText.trim)
        .filter(_._2.size >= 2)
      val smells = Seq[Option[String]](
        duplicates.isEmpty match {
          case true => None
          case false => Some(
            "duplicate song elements:\n\n" +
              duplicates.map{case (k, v) => s"${v.size}×: $k"}.mkString("\n\n")
          )
        }
      ).flatten
      if(smells.nonEmpty){
        println("\n"+"="*80 + s"\n${song.name} (${f.toRealPath()}): "+smells.mkString("\n\n"))
        ok = false
      }
    }
    sys.exit( if(ok) 0 else 1)
  }


object ConverterMain:

  final class NoCommand extends Command:
    def run(): Unit ={
      System.err.println(parser.usage)
      sys.exit(1)
    }
    def lint: Command = LintCommand(Seq())
    def latex = LatexCommand(Seq(), null)
    def spellCheck = SpellCheckCommand(Seq(), null)

  implicit val pathRead: Read[Path] = new Read[Path] {
    override def arity: Int = 1
    override def reads: (String) => Path = s => Paths.get(new File(s).toURI)
  }

  val parser = new scopt.OptionParser[Command]("songsdown"){
    def inputFilesArg[C <: Command](name: String, lens: Lens[C, Seq[Path]]) =
      arg[Path](name)
        .unbounded()
        .required()
        .action{(f, c) => lens.modify(_ ++ Seq(f)).apply(c.asInstanceOf[C])}
    help("help")

    head(
      "Processes a human-friendly format of songs and converts it to LaTeX songs package format. " +
      "See https://songs.sourceforge.net/ for more information about the package."
    )

    note("\n")

    cmd("latex")
      .text("Generates LaTeX song output files.")
      .action { (_, c) => c.asInstanceOf[NoCommand].latex }
      .children(
        opt[Path]('o', "output").required().text("output file").action{(f, c) =>
          c.asInstanceOf[LatexCommand].focus(_.outputFile).replace(f)
        },
        opt[String]("sort-by-title")
          .valueName("<lang>")
          .text("sorts by title; you need to specify language for collation")
          .action{(langName, c) =>
            c.asInstanceOf[LatexCommand].focus(_.songOrderingOption).replace(
              Some(
                Ordering.by[Song, String](_.name)(
                  Ordering.comparatorToOrdering(
                    stringComparatorForLocale(Locale.forLanguageTag(langName))
                  )
                )
              )
            )
          },
        opt[String]("spellcheck")
          .valueName("<lang>")
          .text(
            "Runs spellcheck and adds it to the output. You need Hunspell and the dictionary " +
            "for the language."
          )
          .action{(l, c) => c.asInstanceOf[LatexCommand].focus(_.spellcheckLang).replace(Some(l))},
        opt[String]("no-chords-line-prefix")
          .valueName("<LaTeX code>")
          .text(
            "code that is prefixed to all lines in an unchorded verse. You can use negative vertical space like " +
            "\\ifchorded\\vspace{-10pt}\\fi, or maybe just a macro like \\nc"
          )
          .action{(code, c) => c.asInstanceOf[LatexCommand].focus(_.latexConfig.noChords).replace(code)},
        opt[Path]("literal-names")
          .valueName("<file>")
          .text("File with artist names that shall not be interpreted as first name + last name")
          .action{(path, c) => c.asInstanceOf[LatexCommand].addLiteralNames(path)},
        arg[String]("<input songs>")
          .unbounded()
          .required()
          .action{(f, c) => c.asInstanceOf[LatexCommand].focus(_.inputSources).modify(_ ++ Seq(f))}
      )

    note("\n")

    cmd("spellcheck")
      .text(
        "Runs spellcheck to the stdout. Alternatively, you can use --spellcheck with latex command."
      )
      .action { (_, c) => c.asInstanceOf[NoCommand].spellCheck }
      .children (
        opt[String]('l', "lang")
          .required()
          .action { (l, c) => c.asInstanceOf[SpellCheckCommand].focus(_.lang).replace(l)},
        inputFilesArg("<input songs>", GenLens[SpellCheckCommand](_.inputSources))
      )

    note("\n")

    cmd("lint").text("Looks for suspicious content in songs")
      .action { (_, c) => c.asInstanceOf[NoCommand].lint }.children (
        inputFilesArg("<input songs>", GenLens[LintCommand](_.inputSources))
      )
  }

  def main(args: Array[String]): Unit =
    parser.parse(args, new NoCommand) match {
      case Some(config) =>
        config.run()
        sys.exit(0)
      case None =>
        sys.exit(1)
    }
