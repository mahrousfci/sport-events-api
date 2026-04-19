#!/bin/sh
# Maven Wrapper script
set -e
MAVEN_WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
MAVEN_OPTS="${MAVEN_OPTS:-}"
MAVEN_PROJECTBASEDIR="${MAVEN_PROJECTBASEDIR:-$(pwd)}"
DISTRIBUTION_URL=$(grep distributionUrl .mvn/wrapper/maven-wrapper.properties | cut -d= -f2)

if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
  WRAPPER_URL=$(grep wrapperUrl .mvn/wrapper/maven-wrapper.properties | cut -d= -f2)
  mkdir -p .mvn/wrapper
  wget -q "$WRAPPER_URL" -O "$MAVEN_WRAPPER_JAR" 2>/dev/null || \
    curl -sSL "$WRAPPER_URL" -o "$MAVEN_WRAPPER_JAR"
fi

exec java $MAVEN_OPTS \
  -classpath "$MAVEN_WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain \
  "$@"
