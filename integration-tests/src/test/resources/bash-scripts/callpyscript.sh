#!/bin/sh
# Copyright (c) 2018, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

ARG="$*"
. $DOMAIN_HOME/bin/setDomainEnv.sh
echo "$@"
echo Calling java weblogic.WLST $ARG
eval java weblogic.WLST $ARG || exit 1

