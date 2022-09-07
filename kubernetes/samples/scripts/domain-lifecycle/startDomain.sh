#!/bin/sh
# Copyright (c) 2020, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh

usage() {

  cat << EOF

  This script starts a deployed WebLogic domain by patching 'spec.serverStartPolicy'
  attribute of the domain resource to 'IfNeeded'. This change will cause the operator
  to initiate startup of domain's WebLogic server instance pods if the pods are not
  already running.
 
  Usage:
 
    $(basename $0) [-n mynamespace] [-d mydomainuid] [-m kubecli]
  
    -d <domain_uid>     : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>      : Domain namespace. Default is 'sample-domain1-ns'.

    -m <kubernetes_cli> : Kubernetes command line interface. Default is 'kubectl' if KUBERNETES_CLI env
                          variable is not set. Otherwise default is the value of KUBERNETES_CLI env variable.

    -v <verbose_mode>   : Enables verbose mode. Default is 'false'.

    -h                  : This help.
   
EOF
exit $1
}

kubernetesCli=${KUBERNETES_CLI:-kubectl}
domainUid="sample-domain1"
domainNamespace="sample-domain1-ns"
verboseMode=false

while getopts "vn:d:m:h" opt; do
  case $opt in
    n) domainNamespace="${OPTARG}"
    ;;
    d) domainUid="${OPTARG}"
    ;;
    m) kubernetesCli="${OPTARG}"
    ;;
    v) verboseMode=true;
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done


set -eu
set -o pipefail

initialize() {

  validateErrors=false

  validateKubernetesCliAvailable
  validateJqAvailable

  failIfValidationErrors
}

initialize

# Get the domain in json format
domainJson=$(${kubernetesCli} get domain ${domainUid} -n ${domainNamespace} -o json --ignore-not-found)

if [ -z "${domainJson}" ]; then
  printError "Domain resource for domain '${domainUid}' not found in namespace '${domainNamespace}'. Exiting."
  exit 1
fi

getDomainPolicy "${domainJson}" serverStartPolicy

if [ "${serverStartPolicy}" == 'IfNeeded' ]; then
  printInfo "No changes needed, exiting. The domain '${domainUid}' is already started or starting. The effective value of 'spec.serverStartPolicy' attribute on the domain resource is 'IfNeeded'."
  exit 0
fi

printInfo "Patching domain '${domainUid}' from serverStartPolicy='${serverStartPolicy}' to 'IfNeeded'."

createPatchJsonToUpdateDomainPolicy "IfNeeded" patchJson

executePatchCommand "${kubernetesCli}" "${domainUid}" "${domainNamespace}" "${patchJson}" "${verboseMode}"

printInfo "Successfully patched domain '${domainUid}' in namespace '${domainNamespace}' with 'IfNeeded' start policy!"
