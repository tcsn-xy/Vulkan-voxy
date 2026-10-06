#!/bin/zsh
set -eu
cd "${0:A:h:h}"
qa_java="${JAVA_HOME:-$HOME/.cache/voxy-jdk25/jdk-25.0.4.1+1/Contents/Home}/bin/java"
python3 scripts/prepare-qa.py "$@"
export SDL_MAC_BACKGROUND_APP=1
exec nice -n 10 "$qa_java" @build/qa-instance/launch.args
