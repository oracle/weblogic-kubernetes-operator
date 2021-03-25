# !/bin/sh
# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh
if [ "${debug}" == "true" ]; then set -x; fi;

function usage() {

  cat << EOF

  This script initiates introspection of a WebLogic domain by updating
  the value of 'spec.introspectVersion' attribute of the domain resource. 
 
  Usage:
 
    $(basename $0) [-n mynamespace] [-d mydomainuid] [-i introspectVersion] [-m kubecli]
  
    -d <domain_uid>        : Domain unique-id. Default is 'sample-domain1'.

    -n <namespace>         : Domain namespace. Default is 'sample-domain1-ns'.

    -i <introspectVersion> : Introspect version. If this parameter is not provided, 
                             then the script will generate the 'introspectVersion' by
                             incrementing the existing value. If the 'spec.introspectVersion' 
                             doesn't exist or its value is non-numeric, then the script
                             will set the 'spec.introspectVersion' value to '1'.

    -m <kubernetes_cli>    : Kubernetes command line interface. Default is 'kubectl'
                             if KUBERNETES_CLI env variable is not set. Otherwise 
                             the default is the value of KUBERNETES_CLI env variable.

    -v <verbose_mode>      : Enables verbose mode. Default is 'false'.

    -h                     : This help.
   
EOF
exit $1
}

kubernetesCli=${KUBERNETES_CLI:-kubectl}
clusterName=""
domainUid="sample-domain1"
domainNamespace="sample-domain1-ns"
verboseMode=false
patchJson=""
introspectVersion=""

while getopts "vc:n:m:d:i:h" opt; do
  case $opt in
    n) domainNamespace="${OPTARG}"
    ;;
    d) domainUid="${OPTARG}"
    ;;
    i) introspectVersion="${OPTARG}"
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

#
# Function to perform validations, read files and initialize workspace
#
function initialize {

  validateErrors=false

  validateKubernetesCliAvailable
  validateJqAvailable

  failIfValidationErrors
}

initialize

# Get the domain in json format
domainJson=$(${kubernetesCli} get domain ${domainUid} -n ${domainNamespace} -o json --ignore-not-found)
if [ -z "${domainJson}" ]; then
  printError "Unable to get domain resource for domain '${domainUid}' in namespace '${domainNamespace}'. Please make sure the 'domain_uid' and 'namespace' specified by the '-d' and '-n' arguments are correct. Exiting."
  exit 1
fi

# if the introspectVersion is not provided, generate the value of introspectVersion
if [ -z "${introspectVersion}" ]; then
  generateDomainIntrospectVersion "${domainJson}" introspectVersion
fi

printInfo "Patching introspectVersion for domain '${domainUid}' to '${introspectVersion}'."
createPatchJsonToUpdateDomainIntrospectVersion "${introspectVersion}" patchJson

executePatchCommand "${kubernetesCli}" "${domainUid}" "${domainNamespace}" "${patchJson}" "${verboseMode}"

printInfo "Successfully patched introspectVersion for domain '${domainUid}'!"
