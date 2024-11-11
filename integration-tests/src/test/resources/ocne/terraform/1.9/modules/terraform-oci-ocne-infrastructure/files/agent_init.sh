#! /bin/bash

# Copyright (c) 2024 Oracle Corporation and/or affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl

sudo dd iflag=direct if=/dev/oracleoci/oraclevda of=/dev/null count=1
echo "1" | sudo tee /sys/class/block/`readlink /dev/oracleoci/oraclevda | cut -d'/' -f 2`/device/rescan
sudo /usr/libexec/oci-growfs -y

if [[ ${os_version} > 7.9 ]]; then
  if [[ ${yum_repo_url} != *"yum.oracle.com"* ]]; then
    dnf config-manager --add-repo ${yum_repo_url}
  fi
else
  if [[ ${yum_repo_url} != *"yum.oracle.com"* ]]; then
    yum-config-manager --add-repo ${yum_repo_url}
  fi
fi
