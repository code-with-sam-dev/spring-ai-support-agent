# Sourced by the scripts: find a JDK 25. JAVA_HOME_25 wins; then JAVA_HOME if it
# is a 25; then the SDKMAN default location. A JAVA_HOME left pointing at an
# older JDK cannot run the jar, so it is checked rather than trusted.
is25() { [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | grep -q 'version "25'; }
if [ -n "$JAVA_HOME_25" ]; then JAVA_HOME=$JAVA_HOME_25
elif [ -n "$JAVA_HOME" ] && is25 "$JAVA_HOME"; then :
elif is25 "$HOME/.sdkman/candidates/java/25.0.4-amzn"; then JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.4-amzn
else echo "JDK 25 not found: set JAVA_HOME_25" >&2; exit 1
fi
export JAVA_HOME
