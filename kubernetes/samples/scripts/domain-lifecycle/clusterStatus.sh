#!/bin/sh
# Copyright (c) 2021, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -eu
set -o pipefail

usage() {

local kcli=${kubernetesCli:-${KUBERNETES_CLI:-kubectl}}

cat<<EOF
Usage:
  $(basename $0) [-n DomainNS] [-d DomainUID] [-c ClusterName] [-m KubernetesCli] [-h]

  This script displays a table of WebLogic Cluster statuses.

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

Status and Available columns:
  The available column is simply the value of cluster.status.conditions.Available,
  it can be True even for Failed domains.

  The status column is a calculated value:
    When domain.status.conditions.Failed is True 
      Set to 'Failed'
      Notes: - Failed is reported when there is a problem anywhere in the domain
             - Use '$kcli get -n name-space domain domain-name -o yaml' to check for errors

    Else when .metadata.observedGeneration != .status.observedGeneration
              or status.clusters.conditions.Available is not set
              or status.clusters.conditions.Completed is not set
      Set to 'Unknown'
      (Operator may not be running so domain status may be out of date.)

    Else when status.clusters.conditions.Completed is True
      Set to 'Completed'
      (All expected cluster pods are fully up to date and ready.)

    Else when status.clusters.conditions.Available is True
      Set to 'Available'
      (Sufficient number of cluster pods are ready.)

    Else
      Set to 'Unavailable'
      (Waiting for cluster pods to start.)

Sample output:
  WebLogic Cluster Status -n "" -d "" -c "":

  namespace          domain          cluster    status       available  min  max  goal  current  ready
  ---------          ------          -------    ------       ---------  ---  ---  ----  -------  -----
  sample-domain1-ns  dii-wdt         cluster-1  Completed    True       0    5    2     2        2
  sample-domain1-ns  dii-wlst        cluster-1  Completed    True       0    5    2     2        2
  sample-domain1-ns  sample-domain1  cluster-1  Failed       True       0    5    2     2        2
  sample-domain1-ns  sample-domain1  cluster-2  Unavailable  False      0    5    5     5        2

Operator version note:
  This script relies on domains that are managed by a 4.0 or later operator
  because it relies on 'v9' domain schema elements.

  If you see a message similar to
  'error: the server doesn't have a resource type "domains"',
  then this indicates no operator is installed, or that 
  a domain is managed by an older schema version.

EOF

exit $1
}

# flatten
#   helper fn that converts stdin lines of the form:
#     A B C << D E F >> << H I J >> ...
#   to one stdout line for each "<< >>" clause. E.g:
#     A B C D E F
#     A B C H I J
#   stdin lines with no "<< >>" are ignored...

flatten() {
  while read line 
  do
    __flatten $line
  done
}
__flatten() {
  local prefix=""
  while [ "${1:-}" != "<<" ]; do
    [ -z "${1:-}" ] && return
    prefix+="$1 "
    shift
  done
  while [ "${1:-}" == "<<" ]; do
    local suffix=""
    shift
    while [ "$1" != ">>" ]; do
      suffix+="$1 "
      shift
    done
    shift
    echo $prefix $suffix
  done
}

#
# condition
#   helper fn to take the thirteen column input
#   and collapse some columns into an aggregate status
#
condition() {
  while read line 
  do
    __condition $line
  done
}
__condition() {
  local gen=$1
  local ogen=$2
  local failed=$3
  local completed=${12}
  local available=${13}
  local condition="IMPOSSIBLE"
  if [ "$failed" = "True" ]; then
    condition="Failed"
  elif [ ! "$gen" = "$ogen" ] || [ "$completed" = "NotSet" ] || [ "$available" = "NotSet" ]; then
    condition="Unknown"
  elif [ "$completed" = "True" ]; then
    condition="Completed"
  elif [ "$available" = "True" ]; then
    condition="Available"
  else
    condition="Unavailable"
  fi
  echo "$4 $5 $6 $condition $available $7 $8 $9 ${10} ${11}"
}


#
# clusterStatus
#   function to display the domain cluster status in a table
#   $1=ns $2=uid $3=cluster, pass "" to mean "any"
#   $4=KUBERNETES_CLI
#
clusterStatus() {
  local __ns="${1:-}"
  if [ -z "$__ns" ]; then
    # an empty ns means check all namespaces
    local __ns_filter="--all-namespaces=true"
  else
    local __ns_filter="-n $__ns"
  fi

  local __uid="${2:-}"
  local __cluster_name="${3:-}"
  local __kubernetes_cli="${4:-${KUBERNETES_CLI:-kubectl}}"

  if ! [ -x "$(command -v ${__kubernetes_cli})" ]; then
    echo "@@Error: Kubernetes CLI '${__kubernetes_cli}' is not installed."
    exit 1
  fi
  echo
  echo "WebLogic Cluster Status -n \"${__ns:-}\" -d \"${__uid:-}\" -c \"${__cluster_name}\":"
  echo

  (
    shopt -s nullglob # causes the for loop to silently handle case where no domains match
    local _domains
    local _val

    _domains="$(
      $__kubernetes_cli $__ns_filter get domains.v9.weblogic.oracle \
        -o=jsonpath='{range .items[*]}{.metadata.namespace}{","}{.metadata.name}{","}{.spec.domainUID}{"\n"}{end}'
    )"

    echo "namespace domain cluster status available min max goal current ready"
    echo "--------- ------ ------- ------ --------- --- --- ---- ------- -----"

    for __val in $_domains
    do
      local __ns_cur=$(  echo $__val | cut -d ',' -f 1)
      local __name_cur=$(echo $__val | cut -d ',' -f 2)
      local __uid_cur=$( echo $__val | cut -d ',' -f 3)
      local __uid_cur=${uid_cur:-$__name_cur} # uid defaults to name when not set
      local __jp=''

      [ -n "$__uid" ] && [ ! "$__uid" = "$__uid_cur" ] && continue

      # construct a json path for the domain query

      __jp+='{" ~G"}{.metadata.generation}'
      __jp+='{" ~O"}{.status.observedGeneration}'
      __jp+='{" ~F"}{.status.conditions[?(@.type=="Failed")].status}'
      if [ -z "$__cluster_name" ]; then
        __jp+='{range .status.clusters[*]}'
      else
        __jp+='{range .status.clusters[?(@.clusterName=='\"$__cluster_name\"')]}'
      fi
      __jp+='{" "}{"<<"}'
      __jp+='{" "}{"'$__ns_cur'"}'
      __jp+='{" "}{"'$__uid_cur'"}'
      __jp+='{" "}{.clusterName}'
      __jp+='{" "}{"~!"}{.minimumReplicas}'
      __jp+='{" "}{"~!"}{.maximumReplicas}'
      __jp+='{" "}{"~!"}{.replicasGoal}'
      __jp+='{" "}{"~!"}{.replicas}'
      __jp+='{" "}{"~!"}{.readyReplicas}'
      __jp+='{" "}{"~C"}{.conditions[?(@.type=="Completed")].status}'
      __jp+='{" "}{"~A"}{.conditions[?(@.type=="Available")].status}'
      __jp+='{" "}{">>"}'
      __jp+='{end}'
      __jp+='{"\n"}'

      # get the values, replace empty values with sentinals or '0' as appropriate,
      # and remove all '~?' prefixes

      $__kubernetes_cli -n "$__ns_cur" get domains.v9.weblogic.oracle "$__name_cur" -o=jsonpath="$__jp" \
        | sed 's/~G /~GunknownGen /g' \
        | sed 's/~O /~OunknownOGen /g' \
        | sed 's/~F /~FNotSet /g' \
        | sed 's/~C /~CNotSet /g' \
        | sed 's/~A /~ANotSet /g' \
        | sed 's/~[A-Z]//g' \
        | sed 's/~!\([0-9][0-9]*\)/\1/g' \
        | sed 's/~!/0/g'

    done | flatten \
         | condition \
         | sort --version-sort

  ) | column --table

  echo
}

domainNS=
domainUID=
clusterName=
kubernetesCli=

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
