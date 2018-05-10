#!/bin/sh
#
ARG="$*"
ENVSCRIPT=`find /shared -name setDomainEnv.sh -print`
echo Sourcing $ENVSCRIPT
. $ENVSCRIPT || exit 1
echo "$@"
echo Calling java weblogic.WLST $ARG
eval java weblogic.WLST $ARG || exit 1

