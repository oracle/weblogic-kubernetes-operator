#!/usr/bin/env bash
# Copyright (c) 2018, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description
# This script is to validate if resources are ready for creating a new WebLogic domain.
# It will validate the following resources:
#   * Azure resource group: check if it exists
#   * Azure Kubernetes Service instance: check if it is created
#   * Azure storage account: check if it is created
#   * Azure file share: check if it's created
#   * Kubernetes secret for container registry account: check if it's created
#   * Kubernetes secret for WebLogic domain: check if it's created
#   * Persistent Volume Claim: check if it's mounted and verify the status and storage class

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"

usage() {
  echo "Arguments"
  echo "  --aks-name          [Required] ：Azure Kubernetes Service instance name. "
  echo "  --file-share        [Required] ：File share name."
  echo "  --resource-group -g [Required] ：Resource group name."
  echo "  --storage-account   [Required] ：Storage account name."
  
  echo "  --domain-uid -d     [Required] ：Domain UID."
  echo "  --pvc-name          [Required] : Persistent Volume Claim name."
  echo "  --secret-docker     [Required] : Name of the Kubernetes secret that stores docker account."
  echo "  --help -h                      ：Help"
  exit $1
}

while test $# -gt 0; do
  case "$1" in
    --aks-name*)
        shift
        if test $# -gt 0; then
            export aksName=$1
        else
            echo "Azure Kubernetes Service instance name is required."
            exit 1
        fi
        shift
    ;;
    -g*|--resource-group*) 
        shift
        if test $# -gt 0; then
            export resourceGroup=$1
        else
            echo "Resource group is required."
            exit 1
        fi
        shift
    ;;
    --storage-account*)
        shift
        if test $# -gt 0; then
            export storageAccount=$1
        else
            echo "Storage account is required."
            exit 1
        fi
        shift
    ;;
    --file-share*)
        shift
        if test $# -gt 0; then
            export fileShare=$1
        else
            echo "Storage accounFile share name is required."
            exit 1
        fi
        shift
    ;;
    -d*|--domain-uid*)
        shift
        if test $# -gt 0; then
            export domainUID=$1
        else
            echo "Domain UID is required."
            exit 1
        fi
        shift
    ;;
    --pvc-name*)
        shift
        if test $# -gt 0; then
            export pvcName=$1
        else
            echo "Persistent Volume Claim name is required."
            exit 1
        fi
        shift
    ;;
    --secret-docker*)
        shift
        if test $# -gt 0; then
            export secretDocker=$1
        else
            echo "Secret name for Container Registry Account is required."
            exit 1
        fi
        shift
    ;;
    -h|--help) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

missingRequiredOption="false"
if [ -z ${aksName} ]; then
  echo "${script}: --aks-name must be specified."
  missingRequiredOption="true"
fi
if [ -z ${domainUID} ]; then
  echo "${script}: --domain-uid or -d must be specified."
  missingRequiredOption="true"
fi
if [ -z ${fileShare} ]; then
  echo "${script}: --file-share must be specified."
  missingRequiredOption="true"
fi
if [ -z ${pvcName} ]; then
  echo "${script}: --pvc-name must be specified."
  missingRequiredOption="true"
fi
if [ -z ${resourceGroup} ]; then
  echo "${script}: --resource-group or -g must be specified."
  missingRequiredOption="true"
fi
if [ -z ${secretDocker} ]; then
  echo "${script}: --secret-docker must be specified."
  missingRequiredOption="true"
fi
if [ -z ${storageAccount} ]; then
  echo "${script}: --storage-account must be specified."
  missingRequiredOption="true"
fi

if [ "${missingRequiredOption}" == "true" ]; then
  usage 1
fi

#
# Function to exit and print an error message
# $1 - text of message
fail() {
  echo [ERROR] $*
  exit 1
}

validateAzLogin() {
    az account show
    if [ $? -ne 0 ]; then
        fail "Please run az login to setup account."
    fi
}

validateResourceGroup() {
    ret=$(az group exists --name ${resourceGroup})
    if [ $ret == false ];then 
      fail "${resourceGroup} does not exist."
    fi
}

validateStorageAccount() {
    ret=$(az storage account check-name --name ${storageAccount})
    echo $ret
    nameAvailable=$(echo "$ret" | grep "AlreadyExists")
    if [ -z "$nameAvailable" ];then
      fail "Storage account ${storageAccount} is unavailable."
    fi
}

validateAKSName() {
    ret=$(az aks list -g ${resourceGroup} | grep "${aksName}")
    if [ -z "$ret" ];then 
      fail "AKS instance with name ${aksName} does not exist."
    fi
}

validateFileShare() {
    export azureStorageConnectionString=$(az storage account show-connection-string \
    -n $storageAccount -g $resourceGroup -o tsv)

    echo Check if file share exists
    ret=$( az storage share-rm exists --name ${fileShare} --storage-account ${storageAccount} | grep "exists" | grep false)
    if [ -n "$ret" ];then 
      fail "File share ${fileShare} is unavailable."
    fi
}

connectAKS() {
    az aks get-credentials --resource-group $resourceGroup --name $aksName
    if [ $? -ne 0 ]; then
        fail "Connect to ${aksName} failed."
    fi
}

validateDockerSecret() {
    kubectl get secret ${secretDocker}
    if [ $? -ne 0 ]; then
        fail "Secret:${secretDocker} for docker account is not created."
    fi
}

validateWebLogicDomainSecret() {
    ret=$(kubectl get secrets | grep "weblogic-credentials")
    if [ $? -ne 0 ]; then
        fail "Secret:weblogic-credentials is not created."
    fi

    export secretWebLogic=$(echo ${ret%% *})
}

validatePVC() {
    ret=$(kubectl get pvc)
    index=0
    for item in ${ret};
    do
        index=$((index + 1))
        if [ $index -eq 9 ]; then
            if [[ "$item" != "$pvcName"  ]];then
                fail "Persistent Volume Claim name $item does not match value $pvcName."
            fi
        fi
        
        if [[ $index -eq 10  && "$item" != "Bound" ]]; then
            fail "Persistent Volume Claim status is not Bound."
        fi
    done
} 

validateOperator() {
    ret=$(kubectl get pods | grep "weblogic-operator" | grep "Running")
    if [ -z "${ret}" ]; then
        fail "Please make sure WebLogic operator is running."
    fi
}

validateDomain() {
    ret=$(kubectl get domain | grep "${domainUID}")
    if [ -n "$ret" ]; then
        fail "${domainUID} is created! Please create a new domain or follow the page to delete it https://oracle.github.io/weblogic-kubernetes-operator/samples/domains/domain-home-on-pv/#delete-the-generated-domain-home."
    fi
}

pass() {
    echo ""
    echo "PASS"
    echo "You can create your domain with the following resources ready:"
    echo "  Azure resource group: ${resourceGroup}"
    echo "  Azure Kubernetes Service instance: ${aksName}"
    echo "  Azure storage account: ${storageAccount}"
    echo "  Azure file share: ${fileShare}"
    echo "  Kubernetes secret for Container Registry Account: ${secretDocker}"
    echo "  Kubernetes secret for WebLogic domain: ${secretWebLogic}"
    echo "  Persistent Volume Claim: ${pvcName}"
}

validateAzLogin

validateResourceGroup

validateAKSName

validateStorageAccount

validateFileShare

connectAKS

validateDockerSecret

validateWebLogicDomainSecret

validatePVC

validateOperator

validateDomain

pass





