#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

set -exu

cp /scripts/* /tmp/ # copy the py to another foder to avoid files generated to host folder during run
wlst.sh -skipWLSModuleScanning /tmp/create-domain.py
