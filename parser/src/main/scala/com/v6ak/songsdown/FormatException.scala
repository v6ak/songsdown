package com.v6ak.songsdown

case class FormatException(
  error: String,
  sample: Option[String],
  lineNumber: Option[Int]
) extends RuntimeException(
  Seq(
    Some(s"error: $error"),
    lineNumber.map("line: "+_),
    sample.map("sample: "+_),
  ).flatten.mkString("\n")
)
