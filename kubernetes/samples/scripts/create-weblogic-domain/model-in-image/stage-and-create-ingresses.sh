#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script stages Traefik ingresses for this sample's admin
# server and cluster. It assumes the following:
#
#   - Traefik is (or will be) deployed, and monitors DOMAIN_NAMESPACE
#     (otherwise the ingresses will simply be ignored).
#   - Traefik's external port is 30305.
#   - The WL admin server is named 'admin-server' and listens on port 7001.
#   - The WL cluster is named 'cluster-1' and listens on port 8001.
#
# Optional environment variables (see 'env-custom.sh' for details):
#
#    WORKDIR
#    DOMAIN_NAMESPACE
#    DOMAIN_UID
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

echo "@@ Info: DOMAIN_NAMESPACE=$DOMAIN_NAMESPACE"
echo "@@ Info: DOMAIN_UID=$DOMAIN_UID"

cluster_name=cluster-1
cluster_service_name=${DOMAIN_UID}-cluster-${cluster_name}
cluster_service_name=$(tr [A-Z_] [a-z-] <<< $cluster_service_name)
cluster_ingress_yaml=traefik-ingress-${cluster_service_name}.yaml

admin_name=admin-server
admin_service_name=${DOMAIN_UID}-${admin_name}
admin_service_name=$(tr [A-Z_] [a-z-] <<< $admin_service_name)
admin_ingress_yaml=traefik-ingress-console-${admin_service_name}.yaml

mkdir -p $WORKDIR

function kubehost() {
  kubectl cluster-info | grep KubeDNS | sed 's;^.*//;;' | sed 's;:.*$;;'
}

echo "@@"
echo "@@ Info: Staging ingress yaml for the WebLogic cluster to 'WORKDIR/${cluster_ingress_yaml}'"

if [ -e  ${WORKDIR}/${cluster_ingress_yaml} ]; then
  echo "@@"
  echo "@@ Notice: Skipping stage operation! Target file already exists."
else

cat << EOF > ${WORKDIR}/${cluster_ingress_yaml}
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

fi

echo "@@"
echo "@@ Info: Staging ingress yaml for the admin server console to 'WORKDIR/${admin_ingress_yaml}"

if [ -e ${WORKDIR}/${admin_ingress_yaml} ]; then
  echo "@@"
  echo "@@ Notice: Skipping stage operation! Target file already exists."
else

cat << EOF > ${WORKDIR}/${admin_ingress_yaml}
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: console-${admin_service_name}-traefik-ingress
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
      - path: /console
        backend:
          serviceName: ${admin_service_name}
          servicePort: 7001

EOF

fi

echo "@@"
echo "@@ Info: Creating traefik ingresses."
echo "@@"

kubectl delete -f ${WORKDIR}/${cluster_ingress_yaml} --ignore-not-found
kubectl apply  -f ${WORKDIR}/${cluster_ingress_yaml}

kubectl delete -f ${WORKDIR}/${admin_ingress_yaml} --ignore-not-found
kubectl apply  -f ${WORKDIR}/${admin_ingress_yaml}

echo "@@"
echo "@@ Info: When the Traefik operator and WebLogic domain are both running:"
echo ""
echo "   - the WebLogic console should be available at"
echo "        'http://$(hostname).$(dnsdomainname):30305/console'"
echo "     or"
echo "        'http://$(kubehost):30305/console' (weblogic/welcome1)."
echo ""
echo "   - the sample app should be available at"
echo "        'http://$(hostname).$(dnsdomainname):30305/sample_war/index.jsp'"
echo "     or"
echo "        'http://$(kubehost):30305/sample_war/index.jsp'"
echo ""
echo "@@"
echo "@@ Info: Traefik ingress staging and creation complete!"

