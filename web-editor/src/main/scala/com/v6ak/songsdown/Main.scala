package com.v6ak.songsdown
import com.v6ak.songsdown.{LaTeXConfig, Parser}
import org.scalajs.dom.html.{Anchor, Div, IFrame, TextArea}
import org.scalajs.dom.{Event, document, window}

import scala.io.Source
import scala.scalajs.js
import scala.scalajs.js.Promise
import scala.scalajs.js.timers
import scala.util.{Failure, Success}
import scala.scalajs.js.URIUtils.{decodeURIComponent, encodeURIComponent}
import scala.util.Try

def delayedPromise[T](x: T, durationMs: Double): Promise[T] = new Promise[T]((keep, reject) =>
  timers.setTimeout(durationMs)(keep(x))
)

def promiseFromTry[T](x: Try[T]): Promise[T] = new Promise[T]((keep, reject) =>
  x match
    case Success(value) => keep(value)
    case Failure(exception) => reject(exception)
)

object Main:

  private def sample: String = FileContent.LoremIpsum

  var loading = false
  var hasLoadedSomething = false
  var dirty = true
  var lastValue = ""

  def text = document.getElementById("t").asInstanceOf[TextArea]

  private def textDataUri(s: String): String = "data:text/plain;charset=utf-8," + encodeURIComponent(s)

  private def newWindowLink = document.getElementById("new-window-link").asInstanceOf[Anchor]

  def iframe = document.getElementById("preview-iframe").asInstanceOf[IFrame]

  def setLoading(value: Boolean): Unit = {
    loading = value
    if (value) {
      hasLoadedSomething = true
    }
    document.title = if loading
      then "………"
      else "Editor"
    document.getElementById("loader").innerHTML = if loading
      then "Loading…"
      else (
        if hasLoadedSomething
          then "done"
          else "Preview will be shown there…"
      )
    document.getElementById("overlay").asInstanceOf[Div].className = if loading then "dim" else "";
    if (!value) {
      checkDirty();
    }
  }


  def change(e: Event): Unit = {
    /*
     * 1. mark as dirty if changed
     * 2. when nothing is in progress, process the current input and remove dirty flag
     * 3. when finished (either successfully or unsuccessfully), recheck dirty flag
     */
    val newValue = text.value
    if (newValue != lastValue) {
      lastValue = newValue
      window.location.hash = "#"+encodeURIComponent(newValue)
      dirty = true
      checkDirty()
    }
  }

  def checkDirty(): Unit = {
    if (dirty && !loading) {
      dirty = false
      process()
    }
  }

  def process(): Unit = {
    setLoading(true)
    val parsed = Try(Parser.parse(Source.fromString(text.value)))
    val texTry = parsed.map(_.toLaTeX(LaTeXConfig(noChords = "\\nc")))
    val dataUriFaliableFuture: Promise[String] = promiseFromTry(texTry)
      .`then`(text => filter(text))

    val dataUriFuture: Promise[String] = dataUriFaliableFuture
      .`catch`(e => textDataUri("ERROR:\n" + e.toString))
    dataUriFuture.`then`(
      onFulfilled = dataUri => {
        iframe.src = dataUri
        newWindowLink.href = dataUri
        newWindowLink.style.display = "";
        // checkDirty() – not needed there, it will be called in onLoad
        // If it was called there, it could be called before loading = true.
      }
    )
  }

  private def filter = window.asInstanceOf[js.Dynamic].songsdownFilter.asInstanceOf[js.Function1[String, Promise[String]]]

  private def filter_=(filter: js.Function1[String, Promise[String]]): Unit = {
    window.asInstanceOf[js.Dynamic].songsdownFilter = filter
  }

  def init(e: Event): Unit = {
    text.onchange = change _
    text.onkeyup = change _
    text.value = Some(decodeURIComponent(window.location.hash.drop(1))).filterNot(_=="").getOrElse(sample)
    text.readOnly = false
    if (js.isUndefined(filter)) {
      filter = (x) => delayedPromise(textDataUri(x), Math.random()*2000)
    }
    iframe.onload = _ => setLoading(false)
    iframe.asInstanceOf[js.Dynamic].onerror = ((_: Event) => setLoading(false)): js.Function1[Event, _]
    dirty = true
    checkDirty()
  }

  def main(args: Array[String]): Unit = {
    window.onload = init _
  }
