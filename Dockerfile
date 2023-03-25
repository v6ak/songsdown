#syntax=docker/dockerfile:1.4

# Scala source —> JAR file
FROM sbtscala/scala-sbt:graalvm-ce-22.3.0-b2-java17_1.8.2_3.2.2 AS sbt-build
RUN mkdir app
WORKDIR app
ADD build.sbt build.sbt
ADD batch-converter/src batch-converter/src
ADD batch-converter/build.sbt batch-converter/build.sbt
ADD parser/build.sbt parser/build.sbt
ADD parser/src parser/src
ADD project project
#RUN sbt 'project parser' test
RUN \
  --mount=type=cache,target=/root/.ivy2 \
  --mount=type=cache,target=/root/.cache \
  sbt 'project batchConverter' test assembly


# JAR file —> native executable
FROM  ghcr.io/graalvm/native-image:ol9-java17-22.3.1 AS native-image
COPY --link --from=sbt-build /root/app/batch-converter/target/scala-3.2.2/batch-converter.jar assembly.jar
COPY --link reflection.json .
RUN native-image \
  -H:ReflectionConfigurationFiles=reflection.json \
  --static \
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
