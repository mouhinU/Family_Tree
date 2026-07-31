#!/bin/sh
# Maven Wrapper script
set -e

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd "$(dirname "$0")" && pwd)}"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.9"

if [ ! -f "$MAVEN_HOME/bin/mvn" ]; then
    echo "Downloading Maven 3.9.9..."
    mkdir -p "$MAVEN_HOME"
    DIST_URL=$(grep 'distributionUrl' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
    TMP_ZIP="/tmp/maven-dist.zip"
    curl -fsSL "$DIST_URL" -o "$TMP_ZIP"
    unzip -qo "$TMP_ZIP" -d "$MAVEN_HOME"
    # unzip creates a subdirectory
    INNER_DIR=$(ls "$MAVEN_HOME" | grep apache-maven)
    if [ "$INNER_DIR" != "bin" ] && [ -d "$MAVEN_HOME/$INNER_DIR" ]; then
        mv "$MAVEN_HOME/$INNER_DIR"/* "$MAVEN_HOME"/
        rmdir "$MAVEN_HOME/$INNER_DIR" 2>/dev/null || true
    fi
    rm -f "$TMP_ZIP"
    echo "Maven installed to $MAVEN_HOME"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
