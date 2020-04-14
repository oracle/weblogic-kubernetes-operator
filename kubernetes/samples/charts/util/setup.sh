#!/bin/bash
# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This script is to create or delete Ingress controllers. 
# We support two ingress controllers: traefik and voyager.

UTILDIR="$(dirname "$(readlink -f "$0")")"
VNAME=voyager-operator  # release name for Voyager
TNAME=traefik-operator  # release name for Traefik
VSPACE=voyager # NameSpace for Voyager
TSPACE=traefik   # NameSpace for Traefik
DefaultVoyagerVersion=10.0.0

set -x

HELM_VERSION=$(helm version --short --client)

if [[ "$HELM_VERSION" =~ "v2" ]]; then
   echo "Detected helm version [${HELM_VERSION}]"
   v_list_args=""
   t_list_args=""
   v_helm_delete="helm delete --purge"
   t_helm_delete="helm delete --purge"
   v_helm_install="helm install appscode/voyager --name $VNAME  "
   t_helm_install="helm install stable/traefik --name $TNAME "
   helm_search_voyager="helm search repo | grep appscode/voyager"
   helm_search_traefik="helm search repo | grep stable/traefik"
elif [[ "$HELM_VERSION" =~ "v3" ]]; then
   echo "Detected helm version [${HELM_VERSION}]"
   v_list_args="--namespace $VSPACE "
   t_list_args="--namespace $TSPACE "
   v_helm_delete="helm uninstall --keep-history --namespace $VSPACE "
   t_helm_delete="helm uninstall --keep-history --namespace $TSPACE "
   v_helm_install="helm install $VNAME appscode/voyager  "
   t_helm_install="helm install $TNAME stable/traefik "
   helm_search_voyager="helm search appscode/voyager"
   helm_search_traefik="helm search stable/traefik"
else
    echo "Detected unsupported helm version [${HELM_VERSION}]"
    exit 1
fi

function createNameSpace() {
 ns=$1
 namespace=`kubectl get namespace ${ns} | grep ${ns} | awk '{print $1}'`
 if [ -z ${namespace} ]; then
   echo "Adding namespace[$ns] to kubernetes cluster"
   kubectl create namespace ${ns}
 fi
}

function createVoyager() {
  createNameSpace $VSPACE
  echo "Creating voyager operator on namespace ${VSPACE}"

  if [ "$(${helm_search_voyager} | grep voyager |  wc -l)" = 0 ]; then
    echo "Add appscode chart Repository"
    helm repo add appscode https://charts.appscode.com/stable/
    helm repo update
  else
    echo "Appscode chart repository is already added."
  fi

  if [ "$(helm list ${v_list_args} | grep $VNAME |  wc -l)" = 0 ]; then
    echo "Installing voyager operator."
    
    ${v_helm_install} --version ${VoyagerVersion}  \
      --namespace ${VSPACE} \
      --set cloudProvider=baremetal \
      --set apiserver.enableValidatingWebhook=false \
      --set ingressClass=voyager
  else
    echo "Voyager operator is already installed."
  fi 
  echo

  echo "Wait until voyager operator running."
  max=20
  count=0
  vpod==$(kubectl get po -n ${VSPACE} | grep voyager | awk '{print $1 }')
  while test $count -lt $max; do
    if test "$(kubectl get po -n ${VSPACE} | grep voyager | awk '{ print $2 }')" = 1/1; then
      echo "Voyager operator is running now."
      exit 0;
    fi
    count=`expr $count + 1`
    sleep 5
  done
  echo "ERROR: Voyager operator failed to start."
  kubectl describe pod/${vpod}  -n ${VSPACE}
  exit 1
}

function createTraefik() {
  createNameSpace $TSPACE
  echo "Creating traefik operator on namespace ${TSPACE}" 

  if [ "$(${helm_search_traefik} | grep traefik |  wc -l)" = 0 ]; then
    echo "Add K8SGoogle chart repository"
    helm repo add stable https://kubernetes-charts.storage.googleapis.com
    helm repo update
  else
    echo "K8SGoogle chart repository is already added."
  fi

  if [ "$(helm list ${t_list_args} | grep $TNAME |  wc -l)" = 0 ]; then
    echo "Installing traefik operator."
    ${t_helm_install} --namespace ${TSPACE} \
      --values ${UTILDIR}/../traefik/values.yaml
  else
    echo "Traefik operator is already installed."
  fi
  echo

  echo "Wait until traefik operator running."
  max=20
  count=0
  tpod=$(kubectl get po -n ${TSPACE} | grep traefik | awk '{print $1 }')
  while test $count -lt $max; do
    if test "$(kubectl get po -n ${TSPACE} | grep traefik | awk '{ print $2 }')" = 1/1; then
      echo "Traefik operator is running now."
      exit 0;
    fi
    count=`expr $count + 1`
    sleep 5
  done
  echo "ERROR: Traefik operator failed to start."
  echo
  kubectl describe pod/${tpod} -n ${TSPACE}
  exit 1
}


function purgeCRDs() {
  # get rid of Voyager crd deletion deadlock:  https://github.com/kubernetes/kubernetes/issues/60538
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
  if [ "$(helm list ${v_list_args} | grep $VNAME |  wc -l)" = 1 ]; then
    echo "Deleting voyager operator. "
    ${v_helm_delete} $VNAME 
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
  if [ "$(helm list ${t_list_args}| grep $TNAME |  wc -l)" = 1 ]; then
    echo "Deleting traefik operator." 
    ${t_helm_delete} $TNAME
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
