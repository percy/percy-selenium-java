#!/bin/bash

set -o pipefail
set -e

# If we don't have a local Percy agent binary, run npm install.
if [ ! -f ./node_modules/.bin/percy ]; then
  echo "*** Percy agent not installed. Installing now. ***"
  npm install
fi

# Run the tests with snapshots.
./node_modules/.bin/percy exec -- mvn test
