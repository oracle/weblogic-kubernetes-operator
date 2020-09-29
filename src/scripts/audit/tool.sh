#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.


#set -x
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/utility.sh

function usage {
  echo "usage: ${script} [-i <filename>] [-k <filename>] [-s] [-w <dirname>] [-d] [-h]"
  echo "  -i File containing list of images to scan in [Repository]:[Tag]@[Hash] format."
  echo "  -k File containing Kubernetes Node information in json format (output of 'kubectl get nodes -o json')."
  echo "  -w Working directory for the generated files. Defaults to './work' dir."
  echo "  -s Silent mode."
  echo "  -d Disable validation check of images that are present in node informaion file but have not been pulled on current machine."
  echo "  -h Help"
  exit 1
}

#
# Parse the command line options
#
silentMode=false
disableMissingImagesValidation=false
sizeVerificationDisabled=""
imageFile=""
kubernetesFile=""
artifactsFile="util/artifacts/weblogic.txt"
ignoreImageVerificationList="util/exclude/exclude.txt"
workDir=work
nodeToImageMapping=()

while getopts "des:a:i:h:k:w:x:" opt; do
  case $opt in
    i) imageFile="${OPTARG}"
    ;;
    k) kubernetesFile="${OPTARG}"
    ;;
    a) artifactsFile="${OPTARG}" #for testing only
    ;;
    d) disableMissingImagesValidation=true;
    ;;
    e) sizeVerificationDisabled=true;
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

  # If image list is not provided, get all images on the current machine 
  if [ -z "$imageFile" ]; then
    echo "Scanning all images on current machine, use '-i' option if you want to scan specific set of images."
    imageFile=$(docker images --digests --format "{{.Repository}}:{{.Tag}}@{{.Digest}}@{{.ID}}" | grep -v "<none>:<none>")
  fi

  # Validate list of available images
  if [[ -n "${kubernetesFile}" && ${disableMissingImagesValidation} == 'false' ]]; then
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

  # Read image to node mapping from kubernetes file in nodeToImageMapping array
  if [ -n "${kubernetesFile}" ]; then
    getImagesInNodeFile
  fi

}

function validateAvailableImages {

  info "Validating images available for scanning on this machine..."
  getMissingImagesList
  missingImageCount=$?
  if [[ $missingImageCount -gt 0 ]]; then
    fail "Found $missingImageCount images missing on current machine. The list of missing images is saved in ${missingImagesReport} file, these images must be pulled to current machine before the script can continue. Use '-d' option to disable image validation."
  fi
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
# Read image list, verify hash and get node names (if node info provided) and process
#
function scanAndSearchImageList {

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
    verifyHashAndGetNodeNames ${repository} ${imageHash} ${imageSize} resultNodeName
    if [[ -n "${kubernetesFile}" && "${resultNodeName}" == "FailedNotAvailable" ]]; then
        continue;
    fi

    if [ -n "$imageId" ]; then
      info "Scanning image ${imageRepoTag} with id $imageId "
      scanAndSearchImage "${imageId}" "${imageDir}" "${resultNodeName}" 
    else
      fail "Unable to determine image id for image ${image}, image must be pulled before the script can continue"
    fi
  done
}

#
# Scan image and search for artifact
#
function scanAndSearchImage {
  local imageId=$1
  local imageDir=$2
  local nodeNames=$3

  imageDir="${workDir}/${imageId}"
  mkdir -p "${imageDir}"

  saveImageAndExtractLayers "${imageId}" "${imageDir}"
  searchArtifactInImage "${imageId}" "${imageDir}" "${nodeNames}"
  cleanUpImage "${imageDir}"
}

#
# Verify image hash and get node names using Kubernetes Node information file
#
function verifyHashAndGetNodeNames {
  local imageRepo=$1
  local imageHash=$2
  local imageSize=$3
  local __result=$4

  hashMatchFailed=false
  imageNode=()
  if [ -z "${kubernetesFile}" ]; then
    eval $__result="FailedNotAvailable"
    return
  fi

  IFS=$'\n'
  for nodeImage in ${nodeToImageMapping[@]}; do
    IFS=';' read -ra imageDetails <<< "${nodeImage}"
    nodeName=${imageDetails[0]}
    img=${imageDetails[1]}
    size=${imageDetails[2]}
    [[ $img == *[@]* ]] && repo=$(echo $img | cut -d'@' -f1) && hash=$(echo $img | cut -d'@' -f2)
    [[ $img == *[:]* ]] && repo=$(echo $img | cut -d':' -f1) && tag=$(echo $img | cut -d':' -f2)
    [[ $repo == *[:]* ]] && repo=$(echo $repo | cut -d':' -f1)
    if [[ "${repo}" == "${imageRepo}" ]]; then
      if [[ -z ${sizeVerificationDisabled} && ${size} != ${imageSize} ]]; then
        warning "Size verification failed for image ${imageRepo}. Image size returned by 'docker inspect' is ${imageSize}, image size in k8s file is ${size}"
        break
      fi
      imageNode+=(${nodeName})
      [[ -z ${imageHash} || ${imageHash} == "<none>" ]] && break
      if [[ -n ${imageHash} && ${hash} != ${imageHash} ]]; then
        warning "Hash verification failed for image ${imageRepo}"
        hashMatchFailed=true
        break
      fi
    fi
  done

  len=${#imageNode[@]}
  if [[ $len -lt 1  || ${hashMatchFailed} == 'true' ]]; then
    eval $__result="FailedNotAvailable"
  else 
    eval $__result="'$(echo "${imageNode[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' ')'"
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
  scanAndSearchImageList
  printReport
}
    
runTool
