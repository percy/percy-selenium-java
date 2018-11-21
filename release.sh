#!/bin/bash

set -o pipefail
set -e

# Set the TTY so that we can provide the GPG key passphrase interactively.
export GPG_TTY=$(tty)

# Clean all previous artifacts and update version number in the POM and Git tags
mvn release:clean release:prepare

# Build the release artifacts and deploy them to the remote repository.
mvn release:perform
