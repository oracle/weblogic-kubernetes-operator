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

cluster_name=cluster-1
cluster_service_name=${DOMAIN_UID}-cluster-${cluster_name}
cluster_service_name=$(tr [A-Z_] [a-z-] <<< $cluster_service_name)
cluster_ingress_yaml=traefik-ingress-${cluster_service_name}.yaml

admin_name=admin-server
admin_service_name=${DOMAIN_UID}-${admin_name}
admin_service_name=$(tr [A-Z_] [a-z-] <<< $admin_service_name)
admin_ingress_yaml=traefik-ingress-console-${admin_service_name}.yaml

function get_kube_address() {
  kubectl cluster-info | grep KubeDNS | sed 's;^.*//;;' | sed 's;:.*$;;'
}

function get_sample_host() {
  tr [A-Z_] [a-z-] <<< ${DOMAIN_UID}.mii-sample.org
}

function get_curl_command() {
  echo "curl -s -S -m 10 -H 'host: $(get_sample_host)'"
}

function timestamp() {
  date --utc '+%Y-%m-%dT%H:%M:%S'
}

mkdir -p $WORKDIR/ingresses

echo "@@"
echo "@@ Info: Staging ingress yaml for the WebLogic cluster to 'WORKDIR/ingresses/${cluster_ingress_yaml}'"

#
# TBD there's 99% duplicate code for both ingresses - change into a single function that 
#     takes three arguments:  service_name & service_port & target_file
#

save_file=""
if [ -e  ${WORKDIR}/ingresses/${cluster_ingress_yaml} ]; then
  save_file=${WORKDIR}/ingresses/old/${cluster_ingress_yaml}.$(timestamp)
  mkdir -p $(dirname $save_file)
  echo "@@"
  cp ${WORKDIR}/ingresses/${cluster_ingress_yaml} ${save_file}
fi

cat << EOF > ${WORKDIR}/ingresses/${cluster_ingress_yaml}
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
  - host: $(get_sample_host)
    http:
      paths:
      - path: 
        backend:
          serviceName: ${cluster_service_name}
          servicePort: 8001

EOF

if [ ! "$save_file" = "" ]; then
  if [ "$(cat $save_file)" = "$(cat ${WORKDIR}/ingresses/${cluster_ingress_yaml})" ]; then
    rm $save_file
  else
    echo "@@ Notice! An old version of ingress yaml already exists and will be replaced."
    echo "@@ Notice! Saving old version of the domain resource file to the 'WORKDIR/ingresses/old' directory.'"
  fi
fi

# TBD add doc reference to Info that discusses the necessary browser extensions and/or /etc/hosts changes to make this work!

echo "@@"
echo "@@ Info: Staging ingress yaml for the admin server console to 'WORKDIR/ingresses/${admin_ingress_yaml}'."

save_file=""
if [ -e  ${WORKDIR}/ingresses/${admin_ingress_yaml} ]; then
  save_file=${WORKDIR}/ingresses/old/${admin_ingress_yaml}.$(timestamp)
  mkdir -p $(dirname $save_file)
  cp ${WORKDIR}/ingresses/${admin_ingress_yaml} ${save_file}
fi

cat << EOF > ${WORKDIR}/ingresses/${admin_ingress_yaml}
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
  - host: $(get_sample_host)
    http:
      paths:
      - path: /console
        backend:
          serviceName: ${admin_service_name}
          servicePort: 7001

EOF

if [ ! "$save_file" = "" ]; then
  if [ "$(cat $save_file)" = "$(cat ${WORKDIR}/ingresses/${admin_ingress_yaml})" ]; then
    rm $save_file
  else
    echo "@@ Notice! An old version of ingress yaml already exists and will be replaced."
    echo "@@ Notice! Saving old version of the domain resource file to the 'WORKDIR/ingresses/old' directory.'"
  fi
fi

# TBD add doc reference to Info that discusses the necessary browser extensions and/or /etc/hosts changes to make this work!

if [ "${1:-}" = "-nocreate" ]; then
  echo "@@ Info: Traefik ingress staging complete!"
  exit
fi

echo "@@"
echo "@@ Info: Creating traefik ingresses."
echo "@@"

kubectl delete -f ${WORKDIR}/ingresses/${cluster_ingress_yaml} --ignore-not-found
kubectl apply  -f ${WORKDIR}/ingresses/${cluster_ingress_yaml}

kubectl delete -f ${WORKDIR}/ingresses/${admin_ingress_yaml} --ignore-not-found
kubectl apply  -f ${WORKDIR}/ingresses/${admin_ingress_yaml}

cat << EOF
@@
@@ Info: If the Traefik operator and WebLogic domain are both running, then the sample app should respond to one or both of:

  $(get_curl_command) http://$(hostname).$(dnsdomainname):30305/sample_war/index.jsp

                        - or -

  $(get_curl_command) http://$(get_kube_address):30305/sample_war/index.jsp

@@
@@ Info: Traefik ingress staging and creation complete!
@@
EOF
