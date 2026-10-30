#!/bin/bash
# Headless runs of the whole journey. Usage: tools/simtest/run.sh [runs] [max-game-hours] [v]
# Compiles only the sim package (no Android) with the Kotlin compiler bundled in Gradle's lib folder.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
R="$HERE/../../app/src/main/java/com/jondude1/summonersrpg"
L=$(ls -d /opt/gradle-*/lib 2>/dev/null | head -1)
[ -z "$L" ] && L=$(dirname "$(find / -name 'kotlin-compiler-embeddable-*.jar' 2>/dev/null | head -1)")
CC=$(ls $L/kotlin-compiler-embeddable-*.jar | head -1); V=$(basename $CC .jar | sed 's/kotlin-compiler-embeddable-//')
CP=$CC:$L/kotlin-stdlib-$V.jar:$L/kotlin-script-runtime-$V.jar:$L/kotlin-reflect-$V.jar:$L/kotlin-daemon-embeddable-$V.jar:$(ls $L/trove4j-*.jar):$(ls $L/annotations-*.jar | head -1):$(ls $L/kotlinx-coroutines-core-jvm-*.jar | head -1)
OUT=$(mktemp -d)
java -cp $CP org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp $L/kotlin-stdlib-$V.jar -d $OUT $R/sim/*.kt $HERE/stubs/org/json/Json.kt $HERE/SimMain.kt 2>&1 | grep -vE "^warning|JAVA_TOOL" | head -40
java -cp $OUT:$L/kotlin-stdlib-$V.jar SimMainKt "$@" 2>&1 | grep -v JAVA_TOOL
