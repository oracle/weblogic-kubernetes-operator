#!/bin/sh
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

ARG="$*"
ENVSCRIPT=`find /shared . -name setDomainEnv.sh -print`
echo Sourcing $ENVSCRIPT
. $ENVSCRIPT || exit 1
echo "$@"
echo Calling java weblogic.WLST $ARG
eval java weblogic.WLST $ARG || exit 1

