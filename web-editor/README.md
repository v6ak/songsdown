# Web editor for Songsdown

## Information for end users

See [parent README.md](../README.md#web-based-editor)

## Information for developers

### Running in quick turnaround mode

    sbt ~fastOptJS
    python -m http.server -d server-root

### Build with SwiftLaTeX

AGPL

    DOCKER_BUILDKIT=1 docker build --target web-editor-swiftlatex.agpl-infected .. -o webedit
    python -m http.server -d webedit
