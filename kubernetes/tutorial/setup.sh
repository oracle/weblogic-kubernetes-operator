#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
set -e   # Exit immediately if a command exits with a non-zero status.

SECONDS=0
source ./setenv.sh
bash -e ./operator.sh precheck

# create domains
bash -e ./operator.sh pullImages
bash -e ./operator.sh create
bash -e ./domain.sh createPV
bash -e ./domain.sh createAll
bash -e ./domain.sh waitUntilAllReady

# setup load balancer
bash -e ./$LB_TYPE.sh createCon
bash -e ./$LB_TYPE.sh createIng

echo "$0 took $(($SECONDS / 60)) minutes and $(($SECONDS % 60)) seconds to finish."
