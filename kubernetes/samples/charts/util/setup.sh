#!/bin/bash
# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This script is to create or delete Ingress controllers. 
# We support two ingress controllers: Traefik and Voyager.

UTILDIR="$(dirname "$(readlink -f "$0")")"
VNAME=voyager-operator  # release name for Voyager
TNAME=traefik-operator  # release name for Traefik
VSPACE=voyager          # namespace for Voyager
TSPACE=traefik          # namespace for Traefik
DefaultVoyagerVersion=10.0.0

HELM_VERSION=$(helm version --short --client)
if [[ "$HELM_VERSION" =~ "v2" ]]; then
  echo "Detected unsupported Helm version [${HELM_VERSION}]"
  exit -1
fi

function createNameSpace() {
 ns=$1
 namespace=`kubectl get namespace ${ns} | grep ${ns} | awk '{print $1}'`
 if [ -z ${namespace} ]; then
   echo "Adding namespace[$ns] to Kubernetes cluster"
   kubectl create namespace ${ns}
 fi
}

function createVoyager() {
  createNameSpace $VSPACE
  echo "Creating Voyager operator on namespace ${VSPACE}"
  echo

  if [ "$(helm search appscode/voyager | grep voyager |  wc -l)" = 0 ]; then
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
      --set ingressClass=voyager
  else
    echo "Voyager operator is already installed."
    exit 0;
  fi 
  echo

  echo "Wait until Voyager operator pod is running."
  max=20
  count=0
  vpod==$(kubectl get po -n ${VSPACE} | grep voyager | awk '{print $1 }')
  while test $count -lt $max; do
    if test "$(kubectl get po -n ${VSPACE} | grep voyager | awk '{ print $2 }')" = 1/1; then
      echo "Voyager operator pod is running now."
      exit 0;
    fi
    count=`expr $count + 1`
    sleep 5
  done
  echo "ERROR: Voyager operator pod failed to start."
  kubectl describe pod/${vpod}  -n ${VSPACE}
  exit 1
}

function createTraefik() {
  createNameSpace $TSPACE
  echo "Creating Traefik operator on namespace ${TSPACE}" 

  if [ "$(helm search stable/traefik | grep traefik |  wc -l)" = 0 ]; then
    echo "Add K8SGoogle chart repository"
    helm repo add stable https://kubernetes-charts.storage.googleapis.com
    helm repo update
  else
    echo "K8SGoogle chart repository is already added."
  fi

  if [ "$(helm list --namespace $TSPACE | grep $TNAME |  wc -l)" = 0 ]; then
    echo "Installing Traefik operator."
    helm install $TNAME stable/traefik --namespace ${TSPACE} \
      --values ${UTILDIR}/../traefik/values.yaml
  else
    echo "Traefik operator is already installed."
    exit 0;
  fi
  echo

  echo "Wait until Traefik operator pod is running."
  max=20
  count=0
  tpod=$(kubectl get po -n ${TSPACE} | grep traefik | awk '{print $1 }')
  while test $count -lt $max; do
    if test "$(kubectl get po -n ${TSPACE} | grep traefik | awk '{ print $2 }')" = 1/1; then
      echo "Traefik operator pod is running now."
      exit 0;
    fi
    count=`expr $count + 1`
    sleep 5
  done
  echo "ERROR: Traefik operator pod failed to start."
  echo
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
    echo "Deleting voyager operator. "
    helm uninstall --namespace $VSPACE $VNAME 
    kubectl delete ns ${VSPACE}
    purgeCRDs
  else
    echo "Voyager operator has already been deleted" 
  fi

  if [ "$(helm search appscode/voyager | grep voyager |  wc -l)" != 0 ]; then
    echo "Remove appscode chart repository."
    helm repo remove appscode
  fi

}

function deleteTraefik() {
  if [ "$(helm list --namespace $TSPACE | grep $TNAME |  wc -l)" = 1 ]; then
    echo "Deleting Traefik operator." 
    helm uninstall --namespace $TSPACE  $TNAME
    kubectl delete ns ${TSPACE}
  else
    echo "Traefik operator has already been deleted" 
  fi
}

function usage() {
  echo "usage: $0 create|delete traefik|voyager [voyager version]"
  exit 1
}

function main() {
  if [ "$#" -lt 2 ]; then
    echo "[ERROR] Requires at least 2 positional parameters"
    usage
  fi

  if [ "$1" != create ] && [ "$1" != delete ]; then
    echo "[ERROR] The first parameter MUST be either create or delete "
    usage
  fi
  if [ "$2" != traefik ] && [ "$2" != voyager ]; then
    echo "[ERROR] The second  parameter MUST be either traefik or voyager "
    usage
  fi

  if [ "$1" = create ]; then
    if [ "$2" = traefik ]; then
      createTraefik
    else
      VoyagerVersion="${3:-${DefaultVoyagerVersion}}"
      echo "Selected voyager version [$VoyagerVersion]"
      createVoyager
    fi
  else
    if [ "$2" = traefik ]; then
      deleteTraefik
    else
      deleteVoyager
    fi
  fi
}

main "$@"
