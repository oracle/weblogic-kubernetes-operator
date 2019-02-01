#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

SECONDS=0
. ./env.sh

# clean load balancer
./$LB_TYPE.sh delIng
./$LB_TYPE.sh delCon

# clean WebLogic domains and operator
./domain.sh delAll
./domain.sh waitUntilAllStopped
./operator.sh delete
#./operator.sh delImages

echo "$0 took $(($SECONDS / 60)) minutes and $(($SECONDS % 60)) seconds to finish."
