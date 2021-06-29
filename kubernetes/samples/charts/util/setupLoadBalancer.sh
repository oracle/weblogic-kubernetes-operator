#!/bin/bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This script create or delete an Ingress controller. 
#  The script supports ingress controllers: Traefik, Voyager, and Nginx.

set -eu
set -o pipefail

UTILDIR="$(dirname "$(readlink -f "$0")")"

#Kubernetes command line interface. 
#Default is 'kubectl' if KUBERNETES_CLI env variable is not set.  
kubernetesCli=${KUBERNETES_CLI:-kubectl}

# https://hub.helm.sh/charts/appscode/voyager
# https://artifacthub.io/packages/helm/appscode/voyager
# https://github.com/voyagermesh/voyager#supported-versions
DefaultVoyagerVersion=12.0.0

# https://github.com/containous/traefik/releases
DefaultTraefikVersion=2.2.1

# https://github.com/kubernetes/ingress-nginx/releases
DefaultNginxVersion=2.16.0

action=""
ingressType=""
namespace=""
release=""
repository=""
chart=""

# timestamp
#   purpose:  echo timestamp in the form yyyy-mm-ddThh:mm:ss.nnnnnnZ
#   example:  2018-10-01T14:00:00.000001Z
function timestamp() {
  local timestamp="`date --utc '+%Y-%m-%dT%H:%M:%S.%NZ' 2>&1`"
  if [ ! "${timestamp/illegal/xyz}" = "${timestamp}" ]; then
    # old shell versions don't support %N or --utc
    timestamp="`date -u '+%Y-%m-%dT%H:%M:%S.000000Z' 2>&1`"
  fi
  echo "${timestamp}"
}

# Function to print an error message
function printError {
  echo [`timestamp`][ERROR] "$*"
}

# Function to print an error message
function printInfo {
  echo [`timestamp`][INFO] "$*"
}

function usage() {
  cat << EOF
  Usage:
    $(basename $0) -c[d]  -t ingress-type  [-n namespace] [-v version]
    -c                   : create ingress controller [required]
    -d                   : delete ingress controller [required]
    -t <ingress type>    : ingress type traefik, voyager or nginx [required]
    -v <ingress version> : ingress release version
    -n <namespace>       : ingress namespace
    -m <kubernetes_cli>  : Kubernetes command line interface. Default is 'kubectl' if KUBERNETES_CLI env variable is not set. Otherwise default is the value of KUBERNETES_CLI env variable.
    -h                   : print help
EOF
exit $1
}

action_chosen=false

while getopts "cdt:n:r:v:h" opt; do
  case $opt in
    c) action="create"
       if [ $action_chosen = "true" ]; then
        printError " Both -c (create) and -d (delete) option can not be specified for ingress controller."
        usage 1
       fi 
       action_chosen=true
    ;;
    d) action="delete"
       if [ $action_chosen = "true" ]; then
        printError " Both -c (create) and -d (delete) option can not be specified for ingress controller."
        usage 1
       fi 
       action_chosen=true
    ;;
    n) namespace="${OPTARG}"
    ;;
    t) ingressType="${OPTARG}"
    ;;
    v) release="${OPTARG}"
    ;;
    m) kubernetesCli="${OPTARG}"
    ;;
    h) usage 0
    ;;
    * ) usage 1
    ;;
  esac
done

if [ "x${action}" == "x" ]; then
 printError "You must specify either -c (create) or -d (delete) ingress controller" 
 usage 1
fi

if [ "x${ingressType}" == "x" ]; then
 printError "You must specify ingress type (traefik, voyager or nginx) thru -t option"
 usage 1
fi 

case  ${ingressType} in 
   "traefik") 
              [[ -z "${release}"   ]] && release="${DefaultTraefikVersion}"
              [[ -z "${namespace}" ]] && namespace="${ingressType}"
              repository="traefik"
              chart="traefik-release"
              ;;
    "voyager") 
              [[ -z "${release}"   ]] && release="${DefaultVoyagerVersion}"
              [[ -z "${namespace}" ]] && namespace="${ingressType}"
              repository="appscode"
              chart="voyager-release"
              ;;
    "nginx")   
              [[ -z "${release}"   ]] && release="${DefaultNginxVersion}"
              [[ -z "${namespace}" ]] && namespace="${ingressType}"
              repository="ingress-nginx"
              chart="nginx-release"
              ;;
    *)         printError "Unsupported ingress type [${ingressType}]. Suppoprted ingress type are [traefik, voyager or nginx] "
               exit -1  ;;
esac

printInfo "Action [${action}], Type [${ingressType}], NameSpace [${namespace}], Release [${release}], Chart [$chart]"

# Validate Kubernetes CLI availability
# Try to execute kubernetes cli to see whether cli is available
if ! [ -x "$(command -v ${kubernetesCli})" ]; then
   printError "${kubernetesCli} is not installed"
  exit -1
fi

HELM_VERSION=$(helm version --short --client)
if [[ "$HELM_VERSION" =~ "v2" ]]; then
  printError "Detected unsupported Helm version [${HELM_VERSION}]"
  exit -1
fi

function createNameSpace() {
 ns=$1
 namespace=`${kubernetesCli} get namespace ${ns} 2> /dev/null | grep ${ns} | awk '{print $1}'`
 if [ -z ${namespace} ]; then
   printInfo "Adding namespace[$ns] to Kubernetes cluster"
   ${kubernetesCli} create namespace ${ns}
 fi
}

function waitForIngressPod() {
  type=$1
  ns=$2

  printInfo "Wait until ${type} ingress controller pod is running."
  ipod=$(${kubernetesCli} -o name get po -n ${ns})
  if [[ "${ipod}" != *$chart* ]]; then
   printError "Couldn't find the pod associated with ${type} helm deployment. List helm deployment status on namespace [${ns}]. "
   helm list -n ${ns}
   exit -1;
  fi
  printInfo "Found pod [${ipod}] associated with ${type} helm deployment on namespace [${ns}]."

  max=20
  count=0
  while test $count -lt $max; do
    status=$(${kubernetesCli} get ${ipod} -n ${ns} --no-headers 2> /dev/null | awk '{print $2}')
    #printInfo "[${type} Ingress controller pod status: ${status}]"
    if [ x${status} == "x1/1" ]; then
      echo " "
      printInfo "${type} controller pod is running now."
      ${kubernetesCli} get ${ipod} -n ${ns}
      break;
    fi
    count=`expr $count + 1`
    sleep 2
    echo -n "."
  done
  if test $count -eq $max; then
    ${kubernetesCli} describe ${ipod} -n ${ns}
    printError "${type} controller pod failed to start."
    exit 1
  fi
  ${kubernetesCli} get ${ipod} -n ${ns}
  helm list -n ${ns}
 } 

function createVoyager() {
  ns=${1}
  release=${2}
  createNameSpace $ns || true

  printInfo "Creating Voyager controller on namespace ${ns}"
  if [ "$(helm search repo appscode/voyager | grep voyager | wc -l)" = 0 ]; then
    printInfo "Add AppsCode chart repository"
    helm repo add appscode https://charts.appscode.com/stable/
    helm repo update
  else
    printInfo "AppsCode chart repository is already added."
  fi

  if [ "$(helm list --namespace $ns | grep $chart |  wc -l)" = 0 ]; then
    printInfo "Installing Voyager controller"
    # remove resources from previous run if any  
    purgeVoyagerResources || true
    helm install $chart appscode/voyager --version ${release}  \
      --namespace ${ns} \
      --set cloudProvider=baremetal \
      --set apiserver.enableValidatingWebhook=false \
      --set apiserver.healthcheck.enabled=false \
      --set ingressClass=voyager
    if [ $? != 0 ]; then
      printError "Helm istallation of the Voyager ingress controller failed."
      exit -1;
    fi
  else
    printInfo "Voyager controller is already installed."
    exit 0;
  fi 

  waitForIngressPod voyager ${ns}

  max=20
  count=0
  crd="ingresses.voyager.appscode.com"
  printInfo "Checking availability of voyager ingress resource [${crd}]"
  while test $count -lt $max; do
   obj=$(${kubernetesCli} get crd ${crd} -n ${ns} --no-headers 2> /dev/null | awk '{print $1}' || true)
   if [ "${obj}" == "$crd" ];  then
      echo " "
      printInfo "voyager ingress resource [${crd}] is available now."
      ${kubernetesCli} get crd ${crd} -n ${ns} --no-headers
      break;
   fi
   count=`expr $count + 1`
   echo -n "."
   sleep 2
  done
  if test $count -eq $max; then
    printError "voyager ingress resource can not be created" 
    exit 1
  fi
  exit 0
}

function createTraefik() {
  ns=${1}
  rel=${2}

  createNameSpace $ns || true
  if [ "$(helm search repo traefik/traefik | grep traefik |  wc -l)" = 0 ]; then
    # https://containous.github.io/traefik-helm-chart/
    # https://docs.traefik.io/getting-started/install-traefik/
    printInfo "Add Traefik chart repository"
    helm repo add traefik https://containous.github.io/traefik-helm-chart
    helm repo update
  else
    printInfo "Traefik chart repository is already added."
  fi

  if [ "$(helm list -q -n ${ns} | grep $chart | wc -l)" = 0 ]; then
    printInfo "Installing Traefik controller on namespace ${ns}"
    # https://github.com/containous/traefik-helm-chart/blob/master/traefik/values.yaml
    purgeDefaultResources || true 
    helm install $chart traefik/traefik --namespace ${ns} \
     --set image.tag=${rel} \
     --values ${UTILDIR}/../traefik/values.yaml 
    if [ $? != 0 ]; then 
     printError "Helm istallation of the Traefik ingress controller failed."
     exit -1;
    fi
  else
    printInfo "Traefik controller is already installed."
  fi

  waitForIngressPod traefik ${ns}
  tpod=$(${kubernetesCli} -o name get po -n ${ns})
  traefik_image=$(${kubernetesCli} get ${tpod} -n ${ns} -o jsonpath='{.spec.containers[0].image}')
  printInfo "Traefik image choosen [${traefik_image}]"
}

# Remove ingress related resources from default Namespace ( if any )
function purgeDefaultResources() {
   printInfo "Remove ingress related resources from default Namespace (if any)"
   crole=$(${kubernetesCli} get ClusterRole | grep ${chart} | awk '{print $1}')
   if [ "x${crole}" != "x" ]; then 
   ${kubernetesCli} get ClusterRole | grep ${chart} | awk '{print $1}' | xargs kubectl delete ClusterRole --ignore-not-found
   fi

   crb=$(${kubernetesCli} get ClusterRoleBinding | grep ${chart} | awk '{print $1}')
   if [ x${crb} != "x" ]; then 
  ${kubernetesCli} get ClusterRoleBinding | grep ${chart} | awk '{print $1}' | xargs kubectl delete ClusterRoleBinding 
   fi

   vwc=$(${kubernetesCli} get ValidatingWebhookConfiguration | grep ${chart} | awk '{print $1}')
   if [ x${vwc} != "x" ]; then 
  ${kubernetesCli} get ValidatingWebhookConfiguration | grep ${chart} | awk '{print $1}' | xargs kubectl delete ValidatingWebhookConfiguration 
   fi
}

# Remove voyager related resources from default Namespace ( if any )
function purgeVoyagerResources() {
  # get rid of Voyager CRD deletion deadlock:  https://github.com/kubernetes/kubernetes/issues/60538
  printInfo "Delete extra resources associated with Voyager Ingress Controller"
  crds=(certificates ingresses)
  for crd in "${crds[@]}"; do
    pairs=($(${kubernetesCli} get ${crd}.voyager.appscode.com --all-namespaces -o jsonpath='{range .items[*]}{.metadata.name} {.metadata.namespace} {end}' || true))
    total=${#pairs[*]}

    # save objects
    if [ $total -gt 0 ]; then
      printInfo "Dumping ${crd} objects into ${crd}.yaml"
      ${kubernetesCli} get ${crd}.voyager.appscode.com --all-namespaces -o yaml >${crd}.yaml
    fi

    for ((i = 0; i < $total; i += 2)); do
      name=${pairs[$i]}
      namespace=${pairs[$i + 1]}
      # remove finalizers
      ${kubernetesCli} patch ${crd}.voyager.appscode.com $name -n $namespace -p '{"metadata":{"finalizers":[]}}' --type=merge
      # delete crd object
      printInfo "Deleting ${crd} $namespace/$name"
      ${kubernetesCli} delete ${crd}.voyager.appscode.com $name -n $namespace --ignore-not-found
    done

    # delete crd
    ${kubernetesCli} delete crd ${crd}.voyager.appscode.com --ignore-not-found || true
  done
  # delete user/cluster roles
  ${kubernetesCli} delete clusterroles appscode:voyager:edit appscode:voyager:view --ignore-not-found
   ${kubernetesCli} delete ClusterRole/${chart} --ignore-not-found
   ${kubernetesCli} delete ClusterRoleBinding/${chart} --ignore-not-found
   ${kubernetesCli} delete ClusterRoleBinding/${chart}-apiserver-auth-delegator  --ignore-not-found
   kubectl delete RoleBinding/${chart}-apiserver-extension-server-authentication-reader -n kube-system --ignore-not-found
}

function deleteIngress() {
  type=${1}
  ns=${2}
  if [ "$(helm list --namespace $ns | grep $chart |  wc -l)" = 1 ]; then
    printInfo "Deleting ${type} controller from namespace $ns" 
    helm uninstall --namespace $ns $chart
    ${kubernetesCli} delete ns ${ns}
    printInfo "Remove ${type} chart repository [${repository}] "
    helm repo remove ${repository}
  else
    printInfo "${type} controller has already been deleted from namespace [${ns}] or not installed in the namespace [${ns}]." 
  fi

  if [ "${ingressType}" = traefik ]; then
      purgeDefaultResources || true
    elif [ "${ingressType}" = voyager ]; then
      purgeVoyagerResources || true
    elif [ "${ingressType}" = nginx ]; then
      purgeDefaultResources || true
    fi
}

function createNginx() {
  ns=${1}
  release=${2}
  chart="nginx-release"
  createNameSpace $ns || true
  printInfo "Creating Nginx controller on namespace ${ns}" 

  if [ "$(helm search repo ingress-nginx | grep nginx | wc -l)" = 0 ]; then
    printInfo "Add Nginx chart repository"
    helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
    helm repo update
  else
    printInfo "Nginx chart repository is already added."
  fi

  if [ "$(helm list --namespace ${ns} | grep $chart |  wc -l)" = 0 ]; then
    purgeDefaultResources || true
    helm install $chart ingress-nginx/ingress-nginx \
         --namespace ${ns} --version ${release}
    if [ $? != 0 ]; then
     printError "Helm istallation of the Nginx ingress controller failed."
     exit -1;
    fi
  else
    printInfo "Nginx controller is already installed."
    exit 0;
  fi

  waitForIngressPod nginx ${ns}
  tpod=$(${kubernetesCli} -o name get po -n ${ns})
  ${kubernetesCli} describe ${tpod} -n ${ns}
}

function main() {

  if [ "${action}" = "create" ]; then
    if [ "${ingressType}" = traefik ]; then
      printInfo "Selected Traefik release [${release}]"
      createTraefik ${namespace} ${release}
    elif [ "${ingressType}" = voyager ]; then
      printInfo "Selected Voyager release [$release]"
      createVoyager ${namespace} ${release}
    elif [ "${ingressType}" = nginx ]; then
      printInfo "Selected NGINX release [$release]"
      createNginx ${namespace} ${release}
    fi
  else
    if [ "${ingressType}" = traefik ]; then
      deleteIngress traefik ${namespace}
    elif [ "${ingressType}" = voyager ]; then
      deleteIngress  voyager ${namespace}
    elif [ "${ingressType}" = nginx ]; then
      deleteIngress  nginx ${namespace}
    fi
  fi
}

main "$@"
