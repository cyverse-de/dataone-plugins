#!/bin/sh

set -e

# Generate the configuration files.
generate-configs.sh

# Start the service.
sh /runit.sh
