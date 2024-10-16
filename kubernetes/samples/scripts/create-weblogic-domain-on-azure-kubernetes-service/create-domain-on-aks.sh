#!/usr/bin/env bash
# Copyright (c) 2018, 2023, Oracle and/or its affiliates.
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
#  create-domain-on-aks-inputs.sh. If you also want to customize
#  WebLogic Server domain configuration, please edit
#  kubernetes/samples/scripts/create-weblogic-domain/domain-home-on-pv/create-domain-on-aks-inputs.sh.  Or you can create a copy of this file and edit it and refer to the copy using "-d <your-create-domain-on-aks-inputs.sh>".
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

#Kubernetes command line interface.
#Default is 'kubectl' if KUBERNETES_CLI env variable is not set.
kubernetesCli=${KUBERNETES_CLI:-kubectl}
azureResourceUID=${TIMESTAMP}

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

# Define display end-------------
BLUE="\033[34m"
RED="\033[31m"
RESET="\033[0m"
YELLOW="\033[33m"
GREEN="\033[32m"

# Function: Print colored message
print_message() {
  local contenxt="$1"
  local color="$2"

  echo -e "${color} ${contenxt}${RESET}"
}

print_blue() {
  local contenxt="$1"
  echo -e "${BLUE} ${contenxt}${RESET}"
}

print_red() {
  local contenxt="$1"
  echo -e "${RED} ${contenxt}${RESET}"
}

print_yellow() {
  local contenxt="$1"
  echo -e "${YELLOW} ${contenxt}${RESET}"
}

print_green() {
  local contenxt="$1"
  echo -e "${GREEN} ${contenxt}${RESET}"
}

steps=0
total_steps=12
print_step() {
  ((steps++))
  print_blue "Progress $steps/$total_steps.......... $1"
}
#
# Function to validate the host environment meets the prerequisites.
# $1 - text of message
envValidate() {
  print_step "Checking host environment"
  # Check if the user is logged in to Azure CLI
  if az account show >/dev/null 2>&1; then
    print_blue "Logged in to Azure CLI"
  else
    print_red "[ERROR]Not logged in to Azure CLI. Please log in."
    exit 1
  fi

  # Check if Java JDK is installed
  java_version=$(java -version 2>&1)

  # "Checking if Java is installed..."
  if type -p java; then
    print_blue "Java JDK is installed. Version:"
    java -version
  else
    print_red "[ERROR]Java JDK is not installed. Please install Java JDK."
    exit 1
  fi

  # Check if Docker is installed
  if command -v docker &>/dev/null; then
    echo "Docker is installed."
  else
    print_red "[ERROR]Docker is not installed. Please install Docker."
    exit 1
  fi

  # Check if Helm is installed
  if command -v helm &>/dev/null; then
    print_blue "Helm is installed."
  else
    print_red "[ERROR]Helm is not installed. Please install Helm."
    exit 1
  fi

  # Check if kubectl is installed
  if command -v ${kubernetesCli} &>/dev/null; then
    print_blue "${kubernetesCli} is installed."
  else
    print_red "[ERROR]${kubernetesCli} is not installed. Please install ${kubernetesCli}."
    exit 1
  fi

  echo "Checking host environment passed."
}

#
# Function to prompt the user for input
# $1 - parameter name
# $2 - parameter type
# $3 - parameter length
# $4 - default value
inputParameter() {
  local paramName="$1"
  local parmType="$2"
  local length="$3"
  local defaultValue="$4"

  print_yellow "Please provide a value for ${paramName}."
  read -p "Enter ${parmType}: " input_string

  if [ -z "${input_string}" ]; then
    input_string=${defaultValue}
  fi

  if [[ "$input_string" =~ ^[A-Za-z0-9]+$ ]] && [[ ${#input_string} -le ${length} ]]; then
    export inputValue=$input_string
  else
    echo "Invalid input. Please enter a valid value for ${paramName}."
    exit 1
  fi
}

#
# Function to prompt the user for password input
# $1 - parameter name
inputPassword() {
  local paramName="$1"

  print_yellow "Please provide a value for ${paramName}."
  read -sp "Please enter password: " password
  echo
  read -sp "Please confirm your password: " password_confirm
  echo

  # Check if the passwords match
  if [ "$password" == "$password_confirm" ]; then
    echo "Password is set successfully."
    export inputValue=$password
  else
    echo "Passwords do not match. Please try again."
    exit 1
  fi
}

#
# Function to prompt the user for email input
# $1 - parameter name
inputEmail() {
  local paramName="$1"

  print_yellow "Please provide a value for ${paramName}."
  read -p "Enter Email: " input_string

  if [ -n "${input_string}" ]; then
    export inputValue=$input_string
  else
    echo "Invalid input. Please enter a valid value for ${paramName}."
    exit 1
  fi
}

#
# Function to print the parameters
#
print_parameters() {
  print_green "image_build_branch_name=${image_build_branch_name}"
  print_green "image_build_base_dir=${image_build_base_dir}"
  print_green "dockerEmail=${dockerEmail}"
  print_green "weblogicUserName=${weblogicUserName}"

  print_green "namePrefix=${namePrefix}"
  print_green "azureLocation=${azureLocation}"
  print_green "azureResourceGroupName=${azureResourceGroupName}"
  print_green "aksClusterName=${aksClusterName}"
  print_green "storageAccountName=${storageAccountName}"
  print_green "acrName=${acrName}"
  print_green "azureKubernetesNodepoolName=${azureKubernetesNodepoolName}"
  print_green "azureStorageShareName=${azureStorageShareName}"
  print_green "oracleSsoK8sSecretName=${oracleSsoK8sSecretName}"
  print_green "domainUID=${domainUID}"
  print_green "sampleScriptsDir=${sampleScriptsDir}"
}

parametersValidate() {
  print_step "validating parameters"

  local stringFormatLength5="string contains both letters and numbers, between 1 and 5 characters"
  local stringFormatLength12="string contains both letters and numbers, between 1 and 12 characters"
  local stringFormatLength20="string contains both letters and numbers, between 1 and 20 characters"

  if [ -z "${namePrefix}" ]; then
    inputParameter "a prefix to name resources" "${stringFormatLength5} [default=wls]" 5 "wls"
    export namePrefix=$inputValue
  fi

  if [ -z "${azureLocation}" ]; then
    inputParameter "Azure location" "Azure location [default=eastus]" 12 "eastus"
    export azureLocation=$inputValue
  fi

  if [ -z "${acrName}" ]; then
    inputParameter "Azure Container Registry name" "${stringFormatLength20} [default=${namePrefix}acr${azureResourceUID}]" 20 "${namePrefix}acr${azureResourceUID}"
    export acrName=$inputValue
  fi

  if [ -z "${oracleSsoK8sSecretName}" ]; then
    inputParameter "the Kubernetes secret name associated with the Oracle SSO account." "${stringFormatLength12} [default=${namePrefix}regcred]" 12 "${namePrefix}regcred"
    export oracleSsoK8sSecretName=$inputValue
  fi

  if [ -z "${azureResourceGroupName}" ]; then
    inputParameter "resource group name" "${stringFormatLength20} [default=${namePrefix}rg${azureResourceUID}]" 20 "${namePrefix}rg${azureResourceUID}"
    export azureResourceGroupName=$inputValue
  fi

  if [ -z "${aksClusterName}" ]; then
    inputParameter "Azure Kubernetes Service name" "${stringFormatLength20} [default=${namePrefix}aks${azureResourceUID}]" 20 "${namePrefix}aks${azureResourceUID}"
    export aksClusterName=$inputValue
  fi

  if [ -z "${storageAccountName}" ]; then
    inputParameter "Azure Storage Account name" "${stringFormatLength20} [default=${namePrefix}stg${azureResourceUID}]" 20 "${namePrefix}stg${azureResourceUID}"
    export storageAccountName=$inputValue
  fi

  if [ -z "${azureStorageShareName}" ]; then
    inputParameter "Azure Storage File Share name" "${stringFormatLength20} [default=${namePrefix}${azureResourceUID}]" 20 "${namePrefix}${azureResourceUID}"
    export azureStorageShareName=$inputValue
  fi

  if [ -z "${domainUID}" ]; then
    inputParameter "WebLogic Domain UID" "${stringFormatLength12} [default=domain1]" 12 "domain1"
    export domainUID=$inputValue
  fi

  if [ -z "${dockerEmail}" ]; then
    inputEmail "Oracle Single Sign-On (SSO) account email"
    export dockerEmail=$inputValue
  fi

  if [ -z "${dockerPassword}" ]; then
    inputPassword "Oracle Single Sign-On (SSO) account password"
    export dockerPassword=$inputValue
  fi

  if [ -z "${weblogicUserName}" ]; then
    inputParameter "Name of weblogic user account" "${stringFormatLength12} [default=weblogic]" 12 "weblogic"
    export weblogicUserName=$inputValue
  fi

  if [ -z "${weblogicAccountPassword}" ]; then
    inputPassword "Password of weblogic user account"
    export weblogicAccountPassword=$inputValue
  fi  

  ssoAccountValidate

  print_parameters
}

ssoAccountValidate() {
  print_step "Validate Oracle SSO Account. Make sure docker is running."
  # Attempt to login to Docker
  sudo chmod 666 /var/run/docker.sock
  docker login container-registry.oracle.com -u "$dockerEmail" -p "$dockerPassword" >/dev/null 2>&1

  # Check the login result
  if [ $? -eq 0 ]; then
    echo "Oracle Single Sign-On (SSO) account Username and password are correct"
    # Logout from Docker
    docker logout >/dev/null 2>&1
  else
    print_red "[ERROR]Invalid Oracle Single Sign-On (SSO) account username or password."
    exit 1
  fi
}

#
# Function to setup the environment to run the create Azure resource and domain job
#
initialize() {

  print_step "initializing"

  az provider register -n Microsoft.ContainerRegistry

  source ./create-domain-on-aks-inputs.sh
  source ~/.bashrc

  # Generate Azure resource name

  export image_build_branch_name="v4.2.8"
  export image_build_base_dir="/tmp/tmp${azureResourceUID}"

  export dockerEmail=${ORACLE_SSO_EMAIL}
  export dockerPassword=${ORACLE_SSO_PASSWORD}
  export weblogicUserName=${WEBLOGIC_USERNAME}
  export weblogicAccountPassword=${WEBLOGIC_PASSWORD}

  export namePrefix=${NAME_PREFIX}
  export azureLocation=${AKS_PERS_LOCATION}
  export acrName=${ACR_NAME}
  export oracleSsoK8sSecretName="${SECRET_NAME_DOCKER}"

  export azureResourceGroupName="${AKS_PERS_RESOURCE_GROUP}"
  export aksClusterName="${AKS_CLUSTER_NAME}"
  export storageAccountName="${AKS_PERS_STORAGE_ACCOUNT_NAME}"

  export azureKubernetesNodepoolName="${azureKubernetesNodepoolNamePrefix}${namePrefix}"
  export azureStorageShareName="${AKS_PERS_SHARE_NAME}"
  export domainUID="${domainUID}"
  export sampleScriptsDir=${BASE_DIR}/sample-scripts

  print_parameters  
}

createResourceGroup() {
  print_step "createing resourcegroup"

  az extension add --name resource-graph

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

  print_step "creating AKS"

  # Create aks cluster
  echo Check if ${aksClusterName} exists
  ret=$(az aks list -g ${azureResourceGroupName} | grep "${aksClusterName}")
  if [ -n "$ret" ]; then
    fail "AKS instance with name ${aksClusterName} exists."
  fi

  echo Creating Azure Kubernetes Service ${aksClusterName}

  # Create AKS command
  create_command="az aks create --resource-group $azureResourceGroupName \
                    --name $aksClusterName \
                    --vm-set-type VirtualMachineScaleSets \
                    --node-count ${azureKubernetesNodeCount} \
                    --generate-ssh-keys \
                    --nodepool-name ${azureKubernetesNodepoolName} \
                    --node-vm-size ${azureKubernetesNodeVMSize} \
                    --location $azureLocation \
                    --enable-managed-identity"

  # Maximum number of retries
  max_retries=3
  retry_count=0

  while true; do
    # Execute create AKS command
    $create_command

    # Check exit status
    if [ $? -eq 0 ]; then
      echo "AKS creation successful"
      break
    else
      retry_count=$((retry_count + 1))
      if [ $retry_count -le $max_retries ]; then
        echo "AKS creation failed. Retrying attempt $retry_count..."
        # Delete previously created AKS
        az aks delete --resource-group $azureResourceGroupName --name $aksClusterName --yes --no-wait
      else
        echo "Maximum retry limit reached. Unable to create AKS"
        exit 1
      fi
    fi
  done

  # Connect to AKS cluster
  echo Connencting to Azure Kubernetes Service.
  az aks get-credentials --resource-group $azureResourceGroupName --name $aksClusterName
}

createFileShare() {

  print_step "createing fileshare"
  # Create a storage account
  echo Check if the storage account ${storageAccountName} exists.
  ret=$(az storage account check-name --name ${storageAccountName})
  nameAvailable=$(echo "$ret" | grep "nameAvailable" | grep "false")
  if [ -n "$nameAvailable" ]; then
    echo $ret
    fail "Storage account ${storageAccountName} is unavailable."
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

}

configureStorageAccountNetwork() {
  local aksObjectId=$(az aks show --name ${aksClusterName} --resource-group ${azureResourceGroupName} --query "identity.principalId" -o tsv)
  local storageAccountId=$(az storage account show --name ${storageAccountName} --resource-group ${azureResourceGroupName} --query "id" -o tsv)

  az role assignment create \
    --assignee-object-id "${aksObjectId}" \
    --assignee-principal-type "ServicePrincipal" \
    --role "Contributor" \
    --scope "${storageAccountId}"

  if [ $? != 0 ]; then
    fail "Failed to grant the AKS cluster with Contibutor role to access the storage account."
  fi

  # get the resource group name of the AKS managed resources
  local aksMCRGName=$(az aks show --name $aksClusterName --resource-group $azureResourceGroupName -o tsv --query "nodeResourceGroup")
  echo "aksMCRGName=${aksMCRGName}"

  # get network name of AKS cluster
  local aksNetworkName=$(az graph query -q "Resources \
    | where type =~ 'Microsoft.Network/virtualNetworks' \
    | where resourceGroup  =~ '${aksMCRGName}' \
    | project name = name" --query "data[0].name" -o tsv)

  echo "aksNetworkName="${aksNetworkName}

  # get subnet name of AKS agent pool
  local aksSubnetName=$(az network vnet subnet list --resource-group ${aksMCRGName} --vnet-name ${aksNetworkName} -o tsv --query "[*].name")
  echo ${aksSubnetName}

  local aksSubnetId=$(az network vnet subnet list --resource-group ${aksMCRGName} --vnet-name ${aksNetworkName} -o tsv --query "[*].id")
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
  print_step "installing weblogic kubernetes operator"
  echo "helm version ="$(helm version)
  helm repo add weblogic-operator https://oracle.github.io/weblogic-kubernetes-operator/charts --force-update
  helm install weblogic-operator weblogic-operator/weblogic-operator
}

createWebLogicDomain() {
  print_step "creating weblogic domain"

  # Enable the operator to monitor the namespace
  echo "Enable the operator to monitor the namespace"
  ${kubernetesCli} label namespace default weblogic-operator=enabled

  # Create WebLogic Server Domain
  echo Creating WebLogic Server domain ${domainUID}

  # create credentials
  cd ${sampleScriptsDir}/create-weblogic-domain-credentials
  ./create-weblogic-credentials.sh -u ${weblogicUserName} -p ${weblogicAccountPassword} -d ${domainUID}

  cd ${sampleScriptsDir}/create-kubernetes-secrets
  ./create-docker-credentials-secret.sh -s ${oracleSsoK8sSecretName} -e ${dockerEmail} -p ${dockerPassword} -u ${dockerEmail}

  # generate yaml
  generateYamls

  # Mount the file share as a volume
  echo "Mounting file share as a volume..."
  ${kubernetesCli} apply -f ./azure-csi-nfs.yaml
  ${kubernetesCli} apply -f ./pvc.yaml

  ${kubernetesCli} apply -f domain-resource.yaml
  ${kubernetesCli} apply -f admin-lb.yaml
  ${kubernetesCli} apply -f cluster-lb.yaml

}

generateYamls() {

  echo "generating yamls..."
  cat >azure-csi-nfs.yaml <<EOF
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: azurefile-csi-nfs
provisioner: file.csi.azure.com
parameters:
  protocol: nfs
  resourceGroup: ${azureResourceGroupName}
  storageAccount: ${storageAccountName}
  shareName: ${azureStorageShareName}
reclaimPolicy: Delete
volumeBindingMode: Immediate
allowVolumeExpansion: true

EOF

  cat >pvc.yaml <<EOF
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: wls-azurefile-${azureResourceUID}
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: azurefile-csi-nfs
  resources:
    requests:
      storage: 5Gi

EOF

  cat >domain-resource.yaml <<EOF
# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This is an example of how to define a Domain resource.
#
apiVersion: "weblogic.oracle/v9"
kind: Domain
metadata:
  name: ${domainUID}
  namespace: default
  labels:
    weblogic.domainUID: ${domainUID}

spec:
  # Set to 'PersistentVolume' to indicate 'Domain on PV'.
  domainHomeSourceType: PersistentVolume

  # The WebLogic Domain Home, this must be a location within
  # the persistent volume for 'Domain on PV' domains.
  domainHome: /shared/domains/${domainUID}

  # The WebLogic Server image that the Operator uses to start the domain
  # **NOTE**:
  # This sample uses General Availability (GA) images. GA images are suitable for demonstration and
  # development purposes only where the environments are not available from the public Internet;
  # they are not acceptable for production use. In production, you should always use CPU (patched)
  # images from OCR or create your images using the WebLogic Image Tool.
  # Please refer to the "OCR" and "WebLogic Images" pages in the WebLogic Kubernetes Operator
  # documentation for details.
  image: "container-registry.oracle.com/middleware/weblogic:12.2.1.4"

  # Defaults to "Always" if image tag (version) is ':latest'
  imagePullPolicy: IfNotPresent

  # Identify which Secret contains the credentials for pulling an image
  imagePullSecrets:
    - name: ${namePrefix}regcred

  # Identify which Secret contains the WebLogic Admin credentials,
  # the secret must contain 'username' and 'password' fields.
  webLogicCredentialsSecret:
    name: ${domainUID}-weblogic-credentials

  # Whether to include the WebLogic Server stdout in the pod's stdout, default is true
  includeServerOutInPodLog: true

  # Whether to enable overriding your log file location, defaults to 'True'. See also 'logHome'.
  #logHomeEnabled: false

  # The location for domain log, server logs, server out, introspector out, and Node Manager log files
  # see also 'logHomeEnabled', 'volumes', and 'volumeMounts'.
  #logHome: /shared/logs/sample-${domainUID}
  #
  # Set which WebLogic Servers the Operator will start
  # - "Never" will not start any server in the domain
  # - "AdminOnly" will start up only the administration server (no managed servers will be started)
  # - "IfNeeded" will start all non-clustered servers, including the administration server, and clustered servers up to their replica count.
  serverStartPolicy: IfNeeded

  configuration:
    # Settings for initializing the domain home on 'PersistentVolume'
    initializeDomainOnPV:

      # Settings for domain home on PV.
      domain:
        # Valid model domain types are 'WLS', and 'JRF', default is 'JRF'
        domainType: WLS

        # Domain creation image(s) containing WDT model, archives, and install.
        #   "image"                - Image location
        #   "imagePullPolicy"      - Pull policy, default "IfNotPresent"
        #   "sourceModelHome"      - Model file directory in image, default "/auxiliary/models".
        #   "sourceWDTInstallHome" - WDT install directory in image, default "/auxiliary/weblogic-deploy".
        domainCreationImages:
        - image: "${acrName}.azurecr.io/wdt-domain-image:WLS-v1"
          imagePullPolicy: IfNotPresent
          #sourceWDTInstallHome: /auxiliary/weblogic-deploy
          #sourceModelHome: /auxiliary/models

        # Optional configmap for additional models and variable files
        #domainCreationConfigMap: sample-${domainUID}-wdt-config-map

    # Secrets that are referenced by model yaml macros
    # (the model yaml in the optional configMap or in the image)
    #secrets:
    #- sample-${domainUID}-datasource-secret

  # Settings for all server pods in the domain including the introspector job pod
  serverPod:
    # Optional new or overridden environment variables for the domain's pods
    # - This sample uses CUSTOM_DOMAIN_NAME in its image model file
    #   to set the WebLogic domain name
    env:
    - name: CUSTOM_DOMAIN_NAME
      value: ${domainUID}
    - name: JAVA_OPTIONS
      value: "-Dweblogic.StdoutDebugEnabled=false"
    - name: USER_MEM_ARGS
      value: "-Djava.security.egd=file:/dev/./urandom -Xms256m -Xmx512m "
    resources:
      requests:
        cpu: "250m"
        memory: "768Mi"

    # Volumes and mounts for hosting the domain home on PV and domain's logs. See also 'logHome'.
    volumes:
    - name: weblogic-domain-storage-volume
      persistentVolumeClaim:
        claimName: wls-azurefile-${azureResourceUID}
    volumeMounts:
    - mountPath: /shared
      name: weblogic-domain-storage-volume

  # The desired behavior for starting the domain's administration server.
  # adminServer:
    # Setup a Kubernetes node port for the administration server default channel
    #adminService:
    #  channels:
    #  - channelName: default
    #    nodePort: 30701

  # The number of managed servers to start for unlisted clusters
  replicas: 3

  # The name of each Cluster resource
  clusters:
  - name: sample-${domainUID}-cluster-1

  # Change the restartVersion to force the introspector job to rerun
  # to force a roll of your domain's WebLogic Server pods.
  restartVersion: '1'

  # Changes to this field cause the operator to repeat its introspection of the
  #  WebLogic domain configuration.
  introspectVersion: '1'

---

apiVersion: "weblogic.oracle/v1"
kind: Cluster
metadata:
  name: sample-${domainUID}-cluster-1
  # Update this with the namespace your domain will run in:
  namespace: default
  labels:
    # Update this with the "domainUID" of your domain:
    weblogic.domainUID: ${domainUID}
spec:
  # This must match a cluster name that is  specified in the WebLogic configuration
  clusterName: cluster-1
  # The number of managed servers to start for this cluster
  replicas: 3


EOF

  cat >admin-lb.yaml <<EOF
apiVersion: v1
kind: Service
metadata:
  name: ${domainUID}-admin-server-external-lb
  namespace: default
spec:
  ports:
  - name: default
    port: 7001
    protocol: TCP
    targetPort: 7001
  selector:
    weblogic.domainUID: ${domainUID}
    weblogic.serverName: admin-server
  sessionAffinity: None
  type: LoadBalancer

EOF

  cat >cluster-lb.yaml <<EOF
apiVersion: v1
kind: Service
metadata:
  name: ${domainUID}-cluster-1-lb
  namespace: default
spec:
  ports:
  - name: default
    port: 8001
    protocol: TCP
    targetPort: 8001
  selector:
    weblogic.domainUID: ${domainUID}
    weblogic.clusterName: cluster-1
  sessionAffinity: None
  type: LoadBalancer

EOF

}

buildDomainOnPvImage() {
  print_step "build domain image"

  echo "build image start----------"

  az extension add --name resource-graph
  mkdir ${image_build_base_dir}

  ## Build Azure ACR
  az acr create --resource-group $azureResourceGroupName \
    --name ${acrName} \
    --sku Standard

  echo "enable admin ......"
  az acr update -n ${acrName} --resource-group $azureResourceGroupName --admin-enabled true

  export LOGIN_SERVER=$(az acr show -n $acrName --resource-group $azureResourceGroupName --query 'loginServer' -o tsv)
  export USER_NAME=$(az acr credential show -n $acrName --resource-group $azureResourceGroupName --query 'username' -o tsv)
  export PASSWORD=$(az acr credential show -n $acrName --resource-group $azureResourceGroupName --query 'passwords[0].value' -o tsv)

  sudo docker login $LOGIN_SERVER -u $USER_NAME -p $PASSWORD

  ## need az acr login in order to push
  az acr login --name $acrName

  ## Build image
  cd ${image_build_base_dir}

  mkdir -p ${image_build_base_dir}/sample
  cp -r ${sampleScriptsDir}/create-weblogic-domain/domain-on-pv/* ${image_build_base_dir}/sample

  mkdir -p ${image_build_base_dir}/sample/wdt-artifacts
  cp -r ${sampleScriptsDir}/create-weblogic-domain/wdt-artifacts/* ${image_build_base_dir}/sample/wdt-artifacts

  cd ${image_build_base_dir}/sample/wdt-artifacts

  curl -m 120 -fL https://github.com/oracle/weblogic-deploy-tooling/releases/latest/download/weblogic-deploy.zip \
    -o ${image_build_base_dir}/sample/wdt-artifacts/weblogic-deploy.zip

  curl -m 120 -fL https://github.com/oracle/weblogic-image-tool/releases/latest/download/imagetool.zip \
    -o ${image_build_base_dir}/sample/wdt-artifacts/imagetool.zip

  cd ${image_build_base_dir}/sample/wdt-artifacts
  unzip imagetool.zip

  ./imagetool/bin/imagetool.sh cache deleteEntry --key wdt_latest
  ./imagetool/bin/imagetool.sh cache addInstaller \
    --type wdt \
    --version latest \
    --path ${image_build_base_dir}/sample/wdt-artifacts/weblogic-deploy.zip

  rm -f ${image_build_base_dir}/sample/wdt-artifacts/wdt-model-files/WLS-v1/archive.zip
  cd ${image_build_base_dir}/sample/wdt-artifacts/archives/archive-v1
  zip -r ${image_build_base_dir}/sample/wdt-artifacts/wdt-model-files/WLS-v1/archive.zip wlsdeploy

  cd ${image_build_base_dir}/sample/wdt-artifacts/wdt-model-files/WLS-v1
  ${image_build_base_dir}/sample/wdt-artifacts/imagetool/bin/imagetool.sh createAuxImage \
    --tag ${acrName}.azurecr.io/wdt-domain-image:WLS-v1 \
    --wdtModel ./model.10.yaml \
    --wdtVariables ./model.10.properties \
    --wdtArchive ./archive.zip

  image_name="${acrName}.azurecr.io/wdt-domain-image"
  tag="WLS-v1"
  output=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep "^${image_name}:${tag}$")

  if [ -n "$output" ]; then
    echo "The image '${image_name}' exists locally."
  else
    echo "The image '${image_name}' does not exist locally."
    exit 1
  fi

  ## Push image
  docker push ${acrName}.azurecr.io/wdt-domain-image:WLS-v1

  # allow aks to access acr
  echo allow aks to access acr
  acr_id=$(az acr show -n $acrName --resource-group $azureResourceGroupName --query "id" -o tsv)
  az aks update --name $aksClusterName --resource-group $azureResourceGroupName --attach-acr $acr_id

  ## build image success
  echo "build image end----------"

}

waitForJobComplete() {

  print_step "waiting job to complete"

  waiting_time=0
  max_wait_time=1800
  interval=60

  echo "Waiting Job to be completed."
  echo "Waiting for $interval seconds..."
  sleep $interval

  while [ $waiting_time -lt $max_wait_time ]; do
    status=$(${kubernetesCli} get pod/${domainUID}-admin-server -o=jsonpath='{.status.phase}')
    ready=$(${kubernetesCli} get pod/${domainUID}-admin-server --no-headers | awk '{print $2}')
    if [ "$status" == "Running" ]; then
      if [ "$ready" == "1/1" ]; then
        echo "${domainUID}-admin-server is running. Exiting..."
        break
      else
        echo "${domainUID}-admin-server is running, but not ready. Waiting for $interval seconds..."
      fi
    fi

    echo "${domainUID}-admin-server is not running yet. Waiting for $interval seconds..."
    sleep $interval
    waiting_time=$((waiting_time + interval))
  done

}

printSummary() {

  print_step "print summary"

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
  echo "Connect your ${kubernetesCli} to this cluster with this command:"
  echo "  az aks get-credentials --resource-group ${azureResourceGroupName} --name ${aksClusterName}"
  echo ""

  adminLbIP=$(${kubernetesCli} get svc ${domainUID}-admin-server-external-lb --output jsonpath='{.status.loadBalancer.ingress[0].ip}')
  echo "Administration console access is available at http://${adminLbIP}:7001/console"

  echo ""
  clusterLbIP=$(${kubernetesCli} get svc ${domainUID}-cluster-1-lb --output jsonpath='{.status.loadBalancer.ingress[0].ip}')
  echo "Cluster external ip is ${clusterLbIP}, you can access http://${clusterLbIP}:8001/myapp_war/index.jsp"

  echo "Completed"
}

cd ${scriptDir}

#
# Do these steps to create Azure resources and a WebLogic Server domain.
#

# Validate the host environment meets the prerequisites.
envValidate

# Setup the environment for running this script and perform initial validation checks
initialize

# Validate the parameters
parametersValidate "$@"

# Create resource group
createResourceGroup

# Create Azure Kubernetes Service and connect to AKS cluster
createAndConnectToAKSCluster

# Create File Share
createFileShare

# Install WebLogic Operator to AKS Cluster
installWebLogicOperator

# Build domain image
buildDomainOnPvImage

# Create WebLogic Server Domain
createWebLogicDomain

# Wait for all the domain creation completed
waitForJobComplete

# Print summary
printSummary
