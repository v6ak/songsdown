#syntax=docker/dockerfile:1.4

################################################################################

# Scala source —> JAR file
FROM sbtscala/scala-sbt:graalvm-ce-22.3.0-b2-java17_1.8.2_3.2.2 AS sbt-build
RUN mkdir app
WORKDIR app
ADD build.sbt build.sbt
ADD batch-converter/src batch-converter/src
ADD batch-converter/build.sbt batch-converter/build.sbt
ADD parser/build.sbt parser/build.sbt
ADD parser/src parser/src
ADD web-editor/build.sbt web-editor/build.sbt
ADD web-editor/src web-editor/src
ADD project project
#RUN sbt 'project parser' test
RUN \
  --mount=type=cache,target=/root/.ivy2 \
  --mount=type=cache,target=/root/.cache \
  sbt 'project batchConverter' test assembly

FROM sbt-build as quick
ENTRYPOINT ["java", "-Dfile.encoding=utf-8", "--add-modules", "jdk.localedata", "-jar", \
  "/root/app/batch-converter/target/scala-3.2.2/batch-converter.jar"]

# JAR file —> native executable
FROM ghcr.io/graalvm/native-image:ol9-java17-22.3.1 AS native-image
COPY --link --from=sbt-build /root/app/batch-converter/target/scala-3.2.2/batch-converter.jar assembly.jar
COPY --link reflection.json .
RUN native-image \
  -H:ReflectionConfigurationFiles=reflection.json \
  --static \
  -H:+IncludeAllLocales \
  --no-fallback \
  -H:+ReportExceptionStackTraces \
  -Dfile.encoding=utf-8 \
  -jar assembly.jar


# minimal image with the native executable; spellcheck will not work, as there is no Hunspell
FROM scratch AS bare
COPY --link --from=native-image /app/assembly /usr/bin/songsdown
ENTRYPOINT [ "/usr/bin/songsdown" ]


# tiny image with wget
FROM alpine AS downloader
RUN apk add --no-cache wget


# tiny image with Czech Hunspell dictionary
FROM downloader AS dic-cs
RUN wget \
  https://salsa.debian.org/libreoffice-team/libreoffice/libreoffice-dictionaries/-/raw/master/dictionaries/cs_CZ/cs_CZ.aff \
  https://salsa.debian.org/libreoffice-team/libreoffice/libreoffice-dictionaries/-/raw/master/dictionaries/cs_CZ/cs_CZ.dic


# Alpine image with Hunspell, but with no dicts
FROM alpine AS with-hunspell
RUN apk add --no-cache hunspell
COPY --link --from=native-image /app/assembly /usr/bin/songsdown
ENTRYPOINT [ "/usr/bin/songsdown" ]


# Alpine image with Hunspell + Czech dictionary
FROM with-hunspell AS with-hunspell-cs
COPY --link --from=dic-cs cs_CZ.aff cs_CZ.dic /usr/share/hunspell/

################################################################################

FROM downloader AS songs-dld
ARG SONGS_VERSION=2.18
RUN \
	wget https://downloads.sourceforge.net/project/songs/songs/Unix%20source/songs-${SONGS_VERSION}.tar.gz && \
	mv songs-${SONGS_VERSION}.tar.gz songs.tar.gz

FROM alpine AS songs-extract
RUN \
	--mount=type=cache,target=/var/cache/apk \
	apk add tar
COPY --link --from=songs-dld /songs.tar.gz /songs.tar.gz
RUN \
	mkdir /songs-extract && \
	tar xf songs.tar.gz --dir /songs-extract && \
	mv /songs-extract/songs-* /songs

FROM ubuntu:20.04 AS swiftlatex-texlive-ubuntu
RUN apt-get update && \
  DEBIAN_FRONTEND=noninteractive apt-get install -yqq --no-install-recommends \
  texlive-xetex lmodern texlive-fonts-recommended && \
  rm -rf /var/lib/apt/lists/* /var/cache/apt

FROM swiftlatex-texlive-ubuntu AS swiftlatex-texlive-filtered
ADD web-editor-swiftlatex-filter.agpl-infected/texlive-files.txt \
  web-editor-swiftlatex-filter.agpl-infected/copy-texlive-files.sh \
  /copier/
RUN /copier/copy-texlive-files.sh < /copier/texlive-files.txt

FROM swiftlatex-texlive-ubuntu AS songs-build
COPY --link --from=songs-extract /songs /songs
WORKDIR /songs
RUN \
	apt-get update && \
	apt-get install -yqq --no-install-recommends \
	gcc libc-dev make && rm -rf /var/lib/apt/lists/* /var/cache/apt
RUN ./configure || (cat config.log && false)
# It needs to run twice, because it fails on the first attempts
RUN make install prefix=/build || make install prefix=/build

################################################################################

FROM alpine AS git
RUN apk add --no-cache git

FROM git AS swiftlatex-src-downloader.agpl-infected
RUN git clone https://github.com/SwiftLaTeX/SwiftLaTeX && \
  cd SwiftLaTeX && \
  git checkout f39da903d162f1cae6a621862069a41a630790d6

FROM git AS swiftlatex-texlive.agpl-infected
RUN git clone https://github.com/SwiftLaTeX/Texlive-Ondemand && \
  cd Texlive-Ondemand && \
  git checkout cc2146e220658f5ee0f8643355252ae0995fbc98

FROM emscripten/emsdk:latest AS swiftlatex-build.agpl-infected
COPY --link --from=swiftlatex-src-downloader.agpl-infected /SwiftLaTeX /SwiftLaTeX
WORKDIR /SwiftLaTeX/pdftex.wasm
# workaround: https://github.com/SwiftLaTeX/SwiftLaTeX/issues/82
RUN make 'CXX=em++ -s EXPORTED_RUNTIME_METHODS=["cwrap","allocate"]'

FROM sbt-build AS sbt-web-editor-build
RUN \
  --mount=type=cache,target=/root/.ivy2 \
  --mount=type=cache,target=/root/.cache \
  sbt 'project webEditor' fullOptJS

FROM alpine AS web-editor-js-build-swiftlatex.agpl-infected
RUN apk add --no-cache jq
COPY --link --from=sbt-web-editor-build /root/app/web-editor/target/scala-3.2.2/web-editor-opt/main.js /src/
COPY --link --from=swiftlatex-build.agpl-infected /SwiftLaTeX/pdftex.wasm/PdfTeXEngine.js /src/
ADD web-editor-swiftlatex-filter.agpl-infected/swiftlatex-filter.js /src/
ADD web-editor-swiftlatex-filter.agpl-infected/songtemplate.tex /src
COPY --link --from=songs-build /build/share/texmf/tex/latex/songs/songs.sty /src
COPY /web-editor-swiftlatex-filter.agpl-infected/texlive-files.txt /src
RUN ( \
  echo "const LATEX_SONGS_TEMPLATE = $(cat /src/songtemplate.tex | jq --slurp --raw-input .);" && \
  echo "const LATEX_SONGS_STY = $(cat /src/songs.sty | jq --slurp --raw-input .);" && \
  echo "const TEXLIVE_FILES_TO_PREFETCH = $(grep -vE '^#' /src/texlive-files.txt | grep -vxF "" | \
    jq --slurp --raw-input 'split("\n") | map(select(.!=""))');" \
) > /tex-const.js
RUN cat /src/PdfTeXEngine.js /tex-const.js /src/swiftlatex-filter.js /src/main.js > /main.js

FROM scratch AS web-editor-swiftlatex.agpl-infected
COPY --link --from=sbt-web-editor-build /root/app/web-editor/src/main/resources/index.html /
COPY --link --from=web-editor-js-build-swiftlatex.agpl-infected /main.js /
COPY --link --from=swiftlatex-build.agpl-infected /SwiftLaTeX/pdftex.wasm/swiftlatexpdftex.wasm /SwiftLaTeX/pdftex.wasm/swiftlatexpdftex.js /
COPY --link --from=swiftlatex-texlive-filtered /out/pdftex /pdftex
COPY --link --from=swiftlatex-texlive.agpl-infected /Texlive-Ondemand/swiftlatexpdftex.fmt /pdftex/10/

################################################################################
FROM with-hunspell-cs AS default
