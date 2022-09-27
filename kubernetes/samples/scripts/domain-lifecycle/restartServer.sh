# !/bin/sh
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh
if [ "${debug}" == "true" ]; then set -x; fi;
set -eu

function usage() {

  cat << EOF

  This script restarts a running WebLogic server in a domain by deleting the server pod.
 
  Usage:
 
    $(basename $0) -s myserver [-n mynamespace] [-d mydomainuid] [-m kubecli]
  
    -s <server_name>           : The WebLogic server name (not the pod name).
                                 This parameter is required.

    -d <domain_uid>            : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>             : Domain namespace. Default is 'sample-domain1-ns'.
    
    -m <kubernetes_cli>        : Kubernetes command line interface. Default is 'kubectl' 
                                 if KUBERNETES_CLI env variable is not set. Otherwise the
                                 default is the value of KUBERNETES_CLI env variable.

    -h                         : This help.
   
EOF
exit $1
}

kubernetesCli=${KUBERNETES_CLI:-kubectl}
serverName=""
clusterName=""
domainUid="sample-domain1"
domainNamespace="sample-domain1-ns"
podName=""
legalDNSPodName=""

while getopts "s:m:n:d:h" opt; do
  case $opt in
    s) serverName="${OPTARG}"
    ;;
    n) domainNamespace="${OPTARG}"
    ;;
    m) kubernetesCli="${OPTARG}"
    ;;
    d) domainUid="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done

#
# Function to perform validations, read files and initialize workspace
#
function initialize {

  validateErrors=false

  validateKubernetesCliAvailable
  validateJqAvailable

  # Validate that server name parameter is specified.
  if [ -z "${serverName}" ]; then
    validationError "Please specify the server name using '-s' parameter e.g. '-s managed-server1'."
  fi

  failIfValidationErrors
}

initialize

# Get the domain in json format
domainJson=$(${kubernetesCli} get domain.v8.weblogic.oracle ${domainUid} -n ${domainNamespace} -o json --ignore-not-found)
if [ -z "${domainJson}" ]; then
  printError "Unable to get domain resource for domain '${domainUid}' in namespace '${domainNamespace}'. Please make sure the 'domain_uid' and 'namespace' specified by the '-d' and '-n' arguments are correct. Exiting."
  exit 1
fi

# Validate that specified server is either part of a cluster or is an independent managed server
validateServerAndFindCluster "${domainUid}" "${domainNamespace}" "${serverName}" isValidServer clusterName isAdminServer
if [ "${isValidServer}" != 'true' ]; then
  printError "Server ${serverName} is not part of any cluster and it's not an independent managed server. Please make sure that server name specified is correct."
  exit 1
fi

podName=${domainUid}-${serverName}
toDNS1123Legal ${podName} legalDNSPodName
printInfo "Initiating restart of '${serverName}' by deleting server pod '${legalDNSPodName}'."
result=$(${kubernetesCli} -n ${domainNamespace} delete pod ${legalDNSPodName} --ignore-not-found)
if [ -z "${result}" ]; then
  printError "Server '${serverName}' is not running."
else
  printInfo "Server restart succeeded !"
fi
