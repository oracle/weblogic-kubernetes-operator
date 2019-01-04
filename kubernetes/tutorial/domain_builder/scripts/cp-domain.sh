#!/bin/bash
set -exu

echo "DOMAIN_HOME=$DOMAIN_HOME"
echo "DOMAIN_NAME=$DOMAIN_NAME"
echo "PV_ROOT=$PV_ROOT"

mkdir -p $PV_ROOT/domains/$DOMAIN_NAME
cp -rf $DOMAIN_HOME/* $PV_ROOT/domains/$DOMAIN_NAME
chown -R oracle:oracle $PV_ROOT


