#!/bin/sh
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

ARG="$*"
ENVSCRIPT=`find /u01 . -name setDomainEnv.sh -print`
echo Sourcing $ENVSCRIPT
. $ENVSCRIPT || exit 1
echo "$@"
echo Calling java weblogic.WLST $ARG
eval java weblogic.WLST $ARG || exit 1

