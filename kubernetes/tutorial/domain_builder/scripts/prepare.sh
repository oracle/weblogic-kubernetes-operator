#!/bin/bash

set -exu

echo "DOMAIN_ROOT=$DOMAIN_ROOT"
echo "DOMAIN_HOME=$DOMAIN_HOME"

if [ ! -e $DOMAIN_HOME ]; then
  mkdir -p $DOMAIN_HOME
  chown -R oracle:oracle $DOMAIN_ROOT 
fi

