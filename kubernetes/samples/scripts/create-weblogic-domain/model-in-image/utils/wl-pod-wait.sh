#!/bin/bash
# Copyright (c) 2020, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -eu
set -o pipefail

DOMAIN_UID="sample-domain1"
DOMAIN_NAMESPACE="sample-domain1-ns"
timeout_secs=1000

usage() {

  cat << EOF

  Usage:

    $(basename $0) [-n mynamespace] [-d mydomainuid] \\
       [-p 0|Completed] [-t timeout_secs] [-q]

  Description:

    This utility script exits successfully when a domain UID
    has reached zero pods (the default), or when the
    domain resource status 'Completed' condition is True.

    A domain resource is completed when exactly the expected
    number of WebLogic Server pods reach a 'ready' state
    and have 'restartVersion', 'introspectVersion',
    'spec.image', and 'spec.configuration.model.auxiliaryImages.image'
    values that match their corresponding values in their domain resource.
    If the Completed condition state expects the number of pods to be zero,
    such as when 'domain.spec.serverStartPolicy' is set to 'Never',
    then the '-p Completed' option exits successfully when all pods for
    the given domain have exited.

    This script exits non-zero if:
    - A configurable timeout is reached before the target pod count
      is reached (default $timeout_secs seconds).
    - The '-p Completed' option is specified and the specified domain
      cannot be found.
    - When the domain resource '.apiVersion' is less than 9.
    - When a Failure condition is detected and '-i' is NOT specified.

  Parameters:

    -d <domain_uid> : WKO Domain UID. Defaults to '$DOMAIN_UID'.

    -n <namespace>  : Kubernetes namespace.
                      Defaults to '$DOMAIN_NAMESPACE'.

    -t <timeout>    : Timeout in seconds. Defaults to '$timeout_secs'.

    -p 0            : Expect no pods. This works even when the
                      domain resource is deleted. The default.

    -p Completed    : Expect domain status condition 'Completed' to be True.

    -q              : Quiet mode. Show only a count of wl pods that
                      have reached the desired criteria.

    -i              : Ignore Failure conditions.

    -?              : This help.

EOF
}

syntaxError() {
  echo "@@ Syntax Error: \"${1:-}\". Pass '-?' for usage."
  exit 1
}

expected=0
verbose=true
ignore_failures=false
report_interval=120

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

    -t) timeout_secs="${val}"
        case "${val}" in
          ''|*[!0-9]*) syntaxError "-t requires a positive integer but got '${val}'" ;;
        esac
        ;;

    -p) case "$val" in
          0|none) expected='0' ;;
          completed|Completed|COMPLETED) expected='Completed' ;;
          *) syntaxError '-p requires value "0" or "Completed"' ;;
        esac
        ;;

    -q) verbose=false
        report_interval=30
        ;;

    -i) ignore_failures=true
        ;;

    '-?') usage
        exit 0
        ;;
  esac
done

timestamp() {
  date --utc '+%Y-%m-%dT%H:%M:%S'
}

tempfile() {
  mktemp /tmp/$(basename "$0").$PPID.$(timestamp).XXXXXX
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
  #   - if get fails, and global expected is "Completed", then echo an Error and exit script non-zero
  #   - example: getDomainValue DOM_VERSION '.spec.introspectVersion'
  local __retvar=$1
  local ljpath="{$2}"
  local attvalue
  set +e
  attvalue=$(kubectl -n ${DOMAIN_NAMESPACE} get domain ${DOMAIN_UID} -o=jsonpath="$ljpath" 2>&1)
  if [ $? -ne 0 ]; then
    if [ "$expected" = "Completed" ]; then
      echo "@@ Error: Could not obtain '$1' from '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}'. Is your domain resource deployed? Err='$attvalue'"
      exit 1
    else
      # We're waiting for 0 pods - domain might have been deleted, and it doesn't matter what the value is
      attvalue=''
    fi
  fi
  # echo "DEBUG kubectl -n ${DOMAIN_NAMESPACE} get domain ${DOMAIN_UID} -o=jsonpath=\"$ljpath\" 2>&1"
  # echo "DEBUG   = '$attvalue'"
  eval "$__retvar='$attvalue'"
  set -e
}

getDomainAIImages() {
  # get list of domain auxiliary images (if any) and place result in the env var named by $1
  #   - if expected is "Completed" and get fails, then echo an Error and exit script non-zero
  #   - result is a sorted comma separated list
  local attvalue
  local __retvar=$1
  set +e
  attvalue=$(
    kubectl \
      get domain ${DOMAIN_UID} \
      -n ${DOMAIN_NAMESPACE} \
      -o=jsonpath="{range .spec.configuration.model.auxiliaryImages[*]}{.image}{','}{end}" \
      2>&1
  )
  if [ $? -ne 0 ]; then
    if [ "$expected" = "Completed" ]; then
      echo "@@ Error: Could not obtain '.spec.configuration.model.auxiliaryImages' from '${DOMAIN_UID}' in namespace '${DOMAIN_NAMESPACE}'. Is your domain resource deployed? Err='$attvalue'"
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
  #   - silently set global to "" if expected = '0'
  #   - "exit 1" and report an error if expected = 'Completed'

  getDomainValue    goal_RV_current        ".spec.restartVersion"
  getDomainValue    goal_IV_current        ".spec.introspectVersion"
  getDomainValue    goal_image_current     ".spec.image"
  getDomainAIImages goal_aiimages_current
  getDomainValue    goal_k8s_generation    ".metadata.generation"
  getDomainValue    observed_generation    ".status.observedGeneration"
  getDomainValue    api_version            ".apiVersion"
  getDomainValue    condition_failed_str   ".status.conditions[?(@.type==\"Failed\")]" # has full failure messages, if any
  getDomainValue    condition_completed    ".status.conditions[?(@.type==\"Completed\")].status" # "True" when complete

  all_goals_current="${goal_RV_current}
${goal_IV_current}
${goal_image_current}
${goal_aiimages_current}
${goal_k8s_generation}
${observed_generation}
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

  kubectl -n ${DOMAIN_NAMESPACE} get pods \
          -l weblogic.serverName,weblogic.domainUID="${DOMAIN_UID}" \
          -o=jsonpath="$ljpath" \
          | sortAIImages
}

tmpfileorig=$(tempfile)
tmpfilecur=$(tempfile)

trap "rm -f $tmpfileorig $tmpfilecur" EXIT

pod_count=0
reported="false"
last_report_secs=$SECONDS
all_goals_orig="--not-known--"


# Loop until we reach the desired pod count for pods at the desired restart version,
# introspect version, and image -- or until we reach the timeout.

while [ 1 -eq 1 ]; do

  # Populate info about current domain into globals.
  #   If any gets fail then the following implicitly occurs:
  #   - we assume that the domain resource was not found
  #   - silently set the global to "" when expected = '0'
  #   - "exit 1" and report an error when expected = 'Completed'

  getDomainInfo

  #
  # This script only works in v9+, so let's check.
  #

  version_str=${api_version:-weblogic.oracle/v9}  # if domain resource is missing, api_version is blank
  version_num=$(echo "$version_str" | sed 's/[^0-9]//g')
  if [ $version_num -lt 9 ]; then
    echo "@@ [$(timestamp)][seconds=$SECONDS] Error: Unexpected domain resource '.apiVersion' of '$api_version'. Expected version 9 or higher."
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
  #   If expected = 'Completed'
  #    - get the number of ready pods that match the target image, restart version, etc.
  #    - we have reached our goal if the Completed condition is true
  #      AND .metadata.generation equals .status.conditions.observedGeneration
  #
  #   If expected = '0'
  #    - get the current number of pods regardless of their
  #      restart version, introspect version, image, etc.
  #    - we have reached our goal if the pod count is 0
  #

  is_done="false"

  if [ "$expected" = "0" ]; then
    lead_string="Waiting up to $timeout_secs seconds for there to be no (0) WebLogic Server pods that match the following criteria:"

    criteria="namespace='$DOMAIN_NAMESPACE' domainUID='$DOMAIN_UID'"

    pod_count=$( getPodInfo | wc -l )

    [ $pod_count -eq 0 ] && is_done="true"

  else
    lead_string="Waiting up to $timeout_secs seconds for domain status condition 'Completed' to become 'True'"
    lead_string+=" and '.metadata.generation' to match '.status.observedGeneration'."
    lead_string+=" Current values are '$condition_completed', '$goal_k8s_generation', and '$observed_generation' respectively."
    lead_string+=" WebLogic Server pods, if not shutting down, should reach the following criteria:"

    criteria=""
    criteria+=" namespace='$DOMAIN_NAMESPACE'"
    criteria+=" domainUID='$DOMAIN_UID'"
    criteria+=" ready='true'"
    criteria+=" image='$goal_image_current'"
    criteria+=" auxiliaryImages='$goal_aiimages_current'"
    criteria+=" domainRestartVersion='$goal_RV_current'"
    criteria+=" introspectVersion='$goal_IV_current'"

    # NOTE: this regex must correspond with the jpath in the 'getPodInfo' function
    regex="domainRestartVersion=;$goal_RV_current;"
    regex+=" introspectVersion=;$goal_IV_current;"
    regex+=" image=;$goal_image_current;"
    regex+=" aiimages=;$goal_aiimages_current;"
    regex+=" ready=;true;"

    set +e # disable error checks as grep returns non-zero when it finds nothing (sigh)
    pod_count=$( getPodInfo | grep "$regex" | wc -l )
    set -e

    [ "$condition_completed" = "True" ] && [ "$goal_k8s_generation" = "$observed_generation" ] && is_done="true"

  fi

  #
  # Report the current state to stdout.
  #

  if [ "$verbose" = "false" ]; then

    # "quiet" reporting:
    # - show the goal criteria once plus any time they happen to change
    # - append the count of matching pods to a single line "... = 3 3 2" when it changes
    #   or when a report_interval has passed

    if [ "$reported" = "false" ]; then
      echo "@@ [$(timestamp)][seconds=$SECONDS] Info: $lead_string"
      for criterion in $criteria; do
        echo "@@ [$(timestamp)][seconds=$SECONDS] Info:   $criterion"
      done
      echo -n "@@ [$(timestamp)][seconds=$SECONDS] Info: Current pods that match the above criteria ="
      echo -n " $pod_count"
      reported="true"
      last_report_secs=$SECONDS

    elif [ $((SECONDS - last_report_secs)) -gt $report_interval ] \
         || [ "$is_done" = "true" ]; then

      echo -n " $pod_count"
      last_report_secs=$SECONDS

    fi
  else

    # verbose reporting:
    # - show the goal criteria
    # - show the state of each pod in table form
    # - output above again when any pod state changes or 'report_interval' is exceeded

    getPodInfo > $tmpfilecur

    set +e
    diff -q $tmpfilecur $tmpfileorig 2>&1 > /dev/null
    diff_res=$?
    set -e
    if [ $diff_res -ne 0 ] \
       || [ "$reported" = "false" ] \
       || [ $((SECONDS - last_report_secs)) -gt $report_interval ] \
       || [ "$is_done" = "true" ]; then

      if [ "$reported" = "false" ]; then
        echo
        echo "@@ [$(timestamp)][seconds=$SECONDS] Info: $lead_string"
        for criterion in $criteria; do
          echo "@@ [$(timestamp)][seconds=$SECONDS] Info:   $criterion"
        done
        echo
        reported="true"
      fi

      echo "@@ [$(timestamp)][seconds=$SECONDS] Info: '$pod_count' WebLogic Server pods currently match all criteria."
      echo "@@ [$(timestamp)][seconds=$SECONDS] Info: Introspector and WebLogic Server pods with same namespace and domain-uid:"
      echo

      # print results as a table
      #  - first strip out the var= and replace with "val".
      #  - note that the quotes are necessary so that 'print_table'
      #    doesn't get confused by col entries that are missing values
      (
        # column headers must line up with the jpath in getPodInfo
        echo "NAME RVER IVER IMAGE AIIMAGES READY PHASE"
        echo "---- ---- ---- ----- -------- ----- -----"
        cat $tmpfilecur | sed "s|[^ ]*=;\([^;]*\);|'\1'|g"
      ) | column -t
      echo

      cp $tmpfilecur $tmpfileorig
      last_report_secs=$SECONDS
    fi
  fi

  #
  # Exit 1 if domain resource is reporting a failure and "ignore_failures" is "false" (the default)
  #
  if [ "${ignore_failures}" = "false" ] && [ ! -z "${condition_failed_str}" ]; then
    echo
    echo "@@ [$(timestamp)][seconds=$SECONDS] Error: The domain resource has a failure condition: '${condition_failed_str}'. If you want this script to ignore failure conditions and keep trying regardless, then specify '-i' on the commmand line."
    exit 1
  fi

  #
  # Exit 0 if we've reached our goal
  #
  if [ "$is_done" = "true" ]; then
    echo
    echo "@@ [$(timestamp)][seconds=$SECONDS] Info: Success!"
    exit 0
  fi

  #
  # Exit 1 if too much time has passed.
  #
  if [ $SECONDS -ge $timeout_secs ]; then
    echo
    echo "@@ [$(timestamp)][seconds=$SECONDS] Error: Timeout after waiting more than $timeout_secs seconds."
    exit 1
  fi

  sleep 1
done
