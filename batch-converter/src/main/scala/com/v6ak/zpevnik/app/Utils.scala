package com.v6ak.zpevnik.app

import java.io._
import java.nio.file.{Files, Path, Paths}
import java.text.{RuleBasedCollator, Collator}
import java.util.{Comparator, Locale}

import com.v6ak.zpevnik._

import scala.jdk.CollectionConverters._
import scala.io.{BufferedSource, Source}


object Utils:

  val NumberedPathPattern = """:([0-9]+):(.*)$""".r
  
  def allFiles(p: Path): Seq[Path] = Files.isDirectory(p) match
    case true => Files.newDirectoryStream(p).asScala.flatMap(allFiles).toSeq
    case false => Seq(p)

  def allFilesWithNumbers(d: String): Seq[(Option[Int], Path)] =
    if (d startsWith ":") {
      d match {
        case NumberedPathPattern(number, file) => Seq(Some(number.toInt) -> new File(file).toPath)
      }
    } else {
      val p = new File(d).toPath
      Files.isDirectory(p) match{
        case true => Files.newDirectoryStream(p).asScala.flatMap(allFiles).toSeq.map(None->_)
        case false => Seq((None, p))
      }
    }
  
  def loadSong(f: Path) = Parser.parse(Source.fromFile(f.toFile, "utf-8"))
  
  def spellcheck(song: Song, lang: String) = {
    val text = song.unchorded.text
    import sys.process._
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()
    def ioDaemon(in: InputStream, out: OutputStream) = new Thread{
      // TODO: Not a very clean solution, as we rely on this Thread not to throw any Exception
      override def run(): Unit = {
        out synchronized {
          val b = new Array[Byte](1024)
          var size = 0
          while({size = in.read(b); size != -1}){
            out.write(b, 0, size)
          }
        }
      }
    }.start()
    val process = Seq("hunspell", "-d", lang).run(new ProcessIO(
      out = {ioDaemon(_, out)},
      in = {i => i.write(text.getBytes("utf-8")); i.close()},
      err = {_ => ()},
    ))
    val exitValue = process.exitValue() // also waits
    if (exitValue != 0) {
      sys.error("bad return value: "+exitValue)
    }
    out.synchronized {
      new String(out.toByteArray, "utf-8").split("\n")
        .filter(_.startsWith("&"))
        .map(l => l.drop(1).trim.span(_ != ' '))
        .filterNot(song.spellcheckWhitelist contains _._1)
        .toIndexedSeq
    }
  }
  
  def stringComparatorForLocale(locale: Locale): Comparator[String] = {
    val collator = Collator.getInstance(locale).asInstanceOf[Comparator[String]]
    if (!Locale.getAvailableLocales.contains(locale)) {
      throw Exception(s"Unknown locale: $locale")
    }
    locale.getLanguage match {
      case "cs" => // Special fixes for Czech
        // Hack comes from http://jan.baresovi.cz/dr/en/java-collator-spaces
        // May break with a Java update. However, it might also become unneeded with a Java update…
        // Covered by CzechStringComparatorForLocaleTest
        new RuleBasedCollator(
          collator.asInstanceOf[RuleBasedCollator]
            .getRules
            .replaceAll("<'\u005f'", "<' '<'\u005f'")
        ).asInstanceOf[Comparator[String]]
      case _ => collator
    }
  }

