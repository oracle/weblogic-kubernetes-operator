#!/bin/bash
set -exu

cp /scripts/* /tmp/ # copy the py to another foder to avoid files generated to host folder during run
wlst.sh -skipWLSModuleScanning /tmp/create-domain.py
