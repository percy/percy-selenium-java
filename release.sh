#!/bin/bash

set -o pipefail
set -e

# Set the TTY so that we can provide the GPG key passphrase interactively.
export GPG_TTY=$(tty)

mvn release:clean release:prepare

mvn release:perform
