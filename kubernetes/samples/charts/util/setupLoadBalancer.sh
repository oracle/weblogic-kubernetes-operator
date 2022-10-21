#!/bin/bash
# Copyright (c) 2018, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This script create or delete an Ingress controller. 
#  The script supports ingress controllers: Traefik and Nginx.

set -eu
set -o pipefail

UTILDIR="$(dirname "$(readlink -f "$0")")"

#Kubernetes command line interface. 
#Default is 'kubectl' if KUBERNETES_CLI env variable is not set.  
kubernetesCli=${KUBERNETES_CLI:-kubectl}

# https://github.com/containous/traefik/releases
DefaultTraefikVersion=2.6.0

# https://artifacthub.io/packages/helm/ingress-nginx/ingress-nginx
# https://docs.nginx.com/nginx-ingress-controller/installation/installation-with-helm/
# https://github.com/kubernetes/ingress-nginx/releases
DefaultNginxVersion=4.0.16

action=""
ingressType=""
namespace=""
release=""
repository=""
chart=""
ingressPropFile="ingress.properties"
skipDeleteNamespace="false"

# timestamp
#   purpose:  echo timestamp in the form yyyy-mm-ddThh:mm:ss.nnnnnnZ
#   example:  2018-10-01T14:00:00.000001Z
timestamp() {
  local timestamp="`date --utc '+%Y-%m-%dT%H:%M:%S.%NZ' 2>&1`"
  if [ ! "${timestamp/illegal/xyz}" = "${timestamp}" ]; then
    # old shell versions don't support %N or --utc
    timestamp="`date -u '+%Y-%m-%dT%H:%M:%S.000000Z' 2>&1`"
  fi
  echo "${timestamp}"
}

# Function to print an error message
printError() {
  echo [`timestamp`][ERROR] "$*"
}

# Function to print an error message
printInfo() {
  echo [`timestamp`][INFO] "$*"
}

usage() {
  cat << EOF
  Usage:
    $(basename $0) -c[d]  -t ingress-type  [-n namespace] [-v version]
    -c                   : create ingress controller [required]
    -d                   : delete ingress controller [required]
    -t <ingress type>    : ingress type traefik or nginx [required]
    -v <ingress version> : ingress release version
    -n <namespace>       : ingress namespace
    -p <ingress-prop>    : extra ingress helm properties 
    -s                   : skip deleting ingress namespace
    -m <kubernetes_cli>  : Kubernetes command line interface. Default is 'kubectl' if KUBERNETES_CLI env variable is not set. Otherwise default is the value of KUBERNETES_CLI env variable.
    -h                   : print help
EOF
exit $1
}

action_chosen=false

while getopts "scdt:p:n:r:v:h" opt; do
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
    s) skipDeleteNamespace="true"
       printInfo "Will Skip the Namespace Deletion"
    ;;
    n) namespace="${OPTARG}"
    ;;
    t) ingressType="${OPTARG}"
    ;;
    p) ingressPropFile="${OPTARG}"
       if [ ${action} == "create" ]; then
         if [ ! -f ${ingressPropFile} ]; then
          printError "[create] action is choosen but the custom ingress property file [${ingressPropFile}] is missing."
          usage 1
         fi
      fi 
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
 printError "You must specify ingress type (traefik or nginx) thru -t option"
 usage 1
fi 

case  ${ingressType} in 
   "traefik") 
              [[ -z "${release}"   ]] && release="${DefaultTraefikVersion}"
              [[ -z "${namespace}" ]] && namespace="${ingressType}"
              repository="traefik"
              chart="traefik-release"
              ;;
    "nginx")   
              [[ -z "${release}"   ]] && release="${DefaultNginxVersion}"
              [[ -z "${namespace}" ]] && namespace="${ingressType}"
              repository="ingress-nginx"
              chart="nginx-release"
              ;;
    *)         printError "Unsupported ingress type [${ingressType}]. Suppoprted ingress type are [traefik or nginx] "
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

createNameSpace() {
 ns=$1
 namespace=`${kubernetesCli} get namespace ${ns} 2> /dev/null | grep ${ns} | awk '{print $1}'`
 if [ -z ${namespace} ]; then
   printInfo "Adding namespace[$ns] to Kubernetes cluster"
   ${kubernetesCli} create namespace ${ns}
 fi
}

waitForIngressPod() {
  type=$1
  ns=$2
  if [[ "${type}" == "traefik" ]]; then
    instance=${type}-release-${ns}
  elif [[ "${type}" == "nginx" ]]; then
    instance=${type}-release
  fi

  printInfo "Wait (max 5min) until ${type} ingress controller pod to be ready."
  ${kubernetesCli} wait --namespace ${ns} \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/instance=${instance} \
  --timeout=300s

  if [ $? != 0 ]; then
   printError "${type} ingress controller pod not READY in state in 5 min"
   exit -1;
  else 
   ipod=$(${kubernetesCli} get pod -n ${ns} -l app.kubernetes.io/instance=${instance} -o jsonpath="{.items[0].metadata.name}")
   ${kubernetesCli} get po/${ipod} -n ${ns}
   helm list -n ${ns}
  fi 
 } 

createTraefik() {
  ns=${1}
  rel=${2}

  createNameSpace $ns || true
  if [ "$(helm search repo traefik/traefik | grep traefik |  wc -l)" = 0 ]; then
    # https://helm.traefik.io/traefik
    # https://doc.traefik.io/traefik/getting-started/install-traefik/#use-the-helm-chart
    printInfo "Add Traefik chart repository"
    helm repo add traefik https://helm.traefik.io/traefik --force-update
  else
    printInfo "Traefik chart repository is already added."
  fi

  # load the extra set of helm values if provided thru file using -p option
  if [ "$(helm status ${chart} -n ${ns})" != 0 ]; then
    printInfo "Installing Traefik controller on namespace ${ns}"
    purgeDefaultResources || true
    helm install $chart traefik/traefik --namespace ${ns} \
     $(cat ${ingressPropFile} 2>&- || false ) \
     --set image.tag=${rel} \
     --values ${UTILDIR}/../traefik/values.yaml 
    if [ $? != 0 ]; then 
     printError "Helm installation of the Traefik ingress controller failed."
     exit -1;
    fi
  else
    printInfo "Traefik controller is already installed."
  fi

  waitForIngressPod traefik ${ns}
  tpod=$(${kubernetesCli} -o name get po -n ${ns})
  traefik_image=$(${kubernetesCli} get ${tpod} -n ${ns} -o jsonpath='{.spec.containers[0].image}')
  printInfo "Traefik image chosen [${traefik_image}]"
  helm get values $chart --namespace ${ns} 
}

# Remove ingress related resources from default Namespace ( if any )
purgeDefaultResources() {
   printInfo "Remove ingress related resources from default Namespace (if any)"
  croles=$(${kubernetesCli} get ClusterRole | grep ${chart} | awk '{print $1}')
  for crole in ${croles}; do 
   printInfo "Deleting ClusterRole ${crole} from default Namespace"
   ${kubernetesCli} delete ClusterRole ${crole} 
  done

  crbs=$(${kubernetesCli} get ClusterRoleBinding | grep ${chart} | awk '{print $1}')
  for crb in ${crbs}; do 
   printInfo "Deleting ClusterRoleBinding ${crb} from default Namespace"
   ${kubernetesCli} delete ClusterRoleBinding ${crb} 
  done

  vwcs=$(${kubernetesCli} get ValidatingWebhookConfiguration | grep ${chart} | awk '{print $1}')
  for vwc in ${vwcs}; do 
    printInfo "Deleting ValidatingWebhookConfiguration ${vwc} from default Namespace"
    ${kubernetesCli} delete ValidatingWebhookConfiguration ${vwc} 
  done
}

deleteIngress() {
  type=${1}
  ns=${2}
  if [[ "${type}" == "traefik" ]]; then
    instance=${type}-release-${ns}
  elif [[ "${type}" == "nginx" ]]; then
    instance=${type}-release
  fi  
  if [ "$(helm list --namespace $ns | grep $chart |  wc -l)" = 1 ]; then
    printInfo "Deleting ${type} controller from namespace $ns" 
    helm uninstall --namespace $ns $chart
    ${kubernetesCli} wait --namespace ${ns} \
       --for=delete pod \
       --selector=app.kubernetes.io/instance=${instance} \
       --timeout=120s
    if [ ${skipDeleteNamespace} == "false" ]; then
      ${kubernetesCli} delete ns ${ns}
      ${kubernetesCli} wait --for=delete namespace ${ns} --timeout=60s || true
    fi
    printInfo "Remove ${type} chart repository [${repository}] "
    helm repo remove ${repository}
  else
    printInfo "${type} controller has already been deleted from namespace [${ns}] or not installed in the namespace [${ns}]." 
  fi

  if [ "${ingressType}" = traefik ]; then
      purgeDefaultResources || true
    elif [ "${ingressType}" = nginx ]; then
      purgeDefaultResources || true
    fi
}

createNginx() {
  ns=${1}
  release=${2}
  chart="nginx-release"
  createNameSpace $ns || true
  printInfo "Creating Nginx controller on namespace ${ns}" 

  if [ "$(helm search repo ingress-nginx | grep nginx | wc -l)" = 0 ]; then
    printInfo "Add Nginx chart repository"
    helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx --force-update
  else
    printInfo "Nginx chart repository is already added."
  fi

  # load the extra set of helm values if provided thru file using -p option
  if [ "$(helm list --namespace ${ns} | grep $chart |  wc -l)" = 0 ]; then
    purgeDefaultResources || true
    helm install $chart ingress-nginx/ingress-nginx \
      $(cat ${ingressPropFile} 2>&- || false ) \
      --set "controller.admissionWebhooks.enabled=false" \
      --namespace ${ns} --version ${release} \
      --set "controller.image.tag=v1.2.0" 
    if [ $? != 0 ]; then
     printError "Helm installation of the Nginx ingress controller failed."
     exit -1;
    fi
  else
    printInfo "Nginx controller is already installed."
    exit 0;
  fi

  waitForIngressPod nginx ${ns}
  tpod=$(${kubernetesCli} -o name get po -n ${ns})
  ${kubernetesCli} describe ${tpod} -n ${ns}
  helm get values $chart -n ${ns}
}

main() {

  if [ "${action}" = "create" ]; then
    if [ "${ingressType}" = traefik ]; then
      printInfo "Selected Traefik release [${release}]"
      createTraefik ${namespace} ${release}
    elif [ "${ingressType}" = nginx ]; then
      printInfo "Selected NGINX release [$release]"
      createNginx ${namespace} ${release}
    fi
  else
    if [ "${ingressType}" = traefik ]; then
      deleteIngress traefik ${namespace}
    elif [ "${ingressType}" = nginx ]; then
      deleteIngress  nginx ${namespace}
    fi
  fi
}

main "$@"
