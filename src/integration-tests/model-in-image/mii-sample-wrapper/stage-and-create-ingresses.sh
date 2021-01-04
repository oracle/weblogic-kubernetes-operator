#!/bin/bash
# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script stages Traefik ingresses for this sample's admin
# server and cluster ingresses to yaml files in WORKDIR/ingresses.
# It than calls 'kubectl apply -f' on the yaml by default.
#
# Note: For admin server ingresses, it skips generating yaml or
# calling apply -f unless the DOMAIN_UID is 'sample-domain1' because
# multiple admin server ingresses would  compete for the
# same wide open 'host'. See internal comments below for details.
#
# It assumes the following:
#   - Traefik is (or will be) deployed, and monitors DOMAIN_NAMESPACE
#     (otherwise the ingresses will simply be ignored).
#   - Traefik's external port is 30305.
#   - The WL admin server is named 'admin-server' and listens on port 7001.
#   - a first WL cluster is named 'cluster-1' and listens on port 8001.
#   - A second WL cluster (if any) is named 'cluster-2' and listens on port 9001.
#
# Optional param:
#   '-dry' Stage the ingress yaml files, but don't call 'kubectl'.
#
# Optional environment variables (see ./README for details):
#    WORKDIR
#    DOMAIN_NAMESPACE
#    DOMAIN_UID
#

set -eu
set -o pipefail

SCRIPTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source $SCRIPTDIR/env-init.sh

DRY_RUN="false"
[ "${1:-}" = "-dry" ] && DRY_RUN="true"

function get_service_name() {
  # $1 is service name
  echo $(tr [A-Z_] [a-z-] <<< $1)
}

function get_service_yaml() {
  # $1 is service name
  echo "$WORKDIR/ingresses/traefik-ingress-$(get_service_name $1).yaml"
}

function get_kube_address() {
  echo "\$(kubectl cluster-info | grep KubeDNS | sed 's;^.*//;;' | sed 's;:.*$;;')"
}

function get_sample_host() {
  # $1 is service name
  tr [A-Z_] [a-z-] <<< $1.mii-sample.org
}

function get_curl_command() {
  # $1 is service name
  echo "curl -s -S -m 10 -H 'host: $(get_sample_host $1)'"
}

function timestamp() {
  date --utc '+%Y-%m-%dT%H:%M:%S'
}

function get_help() {
  # $1 is echo prefix
  # $2 is service name
  echo "${1:-}"
  echo "${1:-} This is a Traefik ingress for service '${2}'."
  echo "${1:-}"
  echo "${1:-} Sample curl access:"
  echo "${1:-}"
  echo "${1:-}  Using 'localhost':"
  echo "${1:-}   $(get_curl_command $2) \\"
  echo "${1:-}     http://localhost:30305/myapp_war/index.jsp"
  echo "${1:-}                         - or -"
  echo "${1:-}  Using 'machine host':"
  echo "${1:-}   $(get_curl_command $2) \\"
  echo "${1:-}     http://\$(hostname).\$(dnsdomainname):30305/myapp_war/index.jsp"
  echo "${1:-}                         - or -"
  echo "${1:-}  Using 'kubernetes cluster host':"
  echo "${1:-}   $(get_curl_command $2) \\"
  echo "${1:-}     http://$(get_kube_address):30305/myapp_war/index.jsp"
  echo "${1:-}"
  echo "${1:-} If Traefik is unavailable and your admin server pod is running, try 'kubectl exec':"
  echo "${1:-}"
  echo "${1:-}   kubectl exec -n sample-domain1-ns $DOMAIN_UID-admin-server -- bash -c \\"
  echo "${1:-}     \"curl -s -S -m 10 http://$service_name:8001/myapp_war/index.jsp\""
  echo "${1:-}"
}

mkdir -p $WORKDIR/ingresses

for service_name in $DOMAIN_UID-admin-server \
                    $DOMAIN_UID-cluster-cluster-1
do

  target_yaml="$(get_service_yaml $service_name)"

  echo "@@ Info: Generating ingress file '$target_yaml'."

  if [ "${service_name/admin//}" = "$service_name" ]; then
    # assume we're _not_ an admin server

  cat << EOF > "$target_yaml"
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

$(get_help "# " "$service_name")

apiVersion: traefik.containo.us/v1alpha1
kind: IngressRoute
metadata:
  name: traefik-ingress-$(get_service_name $service_name)
  namespace: ${DOMAIN_NAMESPACE}
  labels:
    weblogic.domainUID: ${DOMAIN_UID}
  annotations:
    kubernetes.io/ingress.class: traefik

spec:
  routes:
  - kind: Rule
    match: Host(\`$(get_sample_host $service_name)\`)
    services:
    - kind: Service
      name: $(get_service_name $service_name)
      port: 8001
EOF

  else

    # assume we're an admin server
    #
    # Note: The admin server console doesn't easily support setting host in an
    # ingress for demo  purposes (it requires configuring /etc/hosts or DNS to 
    # make the host resolvable by the browser), so we don't set the host. This
    # has the limitation  that only one  admin ingress and  therefore only one
    # admin console on one domain-uid should be deployed per Traefik node port,
    # since more than one would try route form the same port...

    if [ "$DOMAIN_UID" = "sample-domain1" ]; then
    
  cat << EOF > "$target_yaml"
# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

apiVersion: traefik.containo.us/v1alpha1
kind: IngressRoute
metadata:
  name: traefik-ingress-$(get_service_name $service_name)
  namespace: ${DOMAIN_NAMESPACE}
  labels:
    weblogic.domainUID: ${DOMAIN_UID}
  annotations:
    kubernetes.io/ingress.class: traefik
spec:
  routes:
  - kind: Rule
    match: PathPrefix(\`/console\`)
    services:
    - kind: Service
      name: $(get_service_name $service_name)
      port: 7001
EOF
    fi
  fi

  if [ ! "$DRY_RUN" = "true" ] && [ -e "$target_yaml" ]; then
    echo "@@ Info: Creating traefik ingresses."

    kubectl delete -f "$target_yaml" --ignore-not-found
    kubectl apply  -f "$target_yaml"

    if [ "${service_name/admin//}" = "$service_name" ]; then
      # assume we're _not_ an admin server
      get_help "@@ Info: " "$service_name"
    fi
  fi

done
