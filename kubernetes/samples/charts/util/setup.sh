#!/bin/bash
# Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#  This script is to create or delete Ingress controllers. We support two ingress controllers: traefik and voyager.

MYDIR="$(dirname "$(readlink -f "$0")")"
VNAME=voyager-operator  # release name of Voyager
TNAME=traefik-operator  # release name of Traefik
VSPACE=voyager # NameSpace for Voyager
TSPACE=traefik   # NameSpace for Traefik
DefaultVoyagerVersion=10.0.0

HELM_VERSION=$(helm version --short --client)

if [[ "$HELM_VERSION" =~ "v2" ]]; then
   echo "Detected Helm Version [${HELM_VERSION}]"
   v_list_args=""
   t_list_args=""
   v_helm_delete="helm delete --purge"
   t_helm_delete="helm delete --purge"
   v_helm_install="helm install appscode/voyager --name $VNAME  "
   t_helm_install="helm install stable/traefik --name $TNAME "
   helm_search="helm search repo | grep appscode/voyager"
elif [[ "$HELM_VERSION" =~ "v3" ]]; then
   echo "Detected Helm Version [${HELM_VERSION}]"
   v_list_args="--namespace $VSPACE "
   t_list_args="--namespace $TSPACE "
   v_helm_delete="helm uninstall --keep-history --namespace $VSPACE "
   t_helm_delete="helm uninstall --keep-history --namespace $TSPACE "
   v_helm_install="helm install $VNAME appscode/voyager  "
   t_helm_install="helm install $TNAME stable/traefik "
   helm_search="helm search appscode/voyager"
else
    echo "Detected Unsuppoted Helm Version [${HELM_VERSION}]"
    exit 1
fi

function createNameSpace() {
 ns=$1
 namespace=`kubectl get namespace ${ns} | grep ${ns} | awk '{print $1}'`
 if [ -z ${namespace} ]; then
   echo "Adding NameSpace[$ns] to Kubernetes Cluster"
   kubectl create namespace ${ns}
 fi
}

function createVoyager() {
  createNameSpace $VSPACE
  echo "Creating Voyager operator on namespace 'voyager'."
  echo

  if [ "$(${helm_search} | grep voyager |  wc -l)" = 0 ]; then
    echo "Add Appscode Chart Repository"
    helm repo add appscode https://charts.appscode.com/stable/
    helm repo update
  else
    echo "Appscode Chart Repository is already added."
  fi
  echo

  if [ "$(helm list ${v_list_args} | grep $VNAME |  wc -l)" = 0 ]; then
    echo "Installing Voyager Operator."
    
    ${v_helm_install} --version ${VoyagerVersion}  \
      --namespace ${VSPACE} \
      --set cloudProvider=baremetal \
      --set apiserver.enableValidatingWebhook=false \
      --set ingressClass=voyager
  else
    echo "Voyager operator is already installed."
  fi 
  echo

  echo "Wait until Voyager operator running."
  max=20
  count=0
  while [ $count -lt $max ]; do
    kubectl -n ${VSPACE} get pod
    if [ "$(kubectl -n voyager get pod | grep voyager | awk '{ print $2 }')" = 1/1 ]; then
      echo "Voyager operator is running now."
      exit 0;
    fi
    count=`expr $count + 1`
    sleep 5
  done
  echo "Error: Voyager operator failed to start."
  exit 1

}

function createTraefik() {
  createNameSpace $TSPACE
  echo "Creating Traefik operator on namespace 'traefik'." 
  echo

  if [ "$(helm list ${t_list_args} | grep $TNAME |  wc -l)" = 0 ]; then
    echo "Installing Traefik Operator."
    ${t_helm_install} --namespace ${TSPACE} --values ${MYDIR}/../traefik/values.yaml
  else
    echo "Traefik Operator is already installed."
  fi
  echo

  echo "Wait until Traefik operator running."
  max=20
  count=0
  while test $count -lt $max; do
    kubectl -n traefik get pod
    if test "$(kubectl -n ${TSPACE} get pod | grep traefik | awk '{ print $2 }')" = 1/1; then
      echo "Traefik operator is running now."
      exit 0;
    fi
    count=`expr $count + 1`
    sleep 5
  done
  echo "Error: Traefik operator failed to start."
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
      echo "dumping ${crd} objects into ${crd}.yaml"
      kubectl get ${crd}.voyager.appscode.com --all-namespaces -o yaml >${crd}.yaml
    fi

    for ((i = 0; i < $total; i += 2)); do
      name=${pairs[$i]}
      namespace=${pairs[$i + 1]}
      # remove finalizers
      kubectl patch ${crd}.voyager.appscode.com $name -n $namespace -p '{"metadata":{"finalizers":[]}}' --type=merge
      # delete crd object
      echo "deleting ${crd} $namespace/$name"
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
    echo "Deleting Voyager Operator. "
    ${v_helm_delete} $VNAME 
    kubectl delete ns ${VSPACE}
    purgeCRDs
  else
    echo "Voyager operator has already been deleted" 
  fi
  echo

  if [ "$(helm search appscode/voyager | grep voyager |  wc -l)" != 0 ]; then
    echo "Remove Appscode Chart Repository."
    helm repo remove appscode
  fi
  echo

}

function deleteTraefik() {
  if [ "$(helm list ${t_list_args}| grep $TNAME |  wc -l)" = 1 ]; then
    echo "Deleting Traefik operator." 
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
    echo "[ERROR] Requires atleast 2 positional parameters"
    usage
  fi

  if [ "$1" != create ] && [ "$1" != delete ]; then
    echo "[ERROR] The first parameter MUST be either create or delete "
    usage
  fi
  if [ "$2" != traefik ] && [ "$2" != voyager ]; then
    echo "[ERROR] The second  parameter MUST be either traefik  or voyager "
    usage
  fi

  if [ "$1" = create ]; then
    if [ "$2" = traefik ]; then
      createTraefik
    else
      VoyagerVersion="${3:-${DefaultVoyagerVersion}}"
      echo "Selected Voyager Version [$VoyagerVersion]"
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
