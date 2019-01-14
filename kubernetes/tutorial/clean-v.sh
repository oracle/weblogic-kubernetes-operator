#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

SECONDS=0
./traefik.sh delIng
./domain.sh delAll
./domain.sh waitUntilAllStopped
./traefik.sh delCon
./operator.sh delete
#./operator.sh delImages

echo "$0 took $(($SECONDS / 60)) minutes and $(($SECONDS % 60)) seconds to finish."
