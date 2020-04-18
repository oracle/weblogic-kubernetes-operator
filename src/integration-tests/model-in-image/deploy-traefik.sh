#!/bin/bash

# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# TBD doc

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$TESTDIR/../../.." > /dev/null 2>&1 ; pwd -P )"

set -eu
set -o pipefail

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

TRAEFIK_NAME=${TRAEFIK_NAME:-traefik-operator}
TRAEFIK_NAMESPACE=${TRAEFIK_NAMESPACE:-${TRAEFIK_NAME}-ns}

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}

cluster_name=cluster-1
cluster_service_name=${DOMAIN_UID}-cluster-${cluster_name}
cluster_service_name=$(tr [A-Z_] [a-z-] <<< $cluster_service_name)

admin_name=admin-server
admin_service_name=${DOMAIN_UID}-${admin_name}
admin_service_name=$(tr [A-Z_] [a-z-] <<< $admin_service_name)

function kubehost() {
  kubectl cluster-info | grep KubeDNS | sed 's;^.*//;;' | sed 's;:.*$;;'
}

#
# Helm uninstall then install traefik
# Skip if it's up and running and it has the same external ports and namespace values
#

set +e
helm get values ${TRAEFIK_NAME} -n ${TRAEFIK_NAMESPACE} > $WORKDIR/traefik-values.cur 2>&1
res=$?
set -e
echo ${DOMAIN_NAMESPACE} >> $WORKDIR/traefik-values.cur
if [ $res -eq 0 ] \
   && [ -e "$WORKDIR/traefik-values.orig" ] \
   && [ "$(cat $WORKDIR/traefik-values.cur)" = "$(cat $WORKDIR/traefik-values.orig)" ]; then
  echo "@@"
  echo "@@ Traefik already installed. Skipping uninstall/install."
  echo "@@"
else
  set +e
  helm uninstall $TRAEFIK_NAME -n $TRAEFIK_NAMESPACE
  kubectl create namespace $TRAEFIK_NAMESPACE
  kubectl create namespace $DOMAIN_NAMESPACE
  set -e

  cd ${SRCDIR}

  # you only need to add the repo once, but we do it every time for simplicity
  helm repo add stable https://kubernetes-charts.storage.googleapis.com/

  helm install ${TRAEFIK_NAME} stable/traefik \
    --namespace $TRAEFIK_NAMESPACE \
    --values kubernetes/samples/charts/traefik/values.yaml \
    --set "kubernetes.namespaces={$TRAEFIK_NAMESPACE,$DOMAIN_NAMESPACE}" \
    --wait

  # Save Traefik settings (we will check this if this script is run again)
  helm get values ${TRAEFIK_NAME} -n ${TRAEFIK_NAMESPACE} > $WORKDIR/traefik-values.orig 2>&1
  echo ${DOMAIN_NAMESPACE} >> $WORKDIR/traefik-values.orig
fi

#
# Stage ingress yaml for cluster-1
#

cat << EOF > ${WORKDIR}/traefik-ingress-${cluster_service_name}.yaml
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: ${cluster_service_name}-traefik-ingress
  namespace: ${DOMAIN_NAMESPACE}
  labels:
    weblogic.domainUID: ${DOMAIN_UID}
  annotations:
    kubernetes.io/ingress.class: traefik
spec:
  rules:
  - host:
    http:
      paths:
      - path: 
        backend:
          serviceName: ${cluster_service_name}
          servicePort: 8001

EOF

#
# Stage ingress yaml for console on admin server
#

cat << EOF > ${WORKDIR}/traefik-ingress-console-${admin_service_name}.yaml
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: console-${admin_service_name}-traefik-ingress
  namespace: ${DOMAIN_NAMESPACE}
  annotations:
    kubernetes.io/ingress.class: traefik
spec:
  rules:
  - host:
    http:
      paths:
      - path: /console
        backend:
          serviceName: ${admin_service_name}
          servicePort: 7001

EOF

#
# Deploy Traefik ingresses
#

kubectl delete -f ${WORKDIR}/traefik-ingress-${cluster_service_name}.yaml --ignore-not-found
kubectl apply  -f ${WORKDIR}/traefik-ingress-${cluster_service_name}.yaml

kubectl delete -f ${WORKDIR}/traefik-ingress-console-${admin_service_name}.yaml --ignore-not-found
kubectl apply  -f ${WORKDIR}/traefik-ingress-console-${admin_service_name}.yaml

# TBD this assumes the k8s cluster includes the test host:
echo "@@"
echo "@@ Info: The WebLogic console should now be available at 'http://$(hostname).$(dnsdomainname):30305/console' or 'http://$(kubehost):30305/console' (weblogic/welcome1)."
echo "@@ Info: The sample app should now be available at 'http://$(hostname).$(dnsdomainname):30305/sample_war/index.jsp' or 'http://$(kubehost):30305/sample_war/index.jsp'"
echo "@@"

# TBD should the ingress setup be moved to the sample?
