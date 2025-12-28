#!/bin/bash
# Build and run CLJS browser tests

set -e

# Get the script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Building browser tests..."
clojure -M:test-browser -co "$SCRIPT_DIR/browser-build.edn" -c

echo "Opening browser tests..."
# Detect OS and use appropriate open command
case "$(uname -s)" in
  Darwin*)  open "$SCRIPT_DIR/public/index.html" ;;
  Linux*)   xdg-open "$SCRIPT_DIR/public/index.html" ;;
  MINGW*|MSYS*|CYGWIN*) start "$SCRIPT_DIR/public/index.html" ;;
  *)        echo "Please open $SCRIPT_DIR/public/index.html in your browser" ;;
esac
