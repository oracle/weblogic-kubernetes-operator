#!/usr/bin/env bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Description
#  This sample script creates a WebLogic Server domain home on the Azure Kubernetes Service (AKS).
#  It creates a new Azure resource group, with a new Azure Storage Account and Azure File Share to allow WebLogic
#  to persist its configuration and data separately from the Kubernetes pods that run WebLogic workloads.
#  Besides, it also generates the domain resource yaml files, which can be used to restart the Kubernetes
#  artifacts of the corresponding domain.
#
#  The Azure resource deployment is customized by editing
#  create-domain-on-aks-inputs.yaml. If you also want to customize
#  WebLogic Server domain configuration, please edit
#  kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain-inputs.yaml.  Or you can create a copy of this file and edit it and refer to the copy using "-d <your-domain-inputs.yaml>".
#
#  The following pre-requisites must be handled prior to running this script:
#    * Environment has set up, with git, azure cli, kubectl and helm installed.
#    * The user must have accepted the license terms for the WebLogic Server docker
#      images in Oracle Container Registry.
#      See https://oracle.github.io/weblogic-kubernetes-operator/quickstart/get-images/
#    * The Azure Service Principal must have been created, with permission to
#      create AKS.

# Initialize
script="${BASH_SOURCE[0]}"
scriptDir="$(cd "$(dirname "${script}")" && pwd)"

source ${scriptDir}/../common/utility.sh
source ${scriptDir}/../common/validate.sh

usage() {
  echo usage: ${script} -i file -o dir [-u uid] [-e] [-d] [-h]
  echo "  -i Parameter inputs file, must be specified."
  echo "  -o Output directory for the generated yaml files, must be specified."
  echo "  -u UID of resource, used to name file share, persistent valume, and persistent valume claim. "
  echo "  -e Also create the Azure Kubernetes Service and create WebLogic Server domain on it using the generated yaml files"
  echo "  -d Paramters inputs file for creating domain, you can use specifed configuration by changing values of kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain-inputs.yaml, otherwise, we will use that file by default."
  echo "  -h Help"
  exit $1
}

#
# Parse the command line options
#
executeIt=false
while getopts "ehi:o:u:d:" opt; do
  case $opt in
  i)
    valuesInputFile="${OPTARG}"
    ;;
  o)
    outputDir="${OPTARG}"
    ;;
  u)
    azureResourceUID="${OPTARG}"
    ;;
  e)
    executeIt=true
    ;;
  d)
    domainInputFile="${OPTARG}"
    ;;
  h)
    usage 0
    ;;
  *)
    usage 1
    ;;
  esac
done

if [ -z ${valuesInputFile} ]; then
  echo "${script}: -i must be specified."
  missingRequiredOption="true"
fi

if [ -z ${outputDir} ]; then
  echo "${script}: -o must be specified."
  missingRequiredOption="true"
fi

if [ "${missingRequiredOption}" == "true" ]; then
  usage 1
fi

if [ -z "${azureResourceUID}" ]; then
  azureResourceUID=$(date +%s)
fi

#
# Function to exit and print an error message
# $1 - text of message
fail() {
  echo [ERROR] $*
  exit 1
}

#
# Function to initialize and validate the output directory
# for the generated yaml files for this domain.
#
initOutputDir() {
  aksOutputDir="$outputDir/weblogic-on-aks"

  scOutput="${aksOutputDir}/azure-csi-nfs.yaml"
  pvcOutput="${aksOutputDir}/pvc.yaml"
  adminLbOutput="${aksOutputDir}/admin-lb.yaml"
  clusterLbOutput="${aksOutputDir}/cluster-lb.yaml"
  domain1Output="${aksOutputDir}/domain1.yaml"

  removeFileIfExists ${scOutput}
  removeFileIfExists ${pvcOutput}
  removeFileIfExists ${adminLbOutput}
  removeFileIfExists ${clusterLbOutput}
  removeFileIfExists ${domain1Output}
  removeFileIfExists ${aksOutputDir}/create-domain-on-aks-inputs.yaml
}

#
# Function to setup the environment to run the create Azure resource and domain job
#
initialize() {

  # Validate the required files exist
  validateErrors=false

  if [ -z "${valuesInputFile}" ]; then
    validationError "You must use the -i option to specify the name of the inputs parameter file (a modified copy of kubernetes/samples/scripts/create-weblogic-domain-on-aks/create-domain-on-aks-inputs.yaml)."
  else
    if [ ! -f ${valuesInputFile} ]; then
      validationError "Unable to locate the input parameters file ${valuesInputFile}"
    fi
  fi

  if [ -z "${outputDir}" ]; then
    validationError "You must use the -o option to specify the name of an existing directory to store the generated yaml files in."
  fi

  storageClassInput="${scriptDir}/azure-csi-storageaccount-template.yaml"
  if [ ! -f ${storageClassInput} ]; then
    validationError "The template file ${storageClassInput} for generating a NFS storage class was not found"
  fi

  domainPVCInput="${scriptDir}/azure-file-pvc-template.yaml"
  if [ ! -f ${domainPVCInput} ]; then
    validationError "The template file ${domainPVCInput} for generating a persistent volume claim was not found"
  fi

  wlsLbInput="${scriptDir}/loadbalancer-template.yaml"
  if [ ! -f ${wlsLbInput} ]; then
    validationError "The template file ${wlsLbInput} for generating load balancer for Administration Server was not found"
  fi

  failIfValidationErrors

  # Parse the common inputs file
  parseCommonInputs
  initOutputDir
  failIfValidationErrors

  if [ ${#namePrefix} -gt 7 ]; then
    fail "namePrefix is allowed lowercase letters and numbers, between 1 and 7 characters."
  fi

  # Generate Azure resource name
  export azureResourceGroupName="${namePrefix}resourcegroup${azureResourceUID}"
  export aksClusterName="${namePrefix}akscluster${azureResourceUID}"
  export storageAccountName="${namePrefix}storage${azureResourceUID}"

  export azureFileShareSecretName="${namePrefix}${azureFileShareSecretNameSuffix}"
  export azureKubernetesNodepoolName="${azureKubernetesNodepoolNamePrefix}${namePrefix}"
  export azureStorageShareName="${namePrefix}-${azureStorageShareNameSuffix}-${azureResourceUID}"
  export imagePullSecretName="${namePrefix}${imagePullSecretNameSuffix}"
  export persistentVolumeClaimName="${namePrefix}-${persistentVolumeClaimNameSuffix}-${azureResourceUID}"
  export persistentVolumeId="${namePrefix}-${persistentVolumeClaimNameSuffix}-${azureResourceUID}"
}

#
# Function to generate the yaml files for creating Azure resources and WebLogic Server domain
#
createYamlFiles() {

  # Create a directory for this domain's output files
  mkdir -p ${aksOutputDir}

  # Make sure the output directory has a copy of the inputs file.
  # The user can either pre-create the output directory, put the inputs
  # file there, and create the domain from it, or the user can put the
  # inputs file some place else and let this script create the output directory
  # (if needed) and copy the inputs file there.
  copyInputsFileToOutputDirectory ${valuesInputFile} "${aksOutputDir}/create-domain-on-aks-inputs.yaml"

  echo Generating ${scOutput}

  cp ${storageClassInput} ${scOutput}  
  sed -i -e "s:%STORAGE_CLASS_NAME%:${azureFileCsiNfsClassName}:g" ${scOutput}
  sed -i -e "s:%AZURE_FILE_SHARE_NAME%:${azureStorageShareName}:g" ${scOutput}
  sed -i -e "s:%STORAGE_ACCOUNT_RESOURCE_GROUP_NAME%:${azureResourceGroupName}:g" ${scOutput}
  sed -i -e "s:%STORAGE_ACCOUNT_NAME%:${storageAccountName}:g" ${scOutput}

  # Generate the yaml to create the persistent volume claim
  echo Generating ${pvcOutput}

  cp ${domainPVCInput} ${pvcOutput}
  sed -i -e "s:%PERSISTENT_VOLUME_CLAIM_NAME%:${persistentVolumeClaimName}:g" ${pvcOutput}
  sed -i -e "s:%STORAGE_CLASS_NAME%:${azureFileCsiNfsClassName}:g" ${pvcOutput}

  # Generate the yaml to create WebLogic Server domain.
  echo Generating ${domain1Output}

  if [ -z ${domainInputFile} ]; then
    domainInputFile="${dirCreateDomain}/create-domain-inputs.yaml"
  fi

  cp ${domainInputFile} ${domain1Output}
  sed -i -e "s;^image\:.*;image\: ${weblogicDockerImage};g" ${domain1Output}
  sed -i -e "s:#imagePullSecretName.*:imagePullSecretName\: ${imagePullSecretName}:g" ${domain1Output}
  sed -i -e "s:imagePullSecretName.*:imagePullSecretName\: ${imagePullSecretName}:g" ${domain1Output}
  sed -i -e "s:exposeAdminNodePort.*:exposeAdminNodePort\: true:g" ${domain1Output}
  sed -i -e "s:persistentVolumeClaimName.*:persistentVolumeClaimName\: ${persistentVolumeClaimName}:g" ${domain1Output}
  sed -i -e "s:serverPodMemoryRequest.*:serverPodMemoryRequest\: ${serverPodMemoryRequest}:g" ${domain1Output}
  sed -i -e "s:serverPodCpuRequest.*:serverPodCpuRequest\: ${serverPodCpuRequest}:g" ${domain1Output}
  sed -i -e "s:serverPodMemoryLimit.*:serverPodMemoryLimit\: ${serverPodMemoryLimit}:g" ${domain1Output}
  sed -i -e "s:serverPodCpuLimit.*:serverPodCpuLimit\: ${serverPodCpuLimit}:g" ${domain1Output}
  sed -i -e "s;^javaOptions.*;javaOptions\: \"${javaOptions}\";g" ${domain1Output}

  # Parse domain configuration yaml for usage in load balancer
  exportValuesFile=$(mktemp /tmp/export-values-XXXXXXXXX.sh)
  tmpFile=$(mktemp /tmp/javaoptions_tmp-XXXXXXXXX.dat)
  parseYaml ${domain1Output} ${exportValuesFile}
  if [ ! -f ${exportValuesFile} ]; then
    echo Unable to locate the parsed output of ${domain1Output}.
    fail 'The file ${exportValuesFile} could not be found.'
  fi

  # Define the environment variables that will be used to fill in template values
  echo Domain parameters being used
  cat ${exportValuesFile}
  echo
  # javaOptions may contain tokens that are not allowed in export command
  # we need to handle it differently.
  # we set the javaOptions variable that can be used later
  tmpStr=$(grep "javaOptions" ${exportValuesFile})
  javaOptions=${tmpStr//"javaOptions="/}

  # We exclude javaOptions from the exportValuesFile
  grep -v "javaOptions" ${exportValuesFile} >${tmpFile}
  source ${tmpFile}
  rm ${exportValuesFile} ${tmpFile}

  # Generate the yaml to create load balancer for Administration Server.
  echo Generating ${adminLbOutput}

  cp ${wlsLbInput} ${adminLbOutput}
  sed -i -e "s:%SELECTOR_SERVER_TYPE%:${selectorAdminServerName}:g" ${adminLbOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${adminLbOutput}
  sed -i -e "s:%SERVER_PORT%:${adminPort}:g" ${adminLbOutput}
  sed -i -e "s:%SERVER_NAME%:${adminServerName}:g" ${adminLbOutput}

  # Generate the yaml to create load balancer for WebLogic Server cluster.
  echo Generating ${clusterLbOutput}

  cp ${wlsLbInput} ${clusterLbOutput}
  sed -i -e "s:%SELECTOR_SERVER_TYPE%:${selectorClusterServerName}:g" ${clusterLbOutput}
  sed -i -e "s:%DOMAIN_UID%:${domainUID}:g" ${clusterLbOutput}
  sed -i -e "s:%SERVER_PORT%:${managedServerPort}:g" ${clusterLbOutput}
  sed -i -e "s:%SERVER_NAME%:${clusterName}:g" ${clusterLbOutput}

  # Remove any "...yaml-e" files left over from running sed
  rm -f ${aksOutputDir}/*.yaml-e
}

loginAzure() {
  # login with a service principal
  az login --service-principal --username $azureServicePrincipalAppId \
  --password $azureServicePrincipalClientSecret \
  --tenant $azureServicePrincipalTenantId
  echo Login Azure with Servie Principal successfully.

  if [ $? -ne 0 ]; then
    fail "Login to Azure failed!"
  fi
}

createResourceGroup() {
  # Create a resource group
  echo Check if ${azureResourceGroupName} exists
  ret=$(az group exists --name ${azureResourceGroupName})
  if [ $ret != false ]; then
    fail "${azureResourceGroupName} exists, please change value of namePrefix to generate a new resource group name."
  fi

  echo Creating Resource Group ${azureResourceGroupName}
  az group create --name $azureResourceGroupName --location $azureLocation
}

createAndConnectToAKSCluster() {
  # Create aks cluster
  echo Check if ${aksClusterName} exists
  ret=$(az aks list -g ${azureResourceGroupName} | grep "${aksClusterName}")
  if [ -n "$ret" ]; then
    fail "AKS instance with name ${aksClusterName} exists."
  fi

  echo Creating Azure Kubernetes Service ${aksClusterName}
  az aks create --resource-group $azureResourceGroupName \
  --name $aksClusterName \
  --vm-set-type VirtualMachineScaleSets \
  --node-count ${azureKubernetesNodeCount} \
  --generate-ssh-keys \
  --nodepool-name ${azureKubernetesNodepoolName} \
  --node-vm-size ${azureKubernetesNodeVMSize} \
  --location $azureLocation \
  --service-principal $azureServicePrincipalAppId \
  --client-secret $azureServicePrincipalClientSecret

  # Connect to AKS cluster
  echo Connencting to Azure Kubernetes Service.
  az aks get-credentials --resource-group $azureResourceGroupName --name $aksClusterName
}

createFileShare() {
  # Create a storage account
  echo Check if the storage account ${storageAccountName} exists.
  ret=$(az storage account check-name --name ${storageAccountName})
  nameAvailable=$(echo "$ret" | grep "nameAvailable" | grep "false")
  if [ -n "$nameAvailable" ]; then
    echo $ret
    fail "Storage account ${aksClusterName} is unavaliable."
  fi

  echo Creating Azure Storage Account ${storageAccountName}.
  az storage account create \
  -n $storageAccountName \
  -g $azureResourceGroupName \
  -l $azureLocation \
  --sku Premium_LRS \
  --kind FileStorage \
  --https-only false \
  --default-action Deny

  echo Creating Azure NFS file share.
  az storage share-rm create \
  --resource-group $azureResourceGroupName \
  --storage-account $storageAccountName \
  --name ${azureStorageShareName} \
  --enabled-protocol NFS \
  --root-squash NoRootSquash \
  --quota 100

  configureStorageAccountNetwork

  # Echo storage account name and key
  echo Storage account name: $storageAccountName
  echo NFS file share name: ${azureStorageShareName}

  # Mount the file share as a volume
  echo Mounting file share as a volume.
  kubectl apply -f ${scOutput}
  kubectl get storageclass ${azureFileCsiNfsClassName} -o yaml
  kubectl apply -f ${pvcOutput}
  kubectl get pvc ${persistentVolumeClaimName} -o yaml

  checkPvcState ${persistentVolumeClaimName} "Bound"
}

configureStorageAccountNetwork() {
  # get the resource group name of the AKS managed resources
  aksMCRGName=$(az aks show --name $aksClusterName --resource-group $azureResourceGroupName -o tsv --query "nodeResourceGroup")
  echo ${aksMCRGName}

  # get network name of AKS cluster
  aksNetworkName=$(az resource list --resource-group ${aksMCRGName} --resource-type Microsoft.Network/virtualNetworks -o tsv --query '[*].name')
  echo ${aksNetworkName}

  # get subnet name of AKS agent pool
  aksSubnetName=$(az network vnet subnet list --resource-group ${aksMCRGName} --vnet-name ${aksNetworkName} -o tsv --query "[*].name")
  echo ${aksSubnetName}

  aksSubnetId=$(az network vnet subnet list --resource-group ${aksMCRGName} --vnet-name ${aksNetworkName} -o tsv --query "[*].id")
  echo ${aksSubnetId}

  az network vnet subnet update \
    --resource-group $aksMCRGName \
    --name ${aksSubnetName} \
    --vnet-name ${aksNetworkName} \
    --service-endpoints Microsoft.Storage

  az storage account network-rule add \
    --resource-group $azureResourceGroupName \
    --account-name $storageAccountName \
    --subnet ${aksSubnetId}

  if [ $? != 0 ]; then
    fail "Fail to configure network for storage account ${storageAccountName}. Network name: ${aksNetworkName}. Subnet name: ${aksSubnetName}."
  fi
}

installWebLogicOperator() {
  echo $(helm version)
  helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts
  helm repo update
  helm install weblogic-operator weblogic-operator/weblogic-operator --version "3.3.7"
}

createWebLogicDomain() {
  # Create WebLogic Server Domain Credentials.
  echo Creating WebLogic Server Domain credentials, with user ${weblogicUserName}, domainUID ${domainUID}
  bash ${dirCreateDomainCredentials}/create-weblogic-credentials.sh -u ${weblogicUserName} \
  -p ${weblogicAccountPassword} -d ${domainUID}

  # Create Container Registry Credentials.
  bash $dirKubernetesSecrets/create-docker-credentials-secret.sh \
  -e ${docker-email} \
  -p ${dockerPassword} \
  -u ${dockerUserName} \
  -s ${imagePullSecretName} \
  -d container-registry.oracle.com

  # Create WebLogic Server Domain
  echo Creating WebLogic Server domain ${domainUID}
  bash ${dirCreateDomain}/create-domain.sh -i $domain1Output -o ${outputDir} -e -v

  kubectl apply -f ${adminLbOutput}
  kubectl apply -f ${clusterLbOutput}
}

waitForJobComplete() {
  attempts=0
  svcState="running"
  while [ ! "$svcState" == "completed" ] && [ ! $attempts -eq 30 ]; do
    svcState="completed"
    attempts=$((attempts + 1))
    echo Waiting for job completed...${attempts}
    sleep 120

    # If the job is completed, there should have the following services created,
    #    ${domainUID}-${adminServerName}, e.g. domain1-admin-server
    #    ${domainUID}-${adminServerName}-ext, e.g. domain1-admin-server-ext
    #    ${domainUID}-${adminServerName}-external-lb, e.g domain1-admin-server-external-lb
    adminServiceCount=$(kubectl get svc | grep -c "${domainUID}-${adminServerName}")
    if [ ${adminServiceCount} -lt 3 ]; then svcState="running"; fi

    # If the job is completed, there should have the following services created, .assuming initialManagedServerReplicas=2
    #    ${domainUID}-${managedServerNameBase}1, e.g. domain1-managed-server1
    #    ${domainUID}-${managedServerNameBase}2, e.g. domain1-managed-server2
    managedServiceCount=$(kubectl get svc | grep -c "${domainUID}-${managedServerNameBase}")
    if [ ${managedServiceCount} -lt ${initialManagedServerReplicas} ]; then svcState="running"; fi

    # If the job is completed, there should have no service in pending status.
    pendingCount=$(kubectl get svc | grep -c "pending")
    if [ ${pendingCount} -ne 0 ]; then svcState="running"; fi

    # If the job is completed, there should have the following pods running
    #    ${domainUID}-${adminServerName}, e.g. domain1-admin-server
    #    ${domainUID}-${managedServerNameBase}1, e.g. domain1-managed-server1
    #    to
    #    ${domainUID}-${managedServerNameBase}n, e.g. domain1-managed-servern, n = initialManagedServerReplicas
    runningPodCount=$(kubectl get pods | grep "${domainUID}" | grep -c "Running")
    if [[ $runningPodCount -le ${initialManagedServerReplicas} ]]; then svcState="running"; fi

    echo ==============================Current Status==========================================
    kubectl get svc
    echo ""
    kubectl get pods
    echo ======================================================================================
  done

  # If all the services are completed, print service details
  # Otherwise, ask the user to refer to document for troubleshooting
  if [ "$svcState" == "completed" ]; then
    kubectl get pods
    kubectl get svc
  else
    echo It takes a little long to create domain, please refer to http://oracle.github.io/weblogic-kubernetes-operator/samples/azure-kubernetes-service/#troubleshooting
  fi
}

printSummary() {
  if [ "${executeIt}" = true ]; then
    regionJsonExcerpt=$(az group list --query "[?name=='${azureResourceGroupName}']" | grep location)
    tokens=($(
      IFS='"'
      for word in $regionJsonExcerpt; do echo "$word"; done
    ))
    region=${tokens[2]}
    echo ""
    echo ""
    echo "The following Azure Resouces have been created: "
    echo "  Resource groups: ${azureResourceGroupName}, MC_${azureResourceGroupName}_${aksClusterName}_${region}"
    echo "  Kubernetes service cluster name: ${aksClusterName}"
    echo "  Storage account: ${storageAccountName}"
    echo ""
    echo "Domain ${domainName} was created and was started by the WebLogic Kubernetes Operator"
    echo ""
    echo "Connect your kubectl to this cluster with this command:"
    echo "  az aks get-credentials --resource-group ${azureResourceGroupName} --name ${aksClusterName}"
    echo ""

    if [ "${exposeAdminNodePort}" = true ]; then
      adminLbIP=$(kubectl get svc ${domainUID}-${adminServerName}-external-lb --output jsonpath='{.status.loadBalancer.ingress[0].ip}')
      echo "Administration console access is available at http://${adminLbIP}:${adminPort}/console"
    fi

    echo ""
    clusterLbIP=$(kubectl get svc ${domainUID}-${clusterName}-external-lb --output jsonpath='{.status.loadBalancer.ingress[0].ip}')
    echo "Cluster external ip is ${clusterLbIP}, after you deploy application to WebLogic Server cluster, you can access it at http://${clusterLbIP}:${managedServerPort}/<your-app-path>"
  fi
  echo ""
  echo "The following files were generated:"
  echo "  ${scOutput}"
  echo "  ${pvcOutput}"
  echo "  ${adminLbOutput}"
  echo "  ${clusterLbOutput}"
  echo "  ${domain1Output}"
  echo ""

  echo "Completed"
}

cd ${scriptDir}

cd ..
export dirSampleScripts=$(pwd)
export dirCreateDomain="${dirSampleScripts}/create-weblogic-domain/domain-home-on-pv"
export dirCreateDomainCredentials="${dirSampleScripts}/create-weblogic-domain-credentials"
export dirKubernetesSecrets="${dirSampleScripts}/create-kubernetes-secrets"
export selectorAdminServerName="serverName"
export selectorClusterServerName="clusterName"

cd ${scriptDir}

#
# Do these steps to create Azure resources and a WebLogic Server domain.
#

# Setup the environment for running this script and perform initial validation checks
initialize

# Generate the yaml files for creating the domain
createYamlFiles

# All done if the execute option is true
if [ "${executeIt}" = true ]; then

  # Login Azure with service principal
  loginAzure

  # Create resource group
  createResourceGroup

  # Create Azure Kubernetes Service and connect to AKS cluster
  createAndConnectToAKSCluster

  # Create File Share
  createFileShare

  # Install WebLogic Operator to AKS Cluster
  installWebLogicOperator

  # Create WebLogic Server Domain
  createWebLogicDomain

  # Wait for all the jobs completed
  waitForJobComplete
fi

# Print summary
printSummary
