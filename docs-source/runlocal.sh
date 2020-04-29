#!/bin/bash

# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# 1313 is the hugo default port
port=${1:-1313}

hugo server -b http://localhost:$port/weblogic-kubernetes-operator -D -p $port
