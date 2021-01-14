# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

function timestamp() {
  date --utc '+%Y-%m-%dT%H:%M:%S'
}

function trace() {
  echo @@ "[$(timestamp)]" "[$(basename $0):${BASH_LINENO[0]}]:" "$@"
}

function tracen() {
  echo -n @@ "[$(timestamp)]" "[$(basename $0):${BASH_LINENO[0]}]:" "$@"
}

# diefast
#
# Use 'diefast' to touch the '/tmp/diefast' file in all WL pods. This will
# cause them to use an unsafe (fast) shutdown when they're deleted/rolled.

function diefast() {
  kubectl -n ${DOMAIN_NAMESPACE:-sample-domain1-ns} get pods -l weblogic.serverName \
    -o=jsonpath='{range .items[*]}{.metadata.namespace}{" "}{.metadata.name}{"\n"}' \
    | awk '{ system("set -x ; kubectl -n " $1 " exec " $2 " touch /tmp/diefast") }'
}

function get_service_name() {
  # $1 is service name
  echo $(tr [A-Z_] [a-z-] <<< $1)
}

function get_service_yaml() {
  # $1 is service name
  echo "$WORKDIR/ingresses/traefik-ingress-$(get_service_name $1).yaml"
}

function get_kube_address() {
  # kubectl cluster-info | grep KubeDNS | sed 's;^.*//;;' | sed 's;:.*$;;'
  # This is the heuristic used by the integration test framework:
  echo ${K8S_NODEPORT_HOST:-$(hostname)}
}

function get_sample_host() {
  # $1 is service name
  tr [A-Z_] [a-z-] <<< $1.mii-sample.org
}

function curl_timeout_parms() {
  local curl_parms="--connect-timeout 5"
  curl_parms+=" --max-time 20"        # max seconds for each try
  # don't bother retrying - we will retry externally because
  #                         connection refusals don't retry
  # curl_parms+=" --retry 5"            # retry up to 5 times
  # curl_parms+=" --retry-delay 0"      # disable exponential backoff
  curl_parms+=" --retry-max-time 130" # total seconds before giving up
  echo "$curl_parms"
}

function get_curl_command() {
  # $1 is service name
  echo "curl -s -S $(curl_timeout_parms) -H 'host: $(get_sample_host $1)'"
}

# testapp
#
# Use 'testapp internal|traefik cluster-1|cluster-2 somestring' to invoke the test
# WebLogic cluster's jsp app and check that the given string is
# is reflected there-in.
#
#   "internal" invokes curl on the admin server
#   "traefik" invokes curl locally through the traefik node port
#
# For example, 'testapp internal "Hello World!"'.

function testapp() {

  # note: we retry 5 times in case services, etc need more time to come up
  #       curl's internal retry doesn't actually retry if there's a 'connect failure'

  local num_tries=0
  local traefik_nodeport=''

  while [ 1 = 1 ] 
  do

    domain_uid=${DOMAIN_UID:-sample-domain1}
    if [ "$1" = "internal" ]; then
      local cluster_service_name=$(get_service_name $domain_uid-cluster-$2)
      local admin_service_name=$(get_service_name $domain_uid-admin-server)
      local ns=${DOMAIN_NAMESPACE:-sample-domain1-ns}
      local command="kubectl exec -n $ns $admin_service_name -- bash -c \"curl -s -S $(curl_timeout_parms) http://$cluster_service_name:8001/myapp_war/index.jsp\""

    elif [ "$1" = "traefik" ]; then
      if [ -z "$traefik_nodeport" ]; then
        echo "@@ Info: Obtaining traefik nodeport by calling:"
        cat<<EOF
          kubectl get svc $TRAEFIK_NAME --namespace $TRAEFIK_NAMESPACE -o=jsonpath='{.spec.ports[?(@.name=="web")].nodePort}'
EOF
        traefik_nodeport=$(kubectl get svc $TRAEFIK_NAME --namespace $TRAEFIK_NAMESPACE -o=jsonpath='{.spec.ports[?(@.name=="web")].nodePort}')
        if [ -z "$traefik_nodeport" ]; then
          echo "@@ Error: Could not obtain traefik nodeport."
          return 1
        fi
      fi
      local command="$(get_curl_command ${DOMAIN_UID:-sample-domain1}-cluster-$2) http://$(get_kube_address):${traefik_nodeport}/myapp_war/index.jsp"

    else
      echo "@@ Error: Unexpected value for '$1' - must be 'traefik' or 'internal'"
      return 1

    fi

    target_file=$WORKDIR/test-out/$PPID.$(printf "%3.3u" ${COMMAND_OUTFILE_COUNT:-0}).$(timestamp).testapp.curl.$1.out

    echo -n "@@ Info: Searching for '$3' in '$1' mode curl app invoke of cluster '$2' using '$command'. Output file '$target_file'."

    set +e
    bash -c "$command" > $target_file 2>&1
    set -e

    # use "cat & sed" instead of "grep" as grep exits with an error when it doesn't find anything

    local before=$(cat $target_file)
    local after=$(cat $target_file | sed "s/$3/ADIFFERENTVALUE/g")

    if [ "$before" = "$after" ]; then
      echo
      echo "@@ Error: '$3' not found in app response for command '$command'. Contents of response file '$target_file':"
      cat $target_file

      num_tries=$((num_tries + 1))
      [ $num_tries -gt 15 ] && return 1
      echo "@@ Info: Curl command failed on try number '$num_tries'. Sleeping 5 seconds and retrying."
      sleep 5

    else
      echo ".. Success!"
      return 0
    fi

  done
}


# doCommand
#
#  runs the given command, potentially redirecting it to a file
#
#  if DRY_RUN is set to 'true'
#    - echos command to stdout prepended with 'dryrun: '
#
#  if DRY_RUN is not set to 'true'
#    - if first parameter is -c
#      - shift, and runs command "$@" in foreground
#    - if first parameter is ! -c
#      - redirects dommand "$@" stdout/stderr to a file something like
#        '$WORKDIR/test-out/$PPID.$COUNT.$(timestamp).$(basename $(printf $1)).out'
#      - prints out an Info with the name of the command and the location of this file
#      - follows info with 'dots' while it waits for command to complete
#    - if command fails, prints out an Error and exits non-zero
#    - if command succeeds, exits zero
#
#  This function expects -e, -u, and -o pipefail to be set. If not, it returns 1.
#

function doCommand() {
  if [ "${SHELLOPTS/errexit}" = "${SHELLOPTS}" ]; then
    trace "Error: The doCommand script expects that 'set -e' was already called."
    return 1
  fi
  if [ "${SHELLOPTS/pipefail}" = "${SHELLOPTS}" ]; then
    trace "Error: The doCommand script expects that 'set -o pipefail' was already called."
    return 1
  fi
  if [ "${SHELLOPTS/nounset}" = "${SHELLOPTS}" ]; then
    trace "Error: The doCommand script expects that 'set -u' was already called."
    return 1
  fi

  local redirect=true
  if [ "${1:-}" = "-c" ]; then
    redirect=false 
    shift
  fi

  local command="$@"

  if [ "${DRY_RUN:-}" = "true" ]; then
    echo "dryrun: $command"
    return
  fi

  if [ "$redirect" = "false" ]; then
    trace "Info: Running command '$command':"
    eval $command
    return $?
  fi

  # COMMAND_OUTFILE_COUNT is also used by other functions in this file
  COMMAND_OUTFILE_COUNT=${COMMAND_OUTFILE_COUNT:-0}
  COMMAND_OUTFILE_COUNT=$((COMMAND_OUTFILE_COUNT + 1))

  mkdir -p $WORKDIR/test-out

  local out_file="$WORKDIR/test-out/$PPID.$(printf "%3.3u" $COMMAND_OUTFILE_COUNT).$(timestamp).$(basename $(printf $1)).out"
  tracen Info: Running command "'$command'," "output='$out_file'."
  printdots_start

  set +e
  eval $command > $out_file 2>&1
  local err_code=$?
  if [ $err_code -ne 0 ]; then
    echo
    trace "Error: Error running command '$command', output='$out_file'. Output contains:"
    cat $out_file
    trace "Error: Error running command '$command', output='$out_file'. Output dumped above."
  fi
  printdots_end
  set -e

  return $err_code
}
