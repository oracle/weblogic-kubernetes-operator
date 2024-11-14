#!/bin/bash

# Copyright (c) 2024 Oracle and/or its affiliates. All rights reserved.
# Licensed under the GNU General Public License Version 3 as shown at https://www.gnu.org/licenses/gpl-3.0.txt.

inpFile=${1}
envName=${2}
k8sName=${3}
ocneApi=${4}
k8sApis=${5}
k8sPort=${6}
virtual=${7}
cpNodes=${8}
wkNodes=${9}
  proxy=${10}
noProxy=${11}
containerRegistry=${12}
ociUser=${13}
ocneVer=${14}

function list_mods() {
  cat ${inpFile} | yq eval '.environments[0].modules[].module'
}

function indexOf() {
  local mods=($(list_mods))
  local idx=-1
  local found=false
  for mod in "${mods[@]}"; do
      idx=$(($idx + 1))
      if [[ "${mod}" == "$1" ]]; then
          found=true
         echo $idx
      fi
  done
  if [[ "${found}" == "false" ]]; then
      echo -1
  fi
}

function setEnv() {
    yq e ".environments[0].globals.\"${1}\" |= \"${2}\"" -i ${inpFile}
}

function addEnv() {
    local cur=$(getEnv ${1})
    if [[ "${cur}" == "" || "${cur}" == "null" ]]; then
        setEnv ${1} "${2}"
    fi
}

function getEnv() {
    yq e ".environments[0].globals.\"${1}\"" ${inpFile}
}

function getArg() {
    local idx=$(indexOf ${1})
    yq e ".environments[0].modules[\"${idx}\"].args.\"${2}\"" ${inpFile}
}

# Default the arg value if it is not set 
function defArg() {
    local idx=$(indexOf ${1})
    local cur=$(getArg  ${1} ${2})
    if [[ "${cur}" == "" || "${cur}" == "null" ]]; then
      yq e ".environments[0].modules[\"${idx}\"].args.\"${2}\" |= \"${3}\"" -i ${inpFile}
    fi
}

# Set the arg value
function setArg() {
    local idx=$(indexOf ${1})
      yq e ".environments[0].modules[\"${idx}\"].args.\"${2}\" |= \"${3}\"" -i ${inpFile}
}

function addArg() {
    local idx=$(indexOf ${1})
    yq e ".environments[0].modules[\"${idx}\"].args.\"${2}\" += [\"${3}\"]" -i ${inpFile}
    # cat ${inpFile}
}

function wPort() {
    if [[ ${1} == *":${2}" ]]; then
        echo "${1}"
    else
        echo "${1}:${2}"
    fi
}

yq e ".environments[0].environment-name |= \"${envName}\"" -i ${inpFile}
ocneApi=$(wPort ${ocneApi} "8091")
setEnv api-server ${ocneApi}
if [[ "${virtual}" == "true" ]]; then
    setEnv virtual-ip ${k8sApis}
else
    k8sApis=$(wPort ${k8sApis} ${k8sPort})
    setEnv load-balancer ${k8sApis}
fi
addEnv remote-command "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no"
if [[ "${ocneVer}" != "" ]]; then
    curVer=$(getEnv version)
    if [[ "${curVer}" == "" || "${curVer}" == "null" || "${curVer}" == "1.7" ]]; then
        setEnv version "${ocneVer}"
    fi
fi
if [[ "${proxy}" != "" ]]; then
    addEnv https-proxy ${proxy}
    addEnv http-proxy  ${proxy}
fi
if [[ "${noProxy}" != "" ]]; then
    addEnv no-proxy ${noProxy}
fi

indexOfHelm=$(indexOf helm)
indexOfKube=$(indexOf kubernetes)

ociKeyPath=$(getArg oci-ccm oci-private-key-file)
if [ ${ociKeyPath} != null ]; then
    setArg oci-ccm oci-private-key-file "/home/${ociUser}/oci_api_key.pem"
fi
kvrtConfig=$(getArg kubevirt kubevirt-config)
if [ ${kvrtConfig} != null ]; then
    setArg kubevirt kubevirt-config "/home/${ociUser}/kubevirt_config.yaml"
fi

yq e ".environments[0].modules[\"${indexOfKube}\"].name |= \"${k8sName}\"" -i ${inpFile}
defArg kubernetes "container-registry" ${containerRegistry}

list=(${cpNodes//,/ })
for item in "${list[@]}"; do
    item=$(wPort ${item} "8090")
    if [[ "${ocneVer}" == "1.5."* ]] || [[ "${ocneVer}" == "1.5" ]]; then
        addArg kubernetes master-nodes ${item}
    else
        addArg kubernetes control-plane-nodes ${item}
    fi
done

list=(${wkNodes//,/ })
for item in "${list[@]}"; do
    item=$(wPort ${item} "8090")
    addArg kubernetes worker-nodes ${item}
done

cat ${inpFile}
