#!/bin/bash

# Copyright (c) 2024 Oracle and/or its affiliates. All rights reserved.
# Licensed under the GNU General Public License Version 3 as shown at https://www.gnu.org/licenses/gpl-3.0.txt.

set -e
eval "$(jq -r '@sh "envName=\(.en) k8sName=\(.kn) ocneVer=\(.ov) cfgFile=\(.path) ERR=\(.error)"')"

ERR=""
function addErr() {
    if [ "${ERR}" == "" ]; then
  cat - <<EOF
$1
EOF
    else
  cat - <<EOF
${ERR}
    $1
EOF
    fi
}

yqMin="4.1"
if command -v yq >/dev/null 2>&1 ; then
    version=$(yq --version | awk '{print $NF}' | sed 's/^v//')
    oldver=$(printf "${version}"'\n'"${yqMin}"'\n' | sort -V | head -n1)
    if [[ "${oldver}" != "${yqMin}" ]]; then
        ERR=`addErr "Require yq >= v${yqMin}"`
    fi
else
    # echo "yq is not installed" >> debug.log
    ERR=`addErr "Require yq >= v${yqMin}"`
fi

function list_mods() {
    cat ${cfgFile} | yq eval '.environments[0].modules[].module'
}

function indexOf() {
  local mods=($(list_mods))
  local idx=-1
  local found=false
  for mod in "${mods[@]}"; do
      idx=$(($idx + 1))
      if [ "${mod}" == "$1" ]; then
          found=true
         echo $idx
      fi
  done
  if [ "${found}" == "false" ]; then
      echo -1
  fi
}

function nameOf() {
    yq e ".environments[0].modules[\"${1}\"].name" ${cfgFile}
}

function getArg() {
    local idx=$(indexOf ${1})
    yq e ".environments[0].modules[\"${idx}\"].args.\"${2}\"" ${cfgFile}
}

function getFilePath() {
  filePath=$(getArg ${1} ${2})
  if [ ${filePath} == null ]; then
      filePath=""
  else
    if [ -f "${filePath}" ]; then
        filePath="${filePath}"
    else
        filePath="The file of ${2} ${filePath} does not exist"
    fi
  fi
  echo "${filePath}"
}

ociKeyPath=""
kvrtConfig=""
if [[ -e ${cfgFile} ]]; then
    if [ "${envName}" != "myenvironment" ] && [ "${envName}" != "" ]; then
        ERR=`addErr "Remove variable environment_name=${envName} from terraform.tfvars"`
    fi
    if [ "${k8sName}" != "mycluster" ] && [ "${k8sName}" != ""  ]; then
        ERR=`addErr "Remove variable kubernetes_name=${k8sName} from terraform.tfvars"`
    fi
    if [ "${ocneVer}" != "1.7" ] && [ "${ocneVer}" != ""  ]; then
        ERR=`addErr "Remove variable ocne_version=${ocneVer} from terraform.tfvars"`
    fi
    numOfEnv=`yq eval '.environments | length' ${cfgFile}`
    if [[ "${numOfEnv}" != "1" ]]; then
        ERR=`addErr "config-file ${cfgFile} must have ONE environment"`
    fi
    mods=($(list_mods))
    findK=""
    for mod in "${mods[@]}"; do
       if [ "${mod}" == "kubernetes" ]; then
          if [ "${findK}" != "" ]; then
              ERR=`addErr "config-file ${cfgFile} must have ONE kubernetes module"`
          fi
          findK="kubernetes"
       fi
    done
    if [ "${findK}" == "" ]; then
        ERR=`addErr "config-file ${cfgFile} must have ONE kubernetes module"`
    fi
    indexOfKube=$(indexOf kubernetes)
    envName=`yq eval '.environments[0].environment-name' ${cfgFile}`
    k8sName=$(nameOf ${indexOfKube})
    ocneVer=`yq eval '.environments[0].globals.version' ${cfgFile}`
    if [ ${ocneVer} == null ]; then
        ocneVer=""
    fi
    ociKeyPath=$(getFilePath oci-ccm oci-private-key-file)
    if [[ "${ociKeyPath}" == *"does not exist" ]]; then
        ERR=`addErr "${ociKeyPath}"`
        ociKeyPath=""
    fi
    kvrtConfig=$(getFilePath kubevirt kubevirt-config)
    if [[ "${kvrtConfig}" == *"does not exist" ]]; then
        ERR=`addErr "${kvrtConfig}"`
        kvrtConfig=""
    fi
else
    if [[ "${cfgFile}" != "" ]]; then
        ERR=`addErr "The file of config_file_path ${cfgFile} does not exist"`
    fi
fi

# echo "config-read ERR: ${ERR}" >> debug.log
yq -o json <<EOF
environment_name: "${envName}"
kubernetes_name: "${k8sName}"
version: "${ocneVer}"
oci_api_key_path: "${ociKeyPath}"
kubevirt_config: "${kvrtConfig}"
error: |
    ${ERR}
EOF
