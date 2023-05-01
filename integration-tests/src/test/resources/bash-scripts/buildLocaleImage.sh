#!/bin/bash
# Copyright (c) 2022, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Description:
#  Update a base WebLogic ol8 image with Japanese Locale

set -o errexit

base_image=${1}
http_proxy=${HTTP_PROXY:-""}
https_proxy=${HTTPS_PROXY:-""}
no_proxy=${NO_PROXY:-""}

# If the image name is phx.ocir.io/<tenancy>/test-images/weblogic:12.2.1.4
# The new image name will be weblogic:12.2.1.4-jp

tagbase=`echo ${base_image} | awk -F '/' '{print $NF}'`
tag="${tagbase}-jp"

cat << EOF > /tmp/Dockerfile
from  ${base_image}
USER root
RUN microdnf install glibc-langpack-ja
USER oracle
EOF

echo docker build -f /tmp/Dockerfile  --tag=${tag} . --build-arg http_proxy=${http_proxy}  --build-arg https_proxy=${https_proxy} -build-arg no_proxy=${no_proxy}

docker build -f /tmp/Dockerfile --tag=${tag} . --build-arg http_proxy=${http_proxy} --build-arg https_proxy=${https_proxy} --build-arg no_proxy=${no_proxy}
