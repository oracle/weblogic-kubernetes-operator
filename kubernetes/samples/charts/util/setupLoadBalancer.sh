#!/bin/bash
# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This script is to create or delete Ingress controllers. 
# Currently the script supports ingress controllers: Traefik, Voyager, and Nginx.
# Usage $0 create|delete traefik|voyager|nginx traefik_version|voyager_version|nginx_version

UTILDIR="$(dirname "$(readlink -f "$0")")"
VNAME=voyager-operator  # release name for Voyager
TNAME=traefik-operator  # release name for Traefik
NNAME=nginx-operator    # release name for Nginx

VSPACE=voyager          # namespace for Voyager
TSPACE=traefik          # namespace for Traefik
NSPACE=nginx            # namespace for Nginx

# https://hub.helm.sh/charts/appscode/voyager
# https://artifacthub.io/packages/helm/appscode/voyager
# https://github.com/voyagermesh/voyager#supported-versions
DefaultVoyagerVersion=12.0.0

# https://github.com/containous/traefik/releases
DefaultTraefikVersion=2.2.1

# https://github.com/kubernetes/ingress-nginx/releases
DefaultNginxVersion=2.16.0

HELM_VERSION=$(helm version --short --client)
if [[ "$HELM_VERSION" =~ "v2" ]]; then
  echo "Detected unsupported Helm version [${HELM_VERSION}]"
  exit -1
fi

function createNameSpace() {
 ns=$1
 namespace=`kubectl get namespace ${ns} 2> /dev/null | grep ${ns} | awk '{print $1}'`
 if [ -z ${namespace} ]; then
   echo "Adding namespace[$ns] to Kubernetes cluster"
   kubectl create namespace ${ns}
 fi
}

function createVoyager() {
  createNameSpace $VSPACE
  echo "Creating Voyager operator on namespace ${VSPACE}"
  echo
  if [ "$(helm search repo appscode/voyager | grep voyager | wc -l)" = 0 ]; then
    echo "Add AppsCode chart repository"
    helm repo add appscode https://charts.appscode.com/stable/
    helm repo update
  else
    echo "AppsCode chart repository is already added."
  fi

  if [ "$(helm list --namespace $VSPACE | grep $VNAME |  wc -l)" = 0 ]; then
    echo "Installing Voyager operator."
    helm install $VNAME appscode/voyager --version ${VoyagerVersion}  \
      --namespace ${VSPACE} \
      --set cloudProvider=baremetal \
      --set apiserver.enableValidatingWebhook=false \
      --set apiserver.healthcheck.enabled=false \
      --set ingressClass=voyager
  else
    echo "Voyager operator is already installed."
    exit 0;
  fi 
  echo

  echo "Wait until Voyager operator pod is running."
  max=20
  count=0
  vpod=$(kubectl get po -n ${VSPACE} --no-headers | awk '{print $1}')
  while test $count -lt $max; do
    status=$(kubectl get po -n ${VSPACE} --no-headers 2> /dev/null | awk '{print $2}')
    if [ ${status} == "1/1" ]; then
      echo "Voyager operator pod is running now."
      kubectl get pod/${vpod} -n ${VSPACE}
      break;
    fi
    count=`expr $count + 1`
    sleep 2
  done
  if test $count -eq $max; then
    echo "ERROR: Voyager operator pod failed to start."
    exit 1
  fi
  kubectl describe pod/${vpod} -n ${VSPACE}
  helm list -n ${VSPACE}
  
  max=20
  count=0
  crd="ingresses.voyager.appscode.com"
  echo "Checking availability of Custom Resource Definition [${crd}]"
  while test $count -lt $max; do
   obj=$(kubectl get crd ${crd} -n ${VSPACE} --no-headers 2>/dev/null | awk '{print $1}')
   if [ "${obj}" == "${crd}" ];  then
      echo "Custom Resource Definition [${crd}] is available now."
      kubectl get crd ${crd} -n ${VSPACE} --no-headers
      break;
   fi
   count=`expr $count + 1`
   sleep 2
  done
  if test $count -eq $max; then
    echo "ERROR: Resource Definition can not be created" 
    exit 1
  fi
  exit 0
}

function createTraefik() {
  createNameSpace $TSPACE
  echo "Creating Traefik operator on namespace ${TSPACE}" 

  if [ "$(helm search repo traefik/traefik | grep traefik |  wc -l)" = 0 ]; then
    # https://containous.github.io/traefik-helm-chart/
    # https://docs.traefik.io/getting-started/install-traefik/
    echo "Add Traefik chart repository"
    helm repo add traefik https://containous.github.io/traefik-helm-chart
    helm repo update
  else
    echo "Traefik chart repository is already added."
  fi

  if [ "$(helm list --namespace $TSPACE | grep $TNAME |  wc -l)" = 0 ]; then
    echo "Installing Traefik operator."
    # https://github.com/containous/traefik-helm-chart/blob/master/traefik/values.yaml
    helm install $TNAME traefik/traefik --namespace ${TSPACE} \
     --set image.tag=${TraefikVersion} \
     --values ${UTILDIR}/../traefik/values.yaml 
  else
    echo "Traefik operator is already installed."
    exit 0;
  fi
  echo

  echo "Wait until Traefik operator pod is running."
  max=20
  count=0
  tpod=$(kubectl get po -n ${TSPACE} --no-headers | awk '{print $1}')
  while test $count -lt $max; do
    status=$(kubectl get po -n ${TSPACE} --no-headers 2> /dev/null | awk '{print $2}')
    if [ ${status} == "1/1" ]; then
      echo "Traefik operator pod is running now."
      kubectl get pod/${tpod} -n ${TSPACE}
      traefik_image=$(kubectl get po/${tpod} -n ${TSPACE} -o jsonpath='{.spec.containers[0].image}')
      echo "Traefik image choosen [${traefik_image}]"
      exit 0;
    fi
    count=`expr $count + 1`
    sleep 2
  done
  echo "ERROR: Traefik operator pod failed to start."
  kubectl describe pod/${tpod} -n ${TSPACE}
  exit 1
}


function purgeCRDs() {
  # get rid of Voyager CRD deletion deadlock:  https://github.com/kubernetes/kubernetes/issues/60538
  crds=(certificates ingresses)
  for crd in "${crds[@]}"; do
    pairs=($(kubectl get ${crd}.voyager.appscode.com --all-namespaces -o jsonpath='{range .items[*]}{.metadata.name} {.metadata.namespace} {end}' || true))
    total=${#pairs[*]}

    # save objects
    if [ $total -gt 0 ]; then
      echo "Dumping ${crd} objects into ${crd}.yaml"
      kubectl get ${crd}.voyager.appscode.com --all-namespaces -o yaml >${crd}.yaml
    fi

    for ((i = 0; i < $total; i += 2)); do
      name=${pairs[$i]}
      namespace=${pairs[$i + 1]}
      # remove finalizers
      kubectl patch ${crd}.voyager.appscode.com $name -n $namespace -p '{"metadata":{"finalizers":[]}}' --type=merge
      # delete crd object
      echo "Deleting ${crd} $namespace/$name"
      kubectl delete ${crd}.voyager.appscode.com $name -n $namespace
    done

    # delete crd
    kubectl delete crd ${crd}.voyager.appscode.com || true
  done
  # delete user roles
  kubectl delete clusterroles appscode:voyager:edit appscode:voyager:view
}

function deleteVoyager() {
  if [ "$(helm list --namespace $VSPACE | grep $VNAME |  wc -l)" = 1 ]; then
    echo "Deleting voyager operator."
    helm uninstall --namespace $VSPACE $VNAME 
    kubectl delete ns ${VSPACE}
    purgeCRDs
  else
    echo "Voyager operator has already been deleted." 
  fi

  if [ "$(helm search repo appscode/voyager | grep voyager |  wc -l)" != 0 ]; then
    echo "Remove appscode chart repository."
    helm repo remove appscode
  fi

}

function deleteTraefik() {
  if [ "$(helm list --namespace $TSPACE | grep $TNAME |  wc -l)" = 1 ]; then
    echo "Deleting Traefik operator." 
    helm uninstall --namespace $TSPACE  $TNAME
    kubectl delete ns ${TSPACE}
    echo "Remove Traefik chart repository."
    helm repo remove traefik
  else
    echo "Traefik operator has already been deleted." 
  fi
}

function deleteNginx() {
  if [ "$(helm list --namespace $NSPACE | grep $NNAME |  wc -l)" = 1 ]; then
    echo "Deleting Nginx operator." 
    helm uninstall --namespace $NSPACE  $NNAME
    kubectl delete ns ${NSPACE}
    echo "Remove Nginx chart repository."
    helm repo remove ingress-nginx
  else
    echo "Nginx operator has already been deleted." 
  fi
}

function createNginx() {
  createNameSpace $NSPACE
  echo "Creating Nginx operator on namespace ${NSPACE}" 

  if [ "$(helm search repo ingress-nginx | grep nginx | wc -l)" = 0 ]; then
    echo "Add Nginx chart repository"
    helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
    helm repo update
  else
    echo "Nginx chart repository is already added."
  fi

  if [ "$(helm list --namespace ${NSPACE} | grep $NNAME |  wc -l)" = 0 ]; then
    echo "Installing Nginx operator."
    helm install $NNAME ingress-nginx/ingress-nginx \
         --namespace ${NSPACE} --version ${DefaultNginxVersion}
  else
    echo "Nginx operator is already installed."
    exit 0;
  fi
  echo

  echo "Wait until Nginx operator pod is running."
  max=20
  count=0
  tpod=$(kubectl get po -n ${NSPACE} --no-headers | awk '{print $1}')
  while test $count -lt $max; do
    status=$(kubectl get po -n ${NSPACE} --no-headers 2> /dev/null | awk '{print $2}')
    if [ ${status} == "1/1" ]; then
      echo "Nginx operator pod is running now."
      kubectl get pod/${tpod} -n ${NSPACE}
      kubectl exec -it $tpod -n ${NSPACE} -- /nginx-ingress-controller --version``
      exit 0;
    fi
    count=`expr $count + 1`
    sleep 2
  done
  echo "ERROR: Nginx operator pod failed to start."
  kubectl describe pod/${tpod} -n ${NSPACE}
  exit 1
}

function usage() {
  echo "usage: $0 create|delete traefik|voyager|nginx [traefik-version|voyager-version | nginx-version]"
  echo "Refer to https://github.com/voyagermesh/voyager#supported-versions for available Voyager version"
  echo "Refer to https://github.com/containous/traefik/releases for available Traefik version "
  echo "Refer to https://github.com/containous/traefik/releases for available NGINX version "
  exit 1
}

function main() {
  if [ "$#" -eq 0 ]; then
    usage
  fi
  if [ "$#" -lt 2 ]; then
    echo "[ERROR] Requires at least 2 positional parameters"
    usage
  fi

  if [ "$1" != create ] && [ "$1" != delete ]; then
    echo "[ERROR] The first parameter MUST be either create or delete "
    usage
  fi
  if [ "$2" != traefik ] && [ "$2" != voyager ] && [ "$2" != nginx ]; then
    echo "[ERROR] The second parameter MUST be either traefik, voyager or nginx "
    usage
  fi

  if [ "$1" = create ]; then
    if [ "$2" = traefik ]; then
      TraefikVersion="${3:-${DefaultTraefikVersion}}"
      echo "Selected Traefik version [$TraefikVersion]"
      createTraefik
    elif [ "$2" = voyager ]; then
      VoyagerVersion="${3:-${DefaultVoyagerVersion}}"
      echo "Selected Voyager version [$VoyagerVersion]"
      createVoyager
    else
      createNginx
    fi
  else
    if [ "$2" = traefik ]; then
      deleteTraefik
    elif [ "$2" = voyager ]; then
      deleteVoyager
    else
      deleteNginx
    fi

  fi
}

main "$@"
