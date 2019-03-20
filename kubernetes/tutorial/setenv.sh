#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

export PV_ROOT=
#two options: traefik, voyager, match to two LB shell file name: traefik.sh and voyager.sh
export LB_TYPE=traefik
#two options, wlst or wdt, which match to the subfolder name under domainHomeBuilder
export DOMAIN_BUILD_TYPE=wdt

export WLS_OPT_ROOT=../../
export WLS_BASE_IMAGE=store/oracle/weblogic:12.2.1.3
#export WLS_OPERATOR_IMAGE=oracle/weblogic-kubernetes-operator:2.0
export WLS_OPERATOR_IMAGE=weblogic-kubernetes-operator:2.1


