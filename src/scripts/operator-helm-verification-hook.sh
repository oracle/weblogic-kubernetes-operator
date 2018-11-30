#!/bin/bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#set -x

# When the operator helm chart is installed or upgraded, the user can make a number of
# errors that will prevent helm from successfully creating / upgrading the release,
# for example, the customer may try to deploy the operator to a namespace that already
# has an operator deployed to it.
#
# We don't want to just let helm detect the failure because it can leave the
# release in a half baked state that can be difficult to cleanup.
#
# So, the operator helm chart wants to check for some of these common user errors
# and abort the install/upgrade if it finds any problems.
#
# It does this by configuring pre-install and pre-upgrade hooks that create kubernetes
# jobs (which use the operator image) that calls this script.
#
# This script is responsible for detecting these common end user problems.
# Currently, it checks the following:
#   - the operator service account exists in the operator namespace
#   - all of the domain namespaces exist
#   - none of the domain namespaces are being managed by an operator in a different namespace
#   - there isn't another operator in the operator namespace (only checked by helm install)
#
# This script requires that the following args are passed in:
# $1    - the kind of kubernetes hook, either "pre-install" or "pre-upgrade"
# $2    - the name of the operator namespace
# $3    - the name of the operator service account (in the operator namespace)
# $4... - the rest of the args contain the names of the domain namespaces that this operator should manage
#
# If this script does not detect any problems, then it exits with a 0 status.
# This causes the pod and job to succeed.
#
# If this script detects any problems, it prints errors to stdout/stderr then exits with a 1 status.
# This causes the pod to fail, which in turn causes the job to fail.
#
# Helm notices whether the job succeed or not.
#
# If the job succeeded, then helm completes the install/upgrade (i.e. creates or
# updates the operator's k8s resources).  It also deletes the job.
#
# If the job fails, then helm does not delete it (so that the customer can use
# "kubectl logs" to view the errors).
#
# If the job fails during a helm install, then helm doesn't create the release
# and doesn't create any of the operator's k8s resources (but still creates
# the operator namespace if it didn't already exist)
#
# If the job fails during a helm upgrade, then helm doesn't update any of the operator's
# k8s resources, but does rev the release's version and sets it status to
# PENDING_UPGRADE.  The customer is responsible for either fixing the problem and
# upgrading the release again, or rolling back the release.


# This script uses the kubernetes REST api to GET various k8s resources so that it
# can detect user errors.  This output is stored in the following files:
k8s_operator_config_maps="operator-config-maps.json"
k8s_namespaces="namespaces.json"
k8s_operator_service_accounts="operator-service-accounts.json"

# This script has uses python to do the actual validation.
# It has some boiler plate python code shared by each kind of validation
# (e.g. overall error handling).  Each kind of validation adds in its own
# python commands.  They are stored in this file:
custom_python_cmds="custom_cmds.py"

# This global variable captures whether this script detected any errors
# (user errors or unexpected errors).  It is used to control whether
# the script returns a status of 0 or 1.
found_error="false"

# This function prints an unexpected error (e.g. the k8s REST api or python failed)
# to stdout and records the fact that an error occurred.
function unexpectedError() {
  echo "Unexpected error: $@"
  errorOccurred
}

# This function records the fact that an error occurred.  This causes the script to fail.
function errorOccurred() {
  found_error="true"
}

# This function uses curl to do an http GET of a kubernetes resource as a json object.
# It takes the following args:
# $1 - the uri of the kubernetes REST resource to GET
# $2 - the name of the file store the results in
#
# If the GET succeeded, then it writes the json output to that file, then returns 0
# If the GET failed, then it prints the reason to stdout/stderr, then returns 1.
function getK8sResource() {
  local uri=$1
  local out=$2
  local resp="resp.out"
  local k8s_access_token=`cat /var/run/secrets/kubernetes.io/serviceaccount/token`
  local k8s_cacert="/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"
  local k8s_master="https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT_HTTPS}"
  echo "Getting Kubernetes resource ${uri}"
  rm -f ${resp}
  http_code=`\
    curl \
    -s \
    -S \
    -w %{http_code} \
    -o ${resp} \
    --cacert ${k8s_cacert} \
    -H "Authorization: Bearer ${k8s_access_token}" \
    -H "Accept: application/json" \
    -X GET ${k8s_master}/${uri}\
  `
  curl_status="$?"
  if [ ${curl_status} -ne 0 ]; then
    unexpectedError "curl failured: ${curl_status}"
    return 1
  fi
  if [ ${http_code} -ne 200 ]; then
    unexpectedError "http code: ${http_code}"
    cat ${resp}
    return 1
  fi
  mv ${resp} ${out}
  return 0
}

# This function fetches all of the kubernetes resources that will be needed to
# validate the user's inputs.  Currently it fetches:
#   all of the config maps (in all namespaces) that have the weblogic.operator label
#   all of the namespaces
#   all of the service accounts in the operator's namespace
#
# If it successfully fetched all of these resources, then it returns 0.
# If it runs into any problems, it immediately stops and returns 1
# (which means that any downstream validation code should not be run).
function getKubernetesResources() {
  echo ""
  local operatorNamespace=$1
  getK8sResource api/v1/configmaps?labelSelector=weblogic.operatorName ${k8s_operator_config_maps}
  status="$?"
  if [ $status -ne 0 ]; then
    return status
  fi
  getK8sResource api/v1/namespaces ${k8s_namespaces}
  status="$?"
  if [ $status -ne 0 ]; then
    return status
  fi
  getK8sResource api/v1/namespaces/${operatorNamespace}/serviceaccounts ${k8s_operator_service_accounts}
  status="$?"
  if [ $status -ne 0 ]; then
    return status
  fi
}

# This function calls a verification python script.
# It takes the following args:
# $1    - the name of a file containing the text to send to stdin
#         this is typically the name of one of the json files that
#         getKubernetesResources fetched.
# $2... - the rest of the args are passed to the python script
#
# The caller must write a verification python script to the 'custom_cmds.py' file
# before calling this script.
#
# This script adds some boiler plate python commands and functions before and after
# the custom script.
#
# It adds a 'userError' function which the custom script must call if it detects any errors.
# This function writes the error to stdout, records the fact that an error occurred, then
# continues.  This lets the custom script detect multiple errors (instead of returning after
# detecting the first problem).
#
# If it detects any user or unexpected errors, it prints the errors to stdout
# and sets found_error to true.
function callVerificationPythonScript() {

  # parse the args:
  local stdin=$1
  shift
  args=$@

  # write all the python commands to this file:
  local python_cmds="cmds.py"

  # add some boiler plate python commands before the custom python commands
  cat > ${python_cmds} << INPUT

import sys, json, traceback

exitCode = 0

def userError( msg ) :
  print "  Error: " + msg
  global exitCode
  exitCode = 2
  return

print ""

try:
INPUT

  # add the custom python commands
  cat ${custom_python_cmds} >> ${python_cmds}

  # add some boiler plate pyton commands after the custom python commands
  cat >> ${python_cmds} << INPUT
  
except:
  print "Unexpected Error: python failed:"
  traceback.print_exc()
  exitCode = 1

if exitCode != 0:
  exit(exitCode)
INPUT

  # call the overall python script, sending in the specified file as stdin
  # and passing in the specified command line args
  cat ${stdin} | python ${python_cmds} ${args}

  status="$?"
  if [ ${status} -eq 2 ]; then
    # the python script succeeded and found one or more user errors
    errorOccurred
  elif [ ${status} -ne 0 ]; then
    # the python script unexpectedly failed
    unexpectedError "python failed running:"
    # print out the python commands (so that the customer can make sense of the python traceback)
    cat ${python_cmds}
  fi
}

# This function creates a custom python script that validates that an operator
# hasn't been deployed to the operator namespace.
function createVerifyOperatorNotDeployedToOperatorNamespacePythonScript() {
  cat > ${custom_python_cmds} << INPUT
  cms = json.load(sys.stdin)["items"]
  operatorNS = sys.argv[1]
  print "Verifying that an operator has not already been deployed to the " + operatorNS + " namespace."
  for cm in cms:
    metadata = cm["metadata"];
    if metadata["name"] == "weblogic-operator-cm":
      opNS = metadata["namespace"]
      if opNS == operatorNS:
        userError("There is already an operator deployed to the " + operatorNS + " namespace.")
INPUT
}

# This function creates a custom python script that validates the operator's
# service account exists in the operator's namespace.
function createVerifyOperatorServiceAccountExistsPythonScript() {
  cat > ${custom_python_cmds} << INPUT
  sas = json.load(sys.stdin)["items"]
  operatorNS = sys.argv[1]
  operatorSA = sys.argv[2]
  print "Verifying that the " + operatorSA + " service account exists in the " + operatorNS + " namespace."
  found = False
  for sa in sas:
    metadata = sa["metadata"];
    if metadata["name"] == operatorSA:
      found = True
  if found == False:
    userError("The " + operatorSA + " service account does not exist in the " + operatorNS + " namespace.")
INPUT
}

# This function creates a custom python script that validates that all of the
# domain namespaces exist.
function createVerifyDomainNamespacesExistPythonScript() {
  cat > ${custom_python_cmds} << INPUT
  nss = json.load(sys.stdin)["items"]
  print "Verifying that all of the domain namespaces exist."
  for i in range(1, len(sys.argv)):
    domainNS = sys.argv[i]
    found = False
    for ns in nss:
      metadata = ns["metadata"];
      if metadata["name"] == domainNS:
        found = True
    if found == False:
      userError("The " + domainNS + " namespace does not exist.")
INPUT
}

# This function creates a custom python script that validates that none of the
# domain namespaces are being managed by another operator.
function createVerifyDomainNamespacesNotControlledByAnotherOperatorPythonScript() {
  cat > ${custom_python_cmds} << INPUT
  cms = json.load(sys.stdin)["items"]
  operatorNS = sys.argv[1]
  print "Verifying that none of the domain namespaces are managed by another operator."
  for i in range(2, len(sys.argv)):
    domainNS = sys.argv[i]
    for cm in cms:
      metadata = cm["metadata"];
      if metadata["name"] == "weblogic-operator-cm":
        opNS = metadata["namespace"]
        if opNS != operatorNS:
          targetNSs = cm["data"]["targetNamespaces"]
          for targetNS in targetNSs.split(","):
            if targetNS == domainNS:
              userError("The operator in the " + opNS + " namespace already manages the domains in the " + domainNS + " namespace.")
INPUT
}

# This function verifies that an operator has not already been deployed to the
# operator namespace.
#
# It takes the following args:
# $1 - the name of the operator namespace
#
# If it detects any user or unexpected errors, it prints the errors to stdout
# and sets found_error to true.
function verifyOperatorNotDeployedToOperatorNamespace() {
  local operatorNamespace=$1
  createVerifyOperatorNotDeployedToOperatorNamespacePythonScript
  callVerificationPythonScript ${k8s_operator_config_maps} ${operatorNamespace}
}

# This function verifies that the operator service account exists in the operator namespace.
#
# It takes the following args:
# $1 - the name of the operator namespace
# $2 - the name of the operator service account
#
# If it detects any user or unexpected errors, it prints the errors to stdout
# and sets found_error to true.
function verifyOperatorServiceAccountExists() {
  local operatorNamespace=$1
  local operatorServiceAccount=$2
  createVerifyOperatorServiceAccountExistsPythonScript
  callVerificationPythonScript ${k8s_operator_service_accounts} ${operatorNamespace} ${operatorServiceAccount}
}

# This function verifies that all the domain namespaces exist.
#
# It takes the following args:
# $1... - the args list the domain name spaces, one namespace per arg
#
# If it detects any user or unexpected errors, it prints the errors to stdout
# and sets found_error to true.
function verifyDomainNamespacesExist() {
  local domainNamespaces=$@
  createVerifyDomainNamespacesExistPythonScript
  callVerificationPythonScript ${k8s_namespaces} ${domainNamespaces}
}

# This function verifies that none of the domain namespaces is being managed by another
# operator.
#
# It takes the following args:
# $1    - the name of the operator namespace
# $2... - the rest of args list the domain name spaces, one namespace per arg
#
# If it detects any user or unexpected errors, it prints the errors to stdout
# and sets found_error to true.
function verifyDomainNamespacesNotControlledByAnotherOperator() {
  local operatorNamespace=${1}
  shift
  local domainNamespaces=$@
  createVerifyDomainNamespacesNotControlledByAnotherOperatorPythonScript
  callVerificationPythonScript ${k8s_operator_config_maps} ${operatorNamespace} ${domainNamespaces}
}

# This function performs all the hook validations:
#   - the operator service account exists in the operator namespace
#   - all of the domain namespaces exist
#   - none of the domain namespaces are being managed by an operator in a different namespace
#   - there isn't another operator in the operator namespace (only checked by helm install)
#
# It takes the following args:
# $1    - whether to do the pre-install checks ("true") or the pre-upgrade checks ("false")
# $2    - the name of the operator namespace
# $3    - the name of the operator service account
# $4... - the rest of the args contain the names of the domain namespaces
#
# If it detects any user or unexpected errors, it prints the errors to stdout
# and sets found_error to true.
function verify() {
  # parse the args
  isInstall=${1}
  shift
  operatorNamespace=${1}
  shift
  operatorServiceAccount=${1}
  shift
  domainNamespaces=$@

  # fetch all of the k8s resources (as json files) that will be needed to perform
  # the various checks
  getKubernetesResources $operatorNamespace
  status="$?"
  if [ ${status} -eq 0 ]; then

    # perform the specific checks
    if [ ${isInstall} == "true" ]; then
      verifyOperatorNotDeployedToOperatorNamespace $operatorNamespace
    fi
    verifyOperatorServiceAccountExists $operatorNamespace $operatorServiceAccount
    verifyDomainNamespacesExist ${domainNamespaces}
    verifyDomainNamespacesNotControlledByAnotherOperator ${operatorNamespace} ${domainNamespaces}
  fi
}

# This function performs all the pre-install hook validations:
#   - the operator service account exists in the operator namespace
#   - all of the domain namespaces exist
#   - none of the domain namespaces are being managed by an operator in a different namespace
#   - there isn't another operator in the operator namespace (only checked by helm install)
#
# It takes the following args:
# $1    - the name of the operator namespace
# $2    - the name of the operator service account
# $3... - the rest of the args contain the names of the domain namespaces
#
# If it detects any user or unexpected errors, it prints the errors to stdout
# and sets found_error to true.
function doPreInstallVerification() {
  echo "Performing the operator helm chart pre-install checks: $@"
  verify "true" $@
}

# This function performs all the pre-upgrade hook validations:
#   - the operator service account exists in the operator namespace
#   - all of the domain namespaces exist
#   - none of the domain namespaces are being managed by an operator in a different namespace
#
# It takes the following args:
# $1    - the name of the operator namespace
# $2    - the name of the operator service account
# $3... - the rest of the args contain the names of the domain namespaces
#
# If it detects any user or unexpected errors, it prints the errors to stdout
# and sets found_error to true.
function doPreUpgradeVerification() {
  echo "Performing the operator helm chart pre-upgrade checks: $@"
  verify "false" $@
}

# This function returns the results of the checks by
# exiting the script with the correct status code.
#
# If there were no user or unexpected errors, then it exits with a status of 0.
# If there were one or more user or unexpected errors, then it exits with a status of 1.
function returnResults() {
  if [ "${found_error}" == "true" ]; then
    exit 1
  else
    exit 0
  fi
}

# This function is the main entry point of this script.
# It performs all of the needed checks.
#
# It takes the following args:
# $1    - the kind of kubernetes hook ("pre-install" or "pre-upgrade"
# $2    - the name of the operator namespace
# $3    - the name of the operator service account
# $4... - the rest of the args contain the names of the domain namespaces
#
# If there were no user or unexpected errors, then it exits with a status of 0.
# If there were one or more user or unexpected errors, then it exits with a status of 1.
function main() {
  hookType=${1}
  shift
  if [ "${hookType}" == "pre-install" ]; then
    doPreInstallVerification $@
  elif [ "${hookType}" == "pre-upgrade" ]; then
    doPreUpgradeVerification $@
  else
    unexpectedError "Unsupported hook type : ${hookType}"
  fi
  returnResults
}

main $@
