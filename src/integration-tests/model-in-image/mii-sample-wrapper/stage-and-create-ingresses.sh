#!/bin/bash
# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script stages Traefik ingresses for this sample's admin
# server and cluster ingresses to yaml files in WORKDIR/ingresses.
# It than calls 'kubectl apply -f' on the yaml by default.
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
# Optional environment variables (see 'env-custom.sh' for details):
#    WORKDIR
#    DOMAIN_NAMESPACE
#    DOMAIN_UID
#

# TBD add doc reference to Info that discusses the necessary 
#     browser extensions and/or /etc/hosts changes to get console
#     to work through the LB.


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
  kubectl cluster-info | grep KubeDNS | sed 's;^.*//;;' | sed 's;:.*$;;'
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
  echo "${1:-} This is a Traefik ingress for service '${2}'. Sample curl access:"
  echo "${1:-}"
  echo "${1:-}   $(get_curl_command $2) \\"
  echo "${1:-}     http://$(hostname).$(dnsdomainname):30305/myapp_war/index.jsp"
  echo "${1:-}                         - or -"
  echo "${1:-}   $(get_curl_command $2) \\"
  echo "${1:-}     http://$(get_kube_address):30305/myapp_war/index.jsp"
  echo "${1:-}"
}

mkdir -p $WORKDIR/ingresses

for service_name in $DOMAIN_UID-admin-server \
                    $DOMAIN_UID-cluster-cluster-1 \
                    $DOMAIN_UID-cluster-cluster-2
do

  target_yaml="$(get_service_yaml $service_name)"

  echo "@@ Info: Generating ingress file '$target_yaml'."

  if [ -e "$target_yaml" ]; then
    save_yaml="$(dirname $target_yaml)/old/$(basename $target_yaml).$(timestamp)"
    mkdir -p "$(dirname $save_yaml)"
    cp "$target_yaml" "$save_yaml"
    echo "@@ Notice! Saving old version of the ingress file to '$save_yaml'."
  fi

  cat << EOF > "$target_yaml"

$(get_help "# " "$service_name")

apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: traefik-ingress-$(get_service_name $service_name)
  namespace: ${DOMAIN_NAMESPACE}
  labels:
    weblogic.domainUID: ${DOMAIN_UID}
  annotations:
    kubernetes.io/ingress.class: traefik
spec:
  rules:
  - host: $(get_sample_host $service_name)
    http:
      paths:
      - path: 
        backend:
          serviceName: $(get_service_name $service_name)
          servicePort: 8001
EOF

  if [ ! "$DRY_RUN" = "true" ]; then
    echo "@@ Info: Creating traefik ingresses."

    kubectl delete -f "$target_yaml" --ignore-not-found
    kubectl apply  -f "$target_yaml"

    get_help "@@ Info: " "$service_name"
  fi

done
