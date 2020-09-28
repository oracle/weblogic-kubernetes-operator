#!/bin/bash
# Copyright 2020, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#set -x
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/utility.sh

function usage {
  echo "usage: ${script} [-i] [-s] [-w] [-h]"
  echo "  -i File containing list of images to scan in [Repository]:[Tag]@[Hash] format."
  echo "  -k File containing Kubernetes Node information in json format (output of 'kubectl get nodes -o json')."
  echo "  -w Working directory for the generated files. Defaults to './work' dir."
  echo "  -s Silent mode."
  echo "  -d Disable Image Validation"
  echo "  -h Help"
  exit 1
}

#
# Parse the command line options
#
silentMode=false
disableValidation=false
sizeVerificationEnabled=true
imageFile=""
kubernetesFile=""
nodeImages=""
artifactsFile="util/artifacts/weblogic.txt"
ignoreImageVerificationList="util/exclude/exclude.txt"
workDir=work
while getopts "des:a:i:h:k:w:x:" opt; do
  case $opt in
    i) imageFile="${OPTARG}"
    ;;
    k) kubernetesFile="${OPTARG}"
    ;;
    a) artifactsFile="${OPTARG}" #for testing only
    ;;
    d) disableValidation=true;
    ;;
    e) sizeVerificationEnabled=false;
    ;;
    s) silentMode=true;
    ;;
    w) workDir="${OPTARG}"
    ;;
    x) ignoreImageVerificationList="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done


# try to execute docker to see whether docker is available
function validateDockerAvailable {
  if ! [ -x "$(command -v docker)" ]; then
    validationError "docker is not installed"
  fi
}

# try to execute jq to see whether jq is available
function validateJqAvailable {
  if ! [ -x "$(command -v jq)" ]; then
    validationError "jq is not installed"
  fi
}

#
# Function to perform validations, read files and initialize workspace
#
function initialize {

  # Validate the required files exist
  validateErrors=false

  validateDockerAvailable
  validateJqAvailable

  if [[ -n "${imageFile}" && ! -f ${imageFile} ]]; then
    validationError "Unable to locate the image file ${imageFile} specified with '-i' parameter"
  fi

  if [[ -n "${kubernetesFile}" && ! -f ${kubernetesFile} ]]; then
    validationError "Unable to locate the Kubernetes Node information file ${kubernetesFile} specified with '-k' parameter."
  fi

  if [ ! -f "$artifactsFile" ]; then
    validationError "Unable to locate the artifacts file ${artifactsFile}"
  fi

  failIfValidationErrors

  # Initialize workspace dir and generate report header
  initWorkDir
  generateReportHeader

  # validate availale images
  if [ -z "$imageFile" ]; then
    echo "Scanning all images on current machine, use '-i' option if you want to scan specific set of images."
    imageFile=$(docker images --digests --format "{{.Repository}}:{{.Tag}}@{{.Digest}}@{{.ID}}" | grep -v "<none>:<none>")
  fi

  if [[ -n "${kubernetesFile}" && ${disableValidation} == 'false' ]]; then
    #echo "validating available images"
    validateAvailableImages
  fi

  # Read or create image file
  if [ -z "$imageFile" ]; then
    IFS=$'\n' read -d '' -r -a images < "${imageFile}"
    imageFile=${images[@]}
  fi

  # Read artifacts file
  IFS=$'\n' read -d '' -r -a artifacts < "$artifactsFile"
  artifactsFile=${artifacts[@]}
}

function validateAvailableImages {

  info "Validating images available for scanning on this machine..."
  nonExistentImages=()
  getNonExistentImages
  sortedNonExistentImages=($(echo "${nonExistentImages[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' '))
  len=${#sortedNonExistentImages[@]}
  if [[ len -gt 0 ]]; then
    validationError "Following images reported in Kubernetes node information file are not available on current machine. These images must be pulled before the script can continue. Use '-d' option to disable image validation."
    echo "================="
    printf '%s\n' "${sortedNonExistentImages[@]}" | tee -a ${missingImagesReport}
    echo "================="
    fail "The images listed in '${missingImagesReport}' file must be pulled before the script can continue. Use '-d' option to disable image validation."
  fi
}

function getNonExistentImages {
  getNodeImages
  for nodeImage in ${nodeImages[@]}
  do
    existingImagesOnHost=($(docker images --digests --format "{{.Repository}}@{{.Digest}}" | grep -v "<none>:<none>"))
    if ! inarray  "${nodeImage}" "${existingImagesOnHost[@]}"; then
      if [ -n ${nodeImage} ]; then 
        nonExistentImages+=(${nodeImage})
      fi
    fi
  done
}

inarray() {
    local n=$1 h
    shift
    for h; do
      [[ $n = "$h" ]] && return
    done
    return 1
}

inarrayPattern() {
    local n=$1 h
    shift
    for h; do
      [[ $n = *"$h"* ]] && return
    done
    return 1
}

function getNodeImages {

if [ -f ${ignoreImageVerificationList} ]; then
  IFS=$'\n' read -d '' -r -a ignoreImageVerificationList < "$ignoreImageVerificationList"
else
  ignoreImageVerificationList=()
fi
imagesInNodeFile=$(cat ${kubernetesFile} | jq '.items |[.[] | {name: .metadata.name, image:.status.images |.[].names | select(.[0] !="<none>@<none>") | .[0] }]' | jq -r '.[].image')

for imageInNodeFile in ${imagesInNodeFile}; do
  if ! inarrayPattern "${imageInNodeFile}" "${ignoreImageVerificationList[@]}"; then
    nodeImages+=(${imageInNodeFile})
  fi
done
}

#
# Initialize workspace dir and generate report header
#
function initWorkDir {
  if [ -z ${workDir} ]; then
    workDir="./work"
  fi
  rm -fr "${workDir}"
  mkdir -p "${workDir}"
  missingImagesReport="${workDir}/missing-images.txt"
  reportFile="${workDir}/report.txt"
  logFile=${workDir}/tool.log
  echo "Log for this run can be found in ${logFile}"
  info "Image scan started at `date -u '+%Y-%m-%d %H.%M.%SUTC'`"
}

#
# Read image list, verify hash, scan image and search for artifact
#
function readImageListAndProcess {

  for image in ${imageFile}
  do
    imageRepoTag=$(echo $image | cut -d'@' -f1)
    imageHash=$(echo $image | cut -d'@' -f2)
    imageId=$(echo $image | cut -d'@' -f3)
    repository=$(echo $imageRepoTag | cut -d':' -f1)
    tag=$(echo $imageRepoTag | cut -d':' -f2)
    if [ -z ${imageId} ]; then
      imageId=$(getImageId "${imageRepoTag}")
    fi
    imageSize=`docker inspect ${imageId} | jq .[].Size`
    
    verifyHashAndGetNodeNames ${repository} ${imageHash} ${imageSize} result
    if [[ -n "${kubernetesFile}" && "${result}" == "NotAvailable" ]]; then
        continue;
    elif [[ ${result} =~ "hashVerificationFailed" ]]; then
        warning "Hash verification failed for ${image} ."
        continue;
    elif [[ ${result} =~ "sizeVerificationFailed" ]]; then
        continue;
    fi

    if [ -n "$imageId" ]; then
      imageDir="${workDir}/${imageId}"
      mkdir -p "${imageDir}"

      info "Scanning image ${imageRepoTag} with id $imageId "

      saveImageAndExtractLayers "${imageId}" "${imageDir}"
      searchArtifactInImage "${imageId}" "${imageDir}" "${result}"
      cleanUpImage "${imageDir}"
    else
      fail "Unable to determine image id for image ${image}, image must be pulled before the script can continue"
    fi
  done
}

#
# Verify image hash and get node names using Kubernetes Node information file
#
function verifyHashAndGetNodeNames {
  local imageRepository=$1
  local imageHash=$2
  local imageSize=$3
  local __result=$4
  hashVerificationFailed=false
  sizeVerificationFailed=false
  imageNode=()
  if [ -z "${kubernetesFile}" ]; then
    eval $__result="NotAvailable"
    return
  fi

  IFS=$'\n'
  for nodeImage in $(cat "${kubernetesFile}" | jq '.items |[.[] | {name: .metadata.name, images:.status.images | .[]}]' | jq '.[] | {node:.name, images:.images.names | .[], size:.images.sizeBytes}' | jq -r '"\(.node);\(.images);\(.size )"'  | grep -v "<none>:<none>" | grep -v "<none>@<none>"); do
    nodeName=$(echo $nodeImage | cut -d';' -f1)
    k8sImage=$(echo $nodeImage | cut -d';' -f2)
    imageSizeInK8sFile=$(echo $nodeImage | cut -d';' -f3)
    if [[ -n $(echo $k8sImage | sed -n 's/@/,/p') ]]; then
      imageRepoInK8sFile=$(echo $k8sImage | cut -d'@' -f1)
      imageHashInK8sFile=$(echo $k8sImage | cut -d'@' -f2)
    fi
    if [[ -n $(echo $imageRepoInK8sFile | sed -n 's/:/,/p') ]]; then
      imageRepoInK8sFile=$(echo $imageRepoInK8sFile | cut -d':' -f1)
    fi
    if [[ "${imageRepoInK8sFile}" == "${imageRepository}" ]]; then
      if [[ ${sizeVerificationEnabled} == 'true' && ${imageSizeInK8sFile} != ${imageSize} ]]; then
        sizeVerificationFailed=true
        break;
      fi
      imageNode+=(${nodeName})
      if [[ -z ${imageHash} || ${imageHash} == "<none>" ]]; then
        break
      fi
      if [[ -n ${imageHash} && ${imageHashInK8sFile} != ${imageHash} ]]; then
        hashVerificationFailed=true
      fi
    fi
  done

  len=${#imageNode[@]}
  if [ ${sizeVerificationFailed} == 'true' ]; then
     warning "Size verification failed for image ${imageRepoInK8sFile}. Image size returned by 'docker inspect' is ${imageSize}, image size in k8s file is ${imageSizeInK8sFile}"
     eval $__result="sizeVerificationFailed"
  elif [[ len -lt 1 ]]; then
     eval $__result="NotAvailable"
  elif [ ${hashVerificationFailed} == 'true' ]; then
     eval $__result="hashVerificationFailed"
  else 
     imageNode=($(echo "${imageNode[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' '))
     eval $__result="'${imageNode}'"
  fi
}


function saveImageAndExtractLayers {
  local image=$1
  local imageDir=$2

  imageTarFile="${imageDir}/${image}.tar"
  docker save "${image}" > "${imageTarFile}"
  tar --warning=none -xf "${imageTarFile}" -C "${imageDir}"
  rm "${imageTarFile}"
}

function searchArtifactInImage {
  local image=$1
  local imageDir=$2
  local nodeName=$3

  IFS=" "
  for artifact in ${artifactsFile}
  do
      find "${imageDir}" -name '*.tar' | while read layer
      do
          searchArtifactInLayers "${image}" "${layer}" "${artifact}" "${nodeName}"
      done
  done
}

function searchArtifactInLayers {
  local image=$1
  local layer=$2
  local artifact=$3 
  local nodeName=$4

  tar -tf "${layer}" | grep -q "${artifact}"
  if [ $? -eq 0 ]; then
    imageName=$(docker images --format "{{.Repository}}:{{.Tag}} {{.ID}}" | grep "${image}" |awk '{print $1}')
    imageDetail=$(docker images --digests |grep ${image})
    if [ -z "${kubernetesFile}" ]; then
      echo $imageDetail | awk -v a="${artifact}" '{printf "%s,%s,%s,%s\n", a,$1,$2,$3}' >> "${reportFile}"
    else 
      echo $imageDetail | awk -v n="${nodeName}" -v a="${artifact}" '{printf "%s,%s,%s,%s,%s\n", n,a,$1,$2,$3}' >> "${reportFile}"
    fi
    info "Artifact $artifact found in image $imageName ."
  fi
}


function runTool {
  initialize
  readImageListAndProcess
  printReport
}
    
runTool
