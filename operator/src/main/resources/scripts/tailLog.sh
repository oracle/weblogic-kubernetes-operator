#!/bin/bash

# Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script is used to tail the server log and is separate from
# startServer.sh so that it is easier to quickly kill the process
# running this script.
#

echo $$ > $2
tail -F -n +0 $1
