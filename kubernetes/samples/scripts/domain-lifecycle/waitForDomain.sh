#!/bin/bash
# Copyright (c) 2020, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -eu
set -o pipefail

timestamp() {
  date --utc '+%Y-%m-%dT%H:%M:%S'
}

tempfile() {
  mktemp /tmp/$(basename "$0").$PPID.$(timestamp).XXXXXX
}

trace() {
  echo "@@ [$(timestamp)][seconds=$SECONDS]" "$@"
}

tracen() {
  echo -n "@@ [$(timestamp)][seconds=$SECONDS]" "$@"
}

syntaxError() {
  trace "Syntax Error: \"${1:-}\". Pass '-?' for usage."
  exit 1
}

initGlobals() {
  DOMAIN_UID_DEFAULT="sample-domain1"
  DOMAIN_UID=$DOMAIN_UID_DEFAULT

  DOMAIN_RTYPE="domain.v9.weblogic.oracle"

  DOMAIN_NAMESPACE_DEFAULT="sample-domain1-ns"
  DOMAIN_NAMESPACE=$DOMAIN_NAMESPACE_DEFAULT

  KUBERNETES_CLI=${KUBERNETES_CLI:-kubectl}

  TIMEOUT_SECS_DEFAULT=1000
  TIMEOUT_SECS=$TIMEOUT_SECS_DEFAULT

  EXPECTED_STATE=0
  VERBOSE=true
  IGNORE_FAILURES=false
  REPORT_INTERVAL_SECS=120
}

usage() {

  cat << EOF

  Usage:

    $(basename $0) [-n mynamespace] [-d mydomainuid] \\
       [-p 0|Completed] [-t timeout_secs] [-q] [-i]

  Description:

    This utility script exits successfully either:
    - When a domain UID has reached zero pods (the '-p 0' default).
    - Or when the domain resource status 'Completed' condition is True
      and all domain and cluster resources are fully up-to-date
      (the '-p Completed' option).

    This script exits non-zero if:
    - A configurable timeout is reached before the target pod count
      is reached (default $TIMEOUT_SECS_DEFAULT seconds).
    - The '-p Completed' option is specified and the specified domain
      cannot be found.
    - When the domain resource '.apiVersion' is less than 9.
    - When a Failure condition is detected and '-i' is NOT specified.

  Parameters:

    -d <domain_uid> : WKO Domain UID. Defaults to '$DOMAIN_UID_DEFAULT'.

    -n <namespace>  : Kubernetes namespace.
                      Defaults to '$DOMAIN_NAMESPACE_DEFAULT'.

    -t <timeout>    : Timeout in seconds. Defaults to '$TIMEOUT_SECS_DEFAULT'.

    -p 0            : Expect no pods. This works even when the
                      domain resource is deleted. The default.

    -p Completed    : Expect domain status condition 'Completed' to be True.

    -q              : Quiet mode. Show only a count of wl pods that
                      have reached the desired criteria.

    -i              : Ignore Failure conditions.

    -?              : This help.

  Notes:

    The '-p Completed' requires:
    - Exactly the expected number of WebLogic Server pods reach a 'ready'
      state and have 'restartVersion', 'introspectVersion',
      'spec.image', and 'spec.configuration.model.auxiliaryImages.image'
      values that match their corresponding values in their domain resource.
    - The 'metadata.generation' matches 'status.observedGeneration'
      for the domain resource and each cluster resource.

    If the Completed condition state expects the number of pods to be zero,
    such as when 'domain.spec.serverStartPolicy' is set to 'Never',
    then the '-p Completed' option exits successfully when all pods for
    the given domain have exited.

EOF
}

processCommandLine() {
  while [ ! "${1:-}" = "" ]; do

    key="${1}"
    val="${2:-}"

    case "$key" in
      -q|-i|'-?') 
         shift
         ;;

      -n|-d|-t|-p)
         [ "$val" = "" ] && syntaxError "$key is missing an argument"
         shift
         shift
         ;;

      *) syntaxError "unrecognized argument '$key'" ;;
    esac

    case "$key" in
      -n) DOMAIN_NAMESPACE="${val}" ;;

      -d) DOMAIN_UID="${val}" ;;

      -t) TIMEOUT_SECS="${val}"
          case "${val}" in
            ''|*[!0-9]*) syntaxError "-t requires a positive integer but got '${val}'" ;;
          esac
          ;;

      -p) case "$val" in
            0|none) EXPECTED_STATE='0' ;;
            completed|Completed|COMPLETED) EXPECTED_STATE='Completed' ;;
            *) syntaxError '-p requires value "0" or "Completed"' ;;
          esac
          ;;

      -q) VERBOSE=false
          REPORT_INTERVAL_SECS=30
          ;;

      -i) IGNORE_FAILURES=true
          ;;

      '-?') usage
          exit 0
          ;;
    esac
  done
}

sortlist() {
  # sort a comma or space separated list
  #   - stdin input, stdout output
  #   - spaces replaced with commas
  #   - blank fields ignored
  #   - output removes any trailing comma
  #   - examples: ""->"" "c,b"->"b,c" "c,b,"->"b,c" "c b"->"b,c"
  tr ' ' '\n'    | \
  tr ',' '\n'    | \
  sort -V        | \
  xargs echo -n  | \
  tr ' ' ','
}
sortAIImages() {
  # sort "aiimages=;im2,im1;" field assuming comma or space sep list
  #   - stdin input, stdout output
  #   - spaces replaced with commas
  #   - input ignores trailing comma, output removes any trailing comma
  #   - examples: see sortAIImagesUnitTest()
  while read line
  do
    echo -n "$line" | sed 's/\(^.*aiimages=;\).*/\1/'
    echo -n "$line" | sed 's/.*aiimages=;\([^;]*\).*/\1/' | sortlist
    echo "$line"    | sed 's/.*aiimages=;[^;]*\(;.*\)/\1/'
  done
}
_sortAIImagesUnitTest() {
  local res=$(echo "$1" | sortAIImages)
  if [ ! "$res" = "$2" ]; then
    echo "unit test fail"
    echo " input ='$1'"
    echo " expect='$2'"
    echo " actual='$res'"
    exit 1
  fi
}
sortAIImagesUnitTest() {
  _sortAIImagesUnitTest "foo=;bar; aiimages=;c,b; bar=;foo;"   "foo=;bar; aiimages=;b,c; bar=;foo;"
  _sortAIImagesUnitTest "foo=;bar; aiimages=; c,b,; bar=;foo;" "foo=;bar; aiimages=;b,c; bar=;foo;"
  _sortAIImagesUnitTest "foo=;bar; aiimages=;; bar=;foo;"      "foo=;bar; aiimages=;; bar=;foo;"
  _sortAIImagesUnitTest "foo=;bar; aiimages=;a ; bar=;foo;"    "foo=;bar; aiimages=;a; bar=;foo;"
  _sortAIImagesUnitTest "aiimages=;c b; bar=;foo; foo=;bar;"   "aiimages=;b,c; bar=;foo; foo=;bar;"
  _sortAIImagesUnitTest "bar=;foo; foo=;bar; aiimages=; c b ;" "bar=;foo; foo=;bar; aiimages=;b,c;"
  _sortAIImagesUnitTest "aiimages=;;"                          "aiimages=;;"
  _sortAIImagesUnitTest "aiimages=; ;"                         "aiimages=;;"
  _sortAIImagesUnitTest "aiimages=;,,;"                        "aiimages=;;"
  return 0
}
sortAIImagesUnitTest


getDomainValue() {
  # get domain value specified by $2 and put in env var named by $1
  #   - if get fails, and global EXPECTED_STATE is "Completed", then echo an Error and exit script non-zero
  #   - example: getDomainValue DOM_VERSION '.spec.introspectVersion'
  local __retvar=$1
  local ljpath="{$2}"
  local attvalue
  set +e
  attvalue=$(${KUBERNETES_CLI} -n ${DOMAIN_NAMESPACE} get ${DOMAIN_RTYPE} ${DOMAIN_UID} -o=jsonpath="$ljpath" 2>&1)
  if [ $? -ne 0 ]; then
    if [ "$EXPECTED_STATE" = "Completed" ]; then
      trace "Error: Could not obtain '$ljpath' from ${DOMAIN_RTYPE} '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}'. Is your domain resource deployed? Err='$attvalue'"
      exit 1
    else
      # We're waiting for 0 pods - domain might have been deleted, and it doesn't matter what the value is
      attvalue=''
    fi
  fi
  # echo "DEBUG ${KUBERNETES_CLI} -n ${DOMAIN_NAMESPACE} get ${DOMAIN_RTYPE} ${DOMAIN_UID} -o=jsonpath=\"$ljpath\" 2>&1"
  # echo "DEBUG   = '$attvalue'"
  eval "$__retvar='$attvalue'"
  set -e
}

getClusterValue() {
  # get cluster value specified by $3 from cluster $1 and put in env var named by $2
  #   - if get fails, and global EXPECTED_STATE is "Completed", then echo an Error and exit script non-zero
  #   - example: getClusterValue my-cluster cgen '.metadata.generation'
  local cname="$1"
  local __retvar=$2
  local ljpath="{$3}"
  local attvalue
  set +e
  attvalue=$(${KUBERNETES_CLI} -n ${DOMAIN_NAMESPACE} get cluster ${cname} -o=jsonpath="$ljpath" 2>&1)
  if [ $? -ne 0 ]; then
    if [ "$EXPECTED_STATE" = "Completed" ]; then
      trace "Error: Could not obtain '$ljpath' from cluster '${cname}' in namespace '${DOMAIN_NAMESPACE}'. Is your cluster resource deployed? Err='$attvalue'"
      exit 1
    else
      # We're waiting for 0 pods - cluster might have been deleted, and it doesn't matter what the value is
      attvalue=''
    fi
  fi
  # echo "DEBUG ${KUBERNETES_CLI} -n ${DOMAIN_NAMESPACE} get cluster ${cname} -o=jsonpath=\"$ljpath\" 2>&1"
  # echo "DEBUG   = '$attvalue'"
  eval "$__retvar='$attvalue'"
  set -e
}

getDomainAIImages() {
  # get list of domain auxiliary images (if any) and place result in the env var named by $1
  #   - if EXPECTED_STATE is "Completed" and get fails, then echo an Error and exit script non-zero
  #   - result is a sorted comma separated list
  local attvalue
  local __retvar=$1
  set +e
  attvalue=$(
    ${KUBERNETES_CLI} \
      get ${DOMAIN_RTYPE} ${DOMAIN_UID} \
      -n ${DOMAIN_NAMESPACE} \
      -o=jsonpath="{range .spec.configuration.model.auxiliaryImages[*]}{.image}{','}{end}" \
      2>&1
  )
  if [ $? -ne 0 ]; then
    if [ "$EXPECTED_STATE" = "Completed" ]; then
      trace "Error: Could not obtain '.spec.configuration.model.auxiliaryImages' from '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}'. Is your domain resource deployed? Err='$attvalue'"
      exit 1
    else
      # We're waiting for 0 pods - it doesn't matter what the value is
      attvalue='':
    fi
  fi
  set -e
  attvalue=$(echo "$attvalue" | sortlist)
  eval "$__retvar='$attvalue'"
}

getDomainInfo() {
  # Get info about the current domain and populate it into global variables
  #
  # If any its gets fail then the following implicitly occurs:
  #   - we assume that the domain resource was not found
  #   - silently set global to "" if EXPECTED_STATE = '0'
  #   - "exit 1" and report an error if EXPECTED_STATE = 'Completed'

  getDomainValue    domain_info_goal_RV_current        ".spec.restartVersion"
  getDomainValue    domain_info_goal_IV_current        ".spec.introspectVersion"
  getDomainValue    domain_info_goal_image_current     ".spec.image"
  getDomainValue    domain_info_clusters               ".spec.clusters"
  getDomainAIImages domain_info_goal_aiimages_current
  getDomainValue    domain_info_api_version            ".apiVersion"
  getDomainValue    domain_info_condition_failed_str   ".status.conditions[?(@.type==\"Failed\")]" # has full failure messages, if any
  getDomainValue    domain_info_observed_generation    ".status.observedGeneration"
  getDomainValue    domain_info_condition_completed    ".status.conditions[?(@.type==\"Completed\")].status" # "True" when complete

  domain_info_clusters=$( echo "$domain_info_clusters" | sed 's/"name"//g' | tr -d '[]{}:' | sortlist | sed 's/,/ /') # convert to sorted space separated list

  # gather observed and goal generation for each cluster:
  cl_info_observed_generations=""
  cl_info_goal_generations=""
  local lgen
  local cl
  for cl in $domain_info_clusters EOL; do
    [ "$cl" = "EOL" ] && break
    getClusterValue $cl lgen ".status.observedGeneration"
    [ -z "$lgen" ] && lgen="NOTSET"
    cl_info_observed_generations="$cl_info_observed_generations $lgen"

    getClusterValue $cl lgen ".metadata.generation"
    cl_info_goal_generations="$cl_info_goal_generations $lgen"
  done

  # Get domain goal generation last in case gen changed while checking the above...
  getDomainValue    domain_info_goal_generation        ".metadata.generation"

  all_goals_current="${domain_info_goal_RV_current}
${domain_info_goal_IV_current}
${domain_info_goal_image_current}
${domain_info_goal_aiimages_current}
${domain_info_goal_generation}
${domain_info_observed_generation}
${cl_info_goal_generations}
${cl_info_observed_generations}
${domain_info_condition_failed_str}
${domain_info_clusters}
^M"
}

getPodInfo() {
  # get interesting info about each pod in DOMAIN_NAMESPACE with DOMAIN_UID
  # output: stdout in the form "var=;value; var2=;value; ..." (one line per pod)
  #
  # NOTE: ljpath must correspond with the regex below and column headers below 

  local ljpath=''
  ljpath+='{range .items[*]}'
    ljpath+='{" name="}'
    ljpath+='{";"}{.metadata.name}{";"}'
    ljpath+='{" domainRestartVersion="}'
    ljpath+='{";"}{.metadata.labels.weblogic\.domainRestartVersion}{";"}'
    ljpath+='{" introspectVersion="}'
    ljpath+='{";"}{.metadata.labels.weblogic\.introspectVersion}{";"}'
    ljpath+='{" image="}'
    ljpath+='{";"}{.spec.containers[?(@.name=="weblogic-server")].image}{";"}'
    ljpath+='{" aiimages="}'
    ljpath+='{";"}{.spec.initContainers[?(@.command[0]=="/weblogic-operator/scripts/auxImage.sh")].image}{";"}'
    ljpath+='{" ready="}'
    ljpath+='{";"}{.status.containerStatuses[?(@.name=="weblogic-server")].ready}{";"}'
    ljpath+='{" phase="}'
    ljpath+='{";"}{.status.phase}{";"}'
    ljpath+='{"\n"}'
  ljpath+='{end}'

  # get introspector pod, if any:
  ${KUBERNETES_CLI} -n ${DOMAIN_NAMESPACE} get pods \
          -l job-name=${DOMAIN_UID}-introspector \
          -o=jsonpath="$ljpath" \
          | sortAIImages

  # get wl server pods, if any:
  ${KUBERNETES_CLI} -n ${DOMAIN_NAMESPACE} get pods \
          -l weblogic.serverName,weblogic.domainUID="${DOMAIN_UID}" \
          -o=jsonpath="$ljpath" \
          | sortAIImages
}

# report timeout setting, criteria, generation, observedGeneration, Failed state, and Completed state
# this information is gathered by 'getDomainInfo' and 'processCommandLine'
reportBasics() {
  if [ "$EXPECTED_STATE" = "0" ]; then
    trace "Info: Waiting up to $TIMEOUT_SECS seconds for there to be no (0) WebLogic Server pods that match the following criteria:"
    trace "Info:   namespace='$DOMAIN_NAMESPACE' domainUID='$DOMAIN_UID'"
  else
    trace "Info: Waiting up to $TIMEOUT_SECS seconds for domain status condition 'Completed' to become 'True'" \
          " and '.metadata.generation' to match '.status.observedGeneration' for the domain and each of its clusters."
    trace "Info: WebLogic Server pods, if not shutting down, should reach the following criteria:"
    trace "Info:   namespace='$DOMAIN_NAMESPACE'"
    trace "Info:   domainUID='$DOMAIN_UID'"
    trace "Info:   ready='true'"
    trace "Info:   image='$domain_info_goal_image_current'"
    trace "Info:   auxiliaryImages='$domain_info_goal_aiimages_current'"
    trace "Info:   domainRestartVersion='$domain_info_goal_RV_current'"
    trace "Info:   introspectVersion='$domain_info_goal_IV_current'"
    trace "Info: Generations:"
    trace "Info:   domain metadata.generation='$domain_info_goal_generation'"
    trace "Info:   domain.status.observedGeneration='$domain_info_observed_generation'"
    trace "Info:   clusters='$domain_info_clusters'"
    trace "Info:     cluster.metadata.generation(s)='$cl_info_goal_generations'"
    trace "Info:     cluster.status.observedGeneration(s)='$cl_info_observed_generations'"
    trace "Info: Completed='$domain_info_condition_completed'"
  fi
  trace "Info: Failure conditions (if any): '${domain_info_condition_failed_str}'."
  echo
}

main() {
  local pod_count=0
  local reported="false"
  local last_report_secs=$SECONDS
  local all_goals_orig="--not-known--"
  local is_done="false"
  local pod_info_orig=""
  local pod_info_cur=""

  # Loop until we reach the desired pod count for pods at the desired restart version,
  # introspect version, and image -- or until we reach the timeout.

  while [ 1 -eq 1 ]; do

    # Populate info about current domain into globals.
    #   If any gets fail then the following implicitly occurs:
    #   - we assume that the domain resource was not found
    #   - silently set the global to "" when EXPECTED_STATE = '0'
    #   - "exit 1" and report an error when EXPECTED_STATE = 'Completed'

    getDomainInfo

    #
    # This script only works in v9+, so let's check the 'domain_info_api_version' global
    #

    local version_str=${domain_info_api_version:-weblogic.oracle/v9}  # if domain resource is missing, domain_info_api_version is blank
    local version_num=$(echo "$version_str" | sed 's/[^0-9]//g')
    if [ $version_num -lt 9 ]; then
      trace "Error: Unexpected domain resource '.apiVersion' of '$domain_info_api_version'. Expected version 9 or higher."
      exit 1
    fi

    #
    # Force new reporting for the rare case where the domain unexpectedly changes while we're looping
    #

    if [ ! "$all_goals_orig" = "$all_goals_current" ]
    then
      [ "$reported" = "true" ] && echo
      reported="false"
      all_goals_orig="$all_goals_current"
    fi

    # Get the current number of pods that match target criteria and also
    # determine if we have reached our goal.
    #
    #   If EXPECTED_STATE = 'Completed'
    #    - get the number of ready pods that match the target image, restart version, etc.
    #    - we have reached our goal if the Completed condition is true
    #      AND .metadata.generation equals .status.conditions.observedGeneration
    #
    #   If EXPECTED_STATE = '0'
    #    - get the current number of pods regardless of their
    #      restart version, introspect version, image, etc.
    #    - we have reached our goal if the pod count is 0
    #

    is_done="false"

    if [ "$EXPECTED_STATE" = "0" ]; then

      pod_count=$( getPodInfo | wc -l )

      [ $pod_count -eq 0 ] && is_done="true"

    else

      # NOTE: this regex must correspond with the jpath in the 'getPodInfo' function
      local regex="domainRestartVersion=;$domain_info_goal_RV_current;"
      regex+=" introspectVersion=;$domain_info_goal_IV_current;"
      regex+=" image=;$domain_info_goal_image_current;"
      regex+=" aiimages=;$domain_info_goal_aiimages_current;"
      regex+=" ready=;true;"

      set +e # disable error checks as grep returns non-zero when it finds nothing (sigh)
      pod_count=$( getPodInfo | grep "$regex" | wc -l )
      set -e

      [ "$domain_info_condition_completed" = "True" ] \
        && [ "$domain_info_goal_generation" = "$domain_info_observed_generation" ] \
        && [ "$cl_info_goal_generations" = "$cl_info_observed_generations" ] \
        && is_done="true"

    fi

    #
    # Report the current state to stdout.
    #

    if [ "$VERBOSE" = "false" ]; then

      # "quiet" reporting:
      # - show the goal criteria once plus any time they happen to change
      # - append the count of matching pods to a single line "... = 3 3 2" when it changes
      #   or when a REPORT_INTERVAL_SECS has passed

      if [ "$reported" = "false" ]; then

        reportBasics # report goal criteria, generation, Failed state, and Completed state

        tracen "Info: Current pods that match the above criteria ="
        echo -n " $pod_count"
        reported="true"
        last_report_secs=$SECONDS

      elif [ $((SECONDS - last_report_secs)) -gt $REPORT_INTERVAL_SECS ] \
           || [ "$is_done" = "true" ]; then

        echo -n " $pod_count"
        last_report_secs=$SECONDS

      fi
    else

      # verbose reporting:
      # - show the goal criteria
      # - show the state of each pod in table form
      # - output above again when any pod state changes or 'REPORT_INTERVAL_SECS' is exceeded

      pod_info_cur=$(getPodInfo) || exit 1

      if [ "$pod_info_cur" != "$pod_info_orig" ] \
         || [ "$reported" = "false" ] \
         || [ $((SECONDS - last_report_secs)) -gt $REPORT_INTERVAL_SECS ] \
         || [ "$is_done" = "true" ]; then

        if [ "$reported" = "false" ]; then
          echo
          reportBasics # report goal and criteria, generation, Failed state, and Completed state
          echo
          reported="true"
        fi

        trace "Info: '$pod_count' WebLogic Server pods currently match all criteria."
        trace "Info: Introspector and WebLogic Server pods with same namespace and domain-uid:"
        echo

        # print results as a table:
        #   convert each "var=val;" in $pod_info_cur to just "'val'".
        #   (the single quotes around val are necessary so that 'column -t'
        #    doesn't get confused by var entries that have empty values)
        (
          # column headers must line up with the jpath in getPodInfo
          echo "NAME RVER IVER IMAGE AIIMAGES READY PHASE"
          echo "---- ---- ---- ----- -------- ----- -----"
          cat <<< "$pod_info_cur" | sed "s|[^ ]*=;\([^;]*\);|'\1'|g"
        ) | column -t
        echo

        pod_info_orig="$pod_info_cur"
        last_report_secs=$SECONDS
      fi
    fi

    #
    # Exit 1 if domain resource is reporting a failure and "IGNORE_FAILURES" is "false" (the default)
    #
    if [ "${IGNORE_FAILURES}" = "false" ] && [ ! -z "${domain_info_condition_failed_str}" ]; then
      echo
      trace "Error: The domain resource has a failure condition: '${domain_info_condition_failed_str}'. If you want this script to ignore failure conditions and keep trying regardless, then specify '-i' on the commmand line."
      exit 1
    fi

    #
    # Exit 0 if we've reached our goal
    #
    if [ "$is_done" = "true" ]; then
      echo
      trace "Info: Success!"
      exit 0
    fi

    #
    # Exit 1 if too much time has passed.
    #
    if [ $SECONDS -ge $TIMEOUT_SECS ]; then
      echo
      reportBasics
      trace "Error: Timeout after waiting more than $TIMEOUT_SECS seconds."
      exit 1
    fi

    sleep 1
  done
}

initGlobals
processCommandLine "${@}"
main
