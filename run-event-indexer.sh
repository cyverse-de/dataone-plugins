#!/bin/sh

set -e

# Generate the configuration files.
generate-configs.sh

# Start the event indexer.
cd /opt/dataone/indexer
java -jar default-event-service-indexer.jar
