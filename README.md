# Songsdown

Markdown-like syntax for [LaTeX songs](https://songs.sourceforge.net/).

While the Songs package is awesome for writing songbooks with chords, I didn't want to teach non-tech friends how to use
it. So, I've created a **Markdown-like syntax for songs**. You can have all the awesomeness of LaTeX/Songs typesetting
with user-friendly syntax.

## Features:

* human-friendly (Markdown-like) syntax
* optionally sorts (locale-aware) songs alphabetically
* runs spellcheck (In a nutshell, it passes text without chords to Hunspell.)
* optionally removes space for chords in chord-less verses
* chord transposition (limited by Songs package)

## Example input

    /// spellcheck-whitelist: somenewword
    Some Artist
    Lorem Ipsum Song
    
    [F]Lorem [A]ipsum [C]dolor sit amet,
    consectetuer [G]adi[C]pis[A]cing elit.
    Nullam [F]feugiat,
    turpis at [E]pulvinar [G]vulputate,
    erat libero tristique [A]tellus,
    nec bibendum [F]odio risus sit amet ante.
    
    R: [F]Phasellus [E]faucibus molestie nisl.
    Suspendisse sagittis [F]ultrices augue.
    Quis autem vel [A]eum iure reprehenderit
    qui in ea [G]voluptate velit esse
    quam nihil [C]molestiae consequatur,
    vel illum [E]qui dolorem eum fugiat
    quo voluptas [F]nulla pariatur?
    
    Temporibus autem quibusdam et aut officiis,
    debitis aut rerum necessitatibus
    saepe eveniet ut et voluptates | repudiandae
    sint et molestiae non recusandae.
    Duis viverra diam non justo.
    
    R:


## Example LaTeX output

    $ docker run -i ghcr.io/v6ak/songsdown-tiny latex -o /dev/stdout '--no-chords-line-prefix=\nc' /dev/stdin < lorem-ipsum.txt
    \beginsong{Lorem Ipsum Song}[by={Some Artist}]\transpose{0}
    \beginverse
    \[F]Lorem \[A]ipsum \[C]dolor sit amet,
    consectetuer \[G]adi\[C]pis\[A]cing elit.
    Nullam \[F]feugiat,
    turpis at \[E]pulvinar \[G]vulputate,
    erat libero tristique \[A]tellus,
    nec bibendum \[F]odio risus sit amet ante.
    \endverse
    \beginchorus
    \[F]Phasellus \[E]faucibus molestie nisl.
    Suspendisse sagittis \[F]ultrices augue.
    Quis autem vel \[A]eum iure reprehenderit
    qui in ea \[G]voluptate velit esse
    quam nihil \[C]molestiae consequatur,
    vel illum \[E]qui dolorem eum fugiat
    quo voluptas \[F]nulla pariatur?
    \endchorus
    \beginverse
    \nc Temporibus autem quibusdam et aut officiis,
    \nc debitis aut rerum necessitatibus
    \nc saepe eveniet ut et voluptates \nc\brk  repudiandae
    \nc sint et molestiae non recusandae.
    \nc Duis viverra diam non justo.
    \endverse
    \textnote{R:}
    \endsong%

## Removal space for chords in chord-less verses

The Songs package allows you to render the song either with verses or completely without them. However, one might want
compact output with chords – the first verse contains chords, other verses have no explicit chords (and no space for
them). There is probably no clean way to do that with Songs package without any modification.

Songsdown has a little hack for you. It can insert negative vertical spaces before any line in an unchorded verse. 
When you use '--no-chords-line-prefix={\nc}', it inserts `\nc` command, you can define it like this:

    \newcommand{\nc}{\ifchorded\vspace{-10pt}\fi}

Remember, this doesn't work well with automatic line-breaking. If you have a line which is too long, you need to wrap it
manually by `|`.

## Transposition

You can set chords transposition by adding a line with number after the song title, e.g.:

    Some Artist
    Lorem Ipsum Song
    +1

However, the implementation of transposition in the Songs package seems to be very limited

## Distribution

Currently, it is distributed just as a Docker image.
[Native image](https://www.graalvm.org/latest/reference-manual/native-image/) allows us to make the Docker image tiny.
You can chose between several packages:

https://github.com/v6ak/songsdown/pkgs/container/songsdown-tiny
* [ghcr.io/v6ak/songsdown-tiny](https://ghcr.io/v6ak/songsdown-tiny) — tiny image (no spellcheck support)
* [ghcr.io/v6ak/songsdown-hunspell](https://ghcr.io/v6ak/songsdown-hunspell) — Alpine-based image with Hunspell
  (for spellcheck), but no dictionary. You need to add a dicrionary yourself.
* [ghcr.io/v6ak/songsdown-hunspell-cs](https://ghcr.io/v6ak/songsdown-hunspell-cs) – Alpine-based image with Hunspell
  (for spellcheck) and Czech dictionary.

When you build it, it should run in any recent JRE (when compiled to JVM bytecode) and probably in a JS engine
(when compiled to Javscript using Scala.js).
