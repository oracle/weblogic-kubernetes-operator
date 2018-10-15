
#!/bin/bash
#  Copyright 2017, 2018, Oracle Corporation and/or affiliates.  All rights reserved.

# Remove Voyager Ingress
if test "$(kubectl get service/path-routing-stats  | grep path-routing-stats | wc -l)" = 1; then
  kubectl delete -f samples/path-routing.yaml
  podName=$(kubectl  get pod | grep path-routing | awk '{ print $1 }')
  while test "$(kubectl  get pod | grep $podName | wc -l)" != 0; do
    kubectl  get pod | grep path-routing
    sleep 3
  done
fi
if test "$(kubectl get service/host-routing-stats  | grep host-routing-stats | wc -l)" = 1; then
  kubectl delete -f samples/host-routing.yaml
  podName=$(kubectl  get pod | grep host-routing | awk '{ print $1 }')
  while test "$(kubectl  get pod | grep $podName | wc -l)" != 0; do
    kubectl  get pod | grep host-routing
    sleep 3
  done
fi

# Delete Voyager Operator
if test "$(kubectl get ns | grep voyager |  wc -l)" = 1; then
  helm delete --purge voyager-operator
  kubectl delete ns voyager
fi

# Remove Appscode Chart Repository
if test "$(helm search appscode/voyager | grep voyager |  wc -l)" != 0; then
  helm repo remove appscode
fi

# Remove onessl.
#if [ -e ~/bin/onessl ]; then
#  rm ~/bin/onessl
#fi
