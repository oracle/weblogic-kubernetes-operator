# !/bin/sh
# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -eu
set -o pipefail

function usage() {
cat<<EOF

Usage:
  $(basename $0) [-n DomainNS] [-d DomainUID] [-c clusterName] [-m kubernetesCli] [-h]

  This script displays a table of WebLogic Cluster status
  replica information for each given WebLogic cluster.

  Omit '-n' or set it to "" to indicate all namespaces.
  Omit '-d' or set it to "" to indicate all domains in the given namespace(s).
  Omit '-c' or set it to "" to indicate all clusters in the given domain(s) and the given namespace(s).
  Use '-m' to specify a Kubernetes command line interface. Default is 'kubectl' if KUBERNETES_CLI env
  variable is not set. Otherwise default is the value of KUBERNETES_CLI env variable.
  Use '-h' to get this help.

Examples:
  - Display all uid across all ns                       : $(basename $0)
  - Display all clusters with uid 'myuid' across all ns : $(basename $0) -d myuid
  - Display all clusters and all uid in ns 'myns'       : $(basename $0) -n myns
  - Display all clusters with uid 'myuid' in ns 'myns'  : $(basename $0) -n myns -d myuid
  - Display cluster 'c1' with uid 'myuid' in ns 'myns'  : $(basename $0) -n myns -d myuid -c c1

Sample output:
  WebLogic Cluster Status -n "" -d "" -c "":
  namespace          domain          cluster    min  max  goal  current  ready
  ---------          ------          -------    ---  ---  ----  -------  -----
  sample-domain1-ns  sample-domain1  cluster-1  0    5    2     2        2

EOF

exit $1
}

# function to display the domain cluster status in a table
# $1=ns $2=uid, pass "" to mean "any"
function clusterStatus() {
  local __ns="${1:-}"
  if [ -z "$__ns" ]; then
    # an empty ns means check all namespaces
    local __ns_filter="--all-namespaces=true"
  else
    local __ns_filter="-n $__ns"
  fi

  local __uid="${2:-}"
  local __cluster_name="${3:-}"
  local __kubernetes_cli="${4:-kubectl}"

  if ! [ -x "$(command -v ${__kubernetes_cli})" ]; then
    echo "@@Error: Kubernetes CLI '${__kubernetes_cli}' is not installed."
    exit 1
  fi
  echo
  echo "WebLogic Cluster Status -n \"${__ns:-}\" -d \"${__uid:-}\" -c \"${__cluster_name}\":"
  echo

  (
    shopt -s nullglob # causes the for loop to silently handle case where no domains match

    echo "namespace domain cluster min max goal current ready"
    echo "--------- ------ ------- --- --- ---- ------- -----"

    local __val
    for __val in \
      $($__kubernetes_cli $__ns_filter get domains.v8.weblogic.oracle \
        -o=jsonpath='{range .items[*]}{.metadata.namespace}{","}{.metadata.name}{","}{.spec.domainUID}{"\n"}{end}')
    do
      local __ns_cur=$(  echo $__val | cut -d ',' -f 1)
      local __name_cur=$(echo $__val | cut -d ',' -f 2)
      local __uid_cur=$( echo $__val | cut -d ',' -f 3)
      local __uid_cur=${uid_cur:-$__name_cur} # uid defaults to name when not set
      local __jp=''

      [ -n "$__uid" ] && [ ! "$__uid" = "$__uid_cur" ] && continue

      if [ -z "$__cluster_name" ]; then
        __jp+='{range .status.clusters[*]}'
      else
        __jp+='{range .status.clusters[?(@.clusterName=='\"$__cluster_name\"')]}'
      fi
      __jp+='{"'$__ns_cur'"}'
      __jp+='{" "}{"'$__uid_cur'"}'
      __jp+='{" "}{.clusterName}'
      __jp+='{" ~!"}{.minimumReplicas}'
      __jp+='{" ~!"}{.maximumReplicas}'
      __jp+='{" ~!"}{.replicasGoal}'
      __jp+='{" ~!"}{.replicas}'
      __jp+='{" ~!"}{.readyReplicas}'
      __jp+='{"\n"}'
      __jp+='{end}'

      $__kubernetes_cli -n "$__ns_cur" get domain.v8.weblogic.oracle "$__uid_cur" -o=jsonpath="$__jp"

    done | sed 's/~!\([0-9][0-9]*\)/\1/g'\
         | sed 's/~!/0/g' \
         | sort --version-sort

  ) | column --table

  echo
}

domainNS=
domainUID=
clusterName=
kubernetesCli=${KUBERNETES_CLI:-kubectl}

set +u
while [ ! -z ${1+x} ]; do
  case "$1" in
    -n) domainNS="$2"; shift ;;
    -d) domainUID="$2"; shift ;;
    -c) clusterName="$2"; shift ;;
    -m) kubernetesCli="$2"; shift ;;
    -h) usage 0;;
    *)  echo "@@Error: unrecognized parameter '$1', pass '-h' for help."; exit 1 ;;
  esac
  shift
done
set -u

clusterStatus "$domainNS" "$domainUID" "$clusterName" "$kubernetesCli"
