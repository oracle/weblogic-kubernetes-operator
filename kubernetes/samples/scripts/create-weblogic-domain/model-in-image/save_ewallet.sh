#!/bin/bash
# Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# Usage: save_ewallet.sh <domain uid> <name space>
#
#

kubectl -n ${2} describe configmap ${1}-weblogic-domain-introspect-cm | sed -n '/ewallet.p12/ {n;n;p}' > ewallet.p12
kubectl -n ${2} create configmap simple-domain1-opsskey-wallet --from-file=ewallet.p12
