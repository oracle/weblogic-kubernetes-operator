#!/bin/bash

# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# 'build-war-app.sh'
#
# Build testwebapp.war for integration-test.
set -o errexit

usage() {
  echo "usage: ${BASH_SOURCE[0]} [-s <endpoint>] [-d <username>]"
  echo "  -s OCIR endpoint (optional) "
  echo "  -d user name (optional) "
  echo "  -h Help"
  exit $1
}

CURRDIR=`pwd`
WARFILENAME=testwebapp.war
SRCDIR=''
DESTDIR=''

while getopts ":s:d:" opt; do
  case $opt in
    s) SRCDIR="${OPTARG}"
    ;;
    d) DESTDIR="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

# Clean last war build
if [ -e ${DESTDIR}/${WARFILENAME} ]; then
    echo "Removing old war {DESTDIR}/${WARFILENAME}"
    rm -rf {DESTDIR}/${WARFILENAME}
fi

# Build war
if [ -d ${SRCDIR} ]; then
    echo "Found source at ${SRCDIR}"
    echo "build ${DESTDIR}/${WARFILENAME} with command jar -cvf ${DESTDIR}/${WARFILENAME} *"
    mkdir -p ${DESTDIR}
    cd ${SRCDIR}
    jar -cvf ${DESTDIR}/${WARFILENAME} *
    cd ${CURRDIR}
fi

# Show war details
ls -la ${DESTDIR}/${WARFILENAME}