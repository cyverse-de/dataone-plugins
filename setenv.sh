#!/bin/bash

export CATALINA_OPTS="-Dorg.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH=true"
export CATALINA_OPTS="$CATALINA_OPTS -Dorg.apache.catalina.connector.CoyoteAdapter.ALLOW_BACKSLASH=true"
