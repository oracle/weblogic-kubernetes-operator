#!/bin/bash
# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Launch a "Persistent volume cleanup helper" pod for examining or cleaning up the contents 
# of domain directory on a persistent volume.

script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/helper.sh
source ${scriptDir}/../common/utility.sh
set -eu

initGlobals() {
  KUBERNETES_CLI=${KUBERNETES_CLI:-kubectl}
  claimName=""
  mountPath=""
  namespace="default"
  image="ghcr.io/oracle/oraclelinux:8-slim"
  imagePullPolicy="IfNotPresent"
  pullsecret=""
  runAsRoot=""
}

usage() {
  cat << EOF

  This is a helper script for examining, changing permissions, or deleting the contents of the persistent
  volume (such as domain files or logs) for a WebLogic Domain on PV or Model in Image domain.
  The script launches a a Kubernetes pod named as 'pvhelper' using the provided persistent volume claim name and the mount path. 
  You can run the '${KUBERNETES_CLI} exec' to get a shell to the running pod container and run commands to examine or clean up the contents of 
  shared directories on persistent volume. 
  If the helper pod is already running in the namespace with the provide options, then it doesn't create a new pod.
  If the helper pod is already running and the persistent volume claim name or mount path doesn't match, then script will generate an error.
  Use '${KUBERNETES_CLI} delete pod pvhelper -n <namespace>' command to delete the pod when it's no longer needed.

  Please see README.md for more details.

  Usage:

    $(basename $0) -c persistentVolumeClaimName -m mountPath [-n namespace] [-i image] [-u imagePullPolicy] [-o helperOutputDir] [-r] [-h]"
    
    [-c | --claimName]                : Persistent volume claim name. This parameter is required.

    [-m | --mountPath]                : Mount path of the persistent volume in helper pod. This parameter is required.

    [-n | --namespace]                : Domain namespace. Default is 'default'.

    [-i | --image]                    : Container image for the helper pod (optional). Default is 'ghcr.io/oracle/oraclelinux:8-slim'.

    [-u | --imagePullPolicy]          : Image pull policy for the helper pod (optional). Default is 'IfNotPresent'.

    [-p | --imagePullSecret]          : Image pull secret for the helper pod (optional). Default is 'None'.

    [-r | --runAsRoot]                : Option to run the pod as a root user. Default is 'runAsNonRoot'.

    [-h | --help]                     : This help.

EOF
exit $1
}

processCommandLine() {
  while [[ "$#" -gt "0" ]]; do
    key="$1"
    case $key in
      -c|--claimName)
        claimName="$2"
        shift 
        ;;
      -m|--mountPath)
        mountPath="$2"
        shift
        ;;
      -n|--namespace)
        namespace="$2"
        shift
        ;;
      -i|--image)
        image="$2"
        shift
        ;;
      -u|--imagePullPolicy)
        imagePullPolicy="$2"
        shift
        ;;
      -p|--pullsecret)
        pullsecret="$2"
        shift
        ;;
      -r|--runAsRoot)
        runAsRoot="#"
        ;;
      -h|--help)
        usage 0
        ;;
      -*|--*)
        echo "Unknown option $1"
        usage 1
        ;;
      *)
        # unknown option
        ;;
    esac
    shift # past arg or value
  done
}

validatePvc() {
  if [ -z "${claimName}" ]; then
    printError "${script}: -c persistentVolumeClaimName must be specified."
    usage 1
  fi
  
  pvc=$(${KUBERNETES_CLI} get pvc ${claimName} -n ${namespace} --ignore-not-found)
  if [ -z "${pvc}" ]; then
    printError "${script}: Persistent volume claim '$claimName' does not exist in namespace ${namespace}. \
      Please specify an existing persistent volume claim name using '-c' parameter."
    exit 1
  fi
}

validateMountPath() {
  if [ -z "${mountPath}" ]; then
    printError "${script}: -m mountPath must be specified."
    usage 1
  elif [[ ! "$mountPath" =~ '/' ]] &&  [[ ! "$mountPath" =~ '\' ]]; then
    printError "${script}: -m mountPath is not a valid path."
    usage 1
  fi
}

checkAndDefaultPullSecret() {
  if [ -z "${pullsecret}" ]; then
    pullsecret="none"
    pullsecretPrefix="#"
  fi
}

validateParameters() {
  validatePvc
  validateMountPath
  checkAndDefaultPullSecret
}


processExistingPod() {
  existingMountPath=$(${KUBERNETES_CLI} get po pvhelper -n ${namespace} -o jsonpath='{.spec.containers[0].volumeMounts[0].mountPath}')
  existingClaimName=$(${KUBERNETES_CLI} get po pvhelper -n ${namespace} -o jsonpath='{.spec.volumes[0].persistentVolumeClaim.claimName}')
  if [ "$existingMountPath" != "$mountPath" ]; then
    printError "Pod 'pvhelper' already exists in namespace '$namespace' but the mount path \
      '$mountPath' doesn't match the mount path '$existingMountPath' for existing pod. \
      Please delete the existing pod using '${KUBERNETES_CLI} delete pod pvhelper -n $namespace'\
      command to create a new pod."
    exit 1
  fi
  if [ "$existingClaimName" != "$claimName" ]; then
    printError "Pod 'pvhelper' already exists but the claim name '$claimName' doesn't match \
    the claim name '$existingClaimName' of existing pod. Please delete the existing pod \
    using '${KUBERNETES_CLI} delete pod pvhelper -n $namespace' command to create a new pod."
    exit 1
  fi
  printInfo "Pod 'pvhelper' exists in namespace '$namespace'."
}

createPod() {
  printInfo "Creating pod 'pvhelper' using image '${image}', persistent volume claim \
    '${claimName}' and mount path '${mountPath}'."

  pvhelperYamlTemp=${scriptDir}/template/pvhelper.yaml.template
  template="$(cat ${pvhelperYamlTemp})"

  template=$(echo "$template" | sed -e "s:%NAMESPACE%:${namespace}:g;\
    s:%WEBLOGIC_IMAGE_PULL_POLICY%:${imagePullPolicy}:g;\
    s:%WEBLOGIC_IMAGE_PULL_SECRET_NAME%:${pullsecret}:g;\
    s:%WEBLOGIC_IMAGE_PULL_SECRET_PREFIX%:${pullsecretPrefix}:g;\
    s:%CLAIM_NAME%:${claimName}:g;s:%VOLUME_MOUNT_PATH%:${mountPath}:g;\
    s:%RUN_AS_ROOT_PREFIX%:${runAsRoot}:g;\
    s?image:.*?image: ${image}?g")
  ${KUBERNETES_CLI} delete po pvhelper -n ${namespace} --ignore-not-found
  echo "$template" | ${KUBERNETES_CLI} apply -f -
}

printCommandOutput() {
  printInfo "Executing '${KUBERNETES_CLI} -n $namespace exec -i pvhelper -- ls -l ${mountPath}' \
             command to print the contents of the mount path in the persistent volume."

  cmdOut=$(${KUBERNETES_CLI} -n $namespace exec -i pvhelper -- ls -l ${mountPath})
  printInfo "=============== Command output ===================="
  echo "$cmdOut"
  printInfo "==================================================="
}

printPodUsage() {
  printInfo "Use command '${KUBERNETES_CLI} -n $namespace exec -it pvhelper -- /bin/sh' and \
             cd to '${mountPath}' directory to view or delete the contents on the persistent volume."
  printInfo "Use command '${KUBERNETES_CLI} -n $namespace delete pod pvhelper' to delete the pod \
             created by the script."
}

main() {
  pvhelperpod=`${KUBERNETES_CLI} get po -n ${namespace} | grep "^pvhelper " | cut -f1 -d " " `
  if [ "$pvhelperpod" = "pvhelper" ]; then
    processExistingPod
  else
    createPod
  fi
  
  checkPod pvhelper $namespace # exits non zero non error
  
  checkPodState pvhelper $namespace "1/1" # exits non zero on error
  
  sleep 5
  
  printCommandOutput
  
  printPodUsage
}

initGlobals
processCommandLine "${@}"
validateParameters
main
