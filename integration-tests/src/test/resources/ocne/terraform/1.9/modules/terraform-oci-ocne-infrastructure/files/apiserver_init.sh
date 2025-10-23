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

function getPackageSuffix() {
    packageSuffix="${ocne_version}"
    if [[ $(echo $${packageSuffix} | awk -F'.' '{print NF}') -lt 3 ]]; then
        packageSuffix=""
    fi
    if [[ $(echo $${packageSuffix} | awk -F'-' '{print NF}') -gt 1 ]]; then
      if [[ "$${packageSuffix}" != *".el7" ]] && [[ "$${packageSuffix}" != *".el8" ]] && [[ "$${packageSuffix}" != *".el9" ]] ; then
        if [[ $(cat /etc/oracle-release) == "Oracle Linux Server release 9."* ]]; then
          packageSuffix="$${packageSuffix}.el9"
        elif [[ $(cat /etc/oracle-release) == "Oracle Linux Server release 8."* ]]; then
          packageSuffix="$${packageSuffix}.el8"
        else
          packageSuffix="$${packageSuffix}.el7"
        fi
      fi
    fi
    if [[ "$${packageSuffix}" != "" ]]; then
      packageSuffix="-$${packageSuffix}"
    fi
    echo $${packageSuffix}
}

# install olcnectl on api-server
if [[ ${os_version} > 8.9 ]]; then
  dnf install -y oracle-olcne-release-el9
  dnf config-manager --disable ol9_olcne*
  dnf config-manager --enable ol9_olcne${ocne_short_version} ol9_addons ol9_baseos_latest
  packageSuffix=$(getPackageSuffix)
  dnf install -y olcnectl$${packageSuffix}
elif [[ ${os_version} > 7.9 && ${os_version} < 9 ]]; then
  dnf install -y oracle-olcne-release-el8
  dnf config-manager --disable ol8_olcne* ol8_UEKR*
  dnf config-manager --enable ol8_olcne${ocne_short_version} ol8_addons ol8_baseos_latest ${kernel_version}
  packageSuffix=$(getPackageSuffix)
  dnf install -y olcnectl$${packageSuffix}
else
  yum install -y oracle-olcne-release-el7
  yum-config-manager --disable ol7_olcne*
  yum-config-manager --enable ol7_olcne${ocne_short_version} ol7_kvm_utils ol7_addons ol7_latest
  packageSuffix=$(getPackageSuffix)
  yum install -y olcnectl$${packageSuffix}
fi

# prepare SSH keys on api-server
ssh-keygen -q -N "" -f /home/${compute_user}/.ssh/id_rsa -C "${compute_user}@api-server-001"
chown ${compute_user}: /home/${compute_user}/.ssh/id_rsa*
