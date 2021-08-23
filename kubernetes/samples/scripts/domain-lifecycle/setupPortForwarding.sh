# !/bin/sh
# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh
if [ "${debug}" == "true" ]; then set -x; fi;
set -eu

function usage() {

  cat << EOF

  This script sets up the 'kubectl port-forward' for a WebLogic domain to allow access to the
  WebLogic Administration Console. The 'kubectl port-forward' pattern is often more secure than
  exposing the console to the public Internet. The script creates a pod in the domain namespace
  using the image containing 'socat' binary and it forwards the request to the WebLogic
  Admistration Server pod. The script derives the address and the adminstration port of the
  WebLogic Admistration Server using the domain topology. The port can also be provided on the
  command line using '-r' option.

  Usage:

    $(basename $0) -n namespace -d domain_uid [-a local_ip_address] [-l localPort] [-i image_name] 
                   [-p image_pull_policy] [-w wait_timeout] [-p port_forward_server_name] [-m kubecli] 
                   [-t pod_running_timeout]

    -d <domain_uid>                : Domain unique-id. This parameter is required.

    -n <namespace>                 : Domain namespace. This parameter is required.

    -a <local_ip_address>          : Addresses to listen on (comma separated value for --address flag). 
                                     Only accepts IP addresses or localhost as a value. Default is 'localhost'.

    -l <local_port>                : A local port to listen on. If not specified, it listens on a random local port.

    -r <remote_port>               : The port of the WebLogic Administartion Server to forward the request to.
                                     If not specified, this script will derive the adminstration port from the
                                     WebLogic domain topology stored in the introspector config map.

    -i <image_name>                : Name of the image containing 'socat' binary to be used for the port-forward
                                     server pod. Default is 'alpine/socat'.

    -p <image_pull_policy>         : Image pull policy for the image to be used for port-forward server pod.
                                     Default is 'IfNotPresent'.

    -w <wait_timeout>              : Wait timeout value used when waiting for the port-forward server pod to be ready.

    -s <port_forward_server_name>  : Name of the port-forward server pod created by this script. 
                                     Default is 'port-forward-server'.
 
    -t <pod_running_timeout>       : Value for --pod-running-timeout flag of kubectl port-forward command. The length of
                                     time to wait until at least one pod is running. Default is 1m0s.

    -m <kubernetes_cli>            : Kubernetes command line interface. Default is 'kubectl' if KUBERNETES_CLI env
                                     variable is not set. Otherwise default is the value of KUBERNETES_CLI env variable.

    -h                             : This help.
   
EOF
exit $1
}

kubernetesCli=${KUBERNETES_CLI:-kubectl}
domainUid=""
domainNamespace=""
portForwardPodName=port-forward-server
localAddress=""
localPort=""
adminHost=""
remotePort=""
address=""
imagePullPolicy=IfNotPresent
imageName=alpine/socat
waitTimeout=30
podRunningTimeout=
derivedAdminPort=0


while getopts "d:l:n:m:a:p:t:w:r:h" opt; do
  case $opt in
    n) domainNamespace="${OPTARG}"
    ;;
    d) domainUid="${OPTARG}"
    ;;
    m) kubernetesCli="${OPTARG}"
    ;;
    a) localAddress="${OPTARG}";
    ;;
    l) localPort="${OPTARG}";
    ;;
    r) remotePort="${OPTARG}";
    ;;
    i) imageName="${OPTARG}";
    ;;
    p) imagePullPolicy="${OPTARG}";
    ;;
    s) portForwardPodName="${OPTARG}";
    ;;
    t) podRunningTimeout="${OPTARG}";
    ;;
    w) waitTimeout="${OPTARG}";
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

  # Validate the required files exist
  validateErrors=false

  validateKubernetesCliAvailable
  validateJqAvailable

  # Validate that domain name and namespace parameters are specified.
  if [ -z "${domainUid}" ]; then
    validationError "Please specify a domain name using '-d' e.g. '-d sample-domain1'."
  fi
  if [ -z "${domainNamespace}" ]; then
    validationError "Please specify domain namespace using '-n' e.g. '-n sample-domain1-ns'."
  fi

  failIfValidationErrors
}

initialize

# Get the domain in json format
domainJson=$(${kubernetesCli} get domain ${domainUid} -n ${domainNamespace} -o json --ignore-not-found)
if [ -z "${domainJson}" ]; then
  printError "Unable to get domain resource for domain '${domainUid}' in namespace '${domainNamespace}'. Please make sure the 'domain_uid' and 'namespace' specified by the '-d' and '-n' arguments are correct. Exiting."
  exit 1
fi

trap "portForwardCleanup \"${domainNamespace}\" \"$portForwardPodName\"" EXIT

getAdminServerHostAdminPort "${domainUid}" "${domainNamespace}" adminHost derivedAdminPort
if [ -z $remotePort ]; then
remotePort=$derivedAdminPort
fi

printInfo "Creating a pod with name '$portForwardPodName' in the namespace '$domainNamespace' using image '$imageName.'"
${kubernetesCli} run -n $domainNamespace --image-pull-policy $imagePullPolicy --restart=Never --image=$imageName $portForwardPodName -- -d -d tcp-listen:$remotePort,fork,reuseaddr tcp-connect:$adminHost:$remotePort

set +e
printInfo "Waiting for the pod '$portForwardPodName' in the namespace '$domainNamespace' to be ready for '$waitTimeout' seconds."
waitTimeout=$waitTimeout"s"
${kubernetesCli} -n $domainNamespace wait --for=condition=Ready pod/$portForwardPodName --timeout $waitTimeout
if [ $? != 0 ]; then
  printError "Timed out while waiting for the pod '$portForwardPodName' to be ready. Check the pod status by running 'kubectl get pod $portForwardPodName -n $domainNamespace' or 'kubectl describe pod $portForwardPodName -n $domainNamespace'. You can also increase the timeout value using '-w' parameter."
  printInfo "Pod '$portForwardPodName' will be deleted after 30 seconds."
  sleep 30
  exit $?
fi
set -e

if [ ! -z $localAddress ]; then
  address="--address $localAddress"
fi

if [ ! -z $podRunningTimeout ]; then
  podRunningTimeout="--pod-running-timeout $podRunningTimeout"
fi


if [ -z $localPort ]; then
  command="${kubernetesCli} -n $domainNamespace port-forward $address pod/$portForwardPodName :$remotePort $podRunningTimeout"
else
  command="${kubernetesCli} -n $domainNamespace port-forward $address pod/$portForwardPodName $localPort:$remotePort $podRunningTimeout"
fi
printInfo "Executing command --> '$command'"
$command
