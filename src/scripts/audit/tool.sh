#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.


#set -x
script="${BASH_SOURCE[0]}"
scriptDir="$( cd "$( dirname "${script}" )" && pwd )"
source ${scriptDir}/utility.sh

function usage {
  echo "usage: ${script} [-i <filename>] [-k <filename>] [-m <container_cli>] [-s] [-w <dirname>] [-d] [-h]"
  echo "  -i File containing list of images to scan in [Repository]:[Tag]@[Hash] format with each image on a separate line."
  echo "  -k Node information file containing mapping of nodes to images. For Kubernetes use-case, this is the output of 'kubectl get nodes -o json' command."
  echo "  -m Container CLI command e.g. 'docker' or 'podman'. Defaults to 'docker'."
  echo "  -w Workspace directory, generated files will be stored in this directory. Defaults to './work' dir."
  echo "  -d Disable validation check of images that are present in the Node information file but have not been pulled on the current machine."
  echo "  -s Silent mode."
  echo "  -h Help"
  exit 1
}

#
# Parse the command line options
#
containerCli=${CONTAINER_CLI:-docker}
imageSizeThreshold=${SCAN_THRESHOLD:-200M}
silentMode=false
disableMissingImagesValidation=false
sizeVerificationEnabled=""
imageFile=""
kubernetesFile=""
artifactsFile="util/artifacts/weblogic.txt"
excludeList="util/exclude/exclude.txt"
workDir=work
nodeToImageMapping=()

while getopts "des:a:i:h:k:m:w:x:" opt; do
  case $opt in
    i) imageFile="${OPTARG}"
    ;;
    k) kubernetesFile="${OPTARG}"
    ;;
    a) artifactsFile="${OPTARG}" #for testing only
    ;;
    d) disableMissingImagesValidation=true;
    ;;
    e) sizeVerificationEnabled=true;
    ;;
    m) containerCli="${OPTARG}";
    ;;
    s) silentMode=true;
    ;;
    w) workDir="${OPTARG}"
    ;;
    x) excludeList="${OPTARG}"
    ;;
    h) usage 0
    ;;
    *) usage 1
    ;;
  esac
done


# try to execute container binary to see whether binary file is available
function validateContainerBinaryAvailable {
  if ! [ -x "$(command -v ${containerCli})" ]; then
    validationError "${containerCli} is not installed"
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

  validateContainerBinaryAvailable
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
    imageFile=$(${containerCli} images --digests --format "{{.Repository}}:{{.Tag}}@{{.Digest}}@{{.ID}}" | grep -v "<none>:<none>")
  else
    IFS=$'\n' read -d '' -r -a images < "${imageFile}"
    imageFile=${images[@]}
  fi

  # Validate list of available images
  if [[ -n "${kubernetesFile}" && ${disableMissingImagesValidation} == 'false' ]]; then
    validateAvailableImages
  fi

  # Read artifacts file
  IFS=$'\n' read -d '' -r -a artifacts < "$artifactsFile"
  artifactsFile=${artifacts[@]}

  # Read node to image mapping from kubernetes file in nodeToImageMapping array
  if [ -n "${kubernetesFile}" ]; then
    getNodeToImageMapping
  fi

}

function validateAvailableImages {

  info "Validating images available for scanning on this machine..."
  getMissingImagesList
  missingImageCount=$?
  if [ $missingImageCount -gt 0 ]; then
    fail "There are $missingImageCount images that have not been pulled on current machine. Please see ${missingImagesReport} file for the list of missing images. These images must be pulled to current machine before the script can continue. Use '-d' option to disable missing image validation."
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
  imageValidationSkippedReport="${workDir}/validation-skipped-images.txt"
  imageScanExcludedReport="${workDir}/scan-excluded-images.txt"
  reportFile="${workDir}/report.txt"
  logFile=${workDir}/tool.log
  echo "Log for this run can be found in ${logFile}"
  # Start the timer
  SECONDS=0
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
    repository=$(echo $imageRepoTag | cut -d':' -f1)
    tag=$(echo $imageRepoTag | cut -d':' -f2)
    imageId=$(getImageId "${image}")

    if [ -n "${imageId}" ]; then
      imageSize=`${containerCli} inspect ${imageId} | jq .[].Size`
    else
      fail "Unable to determine image id for image ${image}, image must be pulled before the script can continue"
    fi
    imageSizeThresholdNum=$(convertToBytes ${imageSizeThreshold})
    if [ ${imageSize} -lt ${imageSizeThresholdNum} ]; then
      info "Image scan skipped as image size is lower than ${imageSizeThreshold}  - ${imageRepoTag}@${imageHash}" | tee -a ${imageScanExcludedReport}
      continue
    fi
    if checkIfImageInExcludeList "${imageRepoTag}" "${excludeList[@]}"; then
      info "Image scan skipped as image is in exclude list - ${imageRepoTag}" | tee -a ${imageScanExcludedReport}
      continue
    fi

    if [ -n "${kubernetesFile}" ]; then
      verifyHashAndGetNodeNames ${imageRepoTag} ${imageHash} ${imageSize} resultNodeName
      if [ "${resultNodeName}" == "NotAvailableOrFailed" ]; then
          continue;
      fi
    fi

    info "Scanning image ${imageRepoTag} with id $imageId "
    scanAndSearchImage "${imageId}" "${imageDir}" "${resultNodeName}" 
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
  local imageRepoTag=$1
  local imageHash=$2
  local imageSize=$3
  local __result=$4

  hashMatchFailed=false
  imageNode=()
  imageRepo=$(echo $imageRepoTag | cut -d':' -f1)
  imageTag=$(echo $imageRepoTag | cut -d':' -f2)

  IFS=$'\n'
  for nodeImage in ${nodeToImageMapping[@]}; do
    IFS=';' read -ra imageDetails <<< "${nodeImage}"
    nodeName=${imageDetails[0]}
    imgTag=${imageDetails[1]}
    imgHash=${imageDetails[2]}
    size=${imageDetails[3]}
    [[ $imgHash == *[@]* ]] && repo=$(echo $imgHash | cut -d'@' -f1) && hash=$(echo $imgHash | cut -d'@' -f2)
    [[ $imgTag == *[:]* ]] && repo=$(echo $imgTag | cut -d':' -f1) && tag=$(echo $imgTag | cut -d':' -f2)
    [[ $repo == *[:]* ]] && repo=$(echo $repo | cut -d':' -f1)
    if [[ "${repo}" == "${imageRepo}" && "${tag}" == "${imageTag}" ]]; then
      if [[ -n ${sizeVerificationEnabled} && ${size} != ${imageSize} ]]; then
        warning "Image size verification failed - ${imageRepoTag}:${imageTag}@${imageHash}" | tee -a ${imageScanExcludedReport}
        break
      fi
      imageNode+=(${nodeName})
      if [[ -z ${imageHash} || ${hash} != ${imageHash} ]]; then
        warning "Image hash verification failed - ${imageRepoTag}:${imageTag}@${imageHash}" | tee -a ${imageScanExcludedReport}
        hashMatchFailed=true
        break
      fi
    fi
  done

  len=${#imageNode[@]}
  if [[ $len -lt 1  || ${hashMatchFailed} == 'true' ]]; then
    eval $__result="NotAvailableOrFailed"
  else 
    eval $__result="'$(echo "${imageNode[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' ')'"
  fi
}

function saveImageAndExtractLayers {
  local image=$1
  local imageDir=$2

  imageTarFile="${imageDir}/${image}.tar"
  ${containerCli} save "${image}" -o "${imageTarFile}" > >(tee -a ${logFile}) 2> >(tee -a ${logFile} >&2)
  if [ -f ${imageTarFile} ]; then
    tar --warning=none -xf "${imageTarFile}" -C "${imageDir}"
    rm "${imageTarFile}"
  fi
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
          searchArtifactInLayers "${image}" "${layer}" "${artifact}" "${nodeName}" matchFound
          if [ "${matchFound}" == 'true' ]; then
            break
          fi
      done
  done
}

function searchArtifactInLayers {
  local image=$1
  local layer=$2
  local artifact=$3 
  local nodeName=$4
  local __matchFound=$5

  tar -tf "${layer}" | grep -q "${artifact}"
  if [ $? -eq 0 ]; then
    imageName=$(${containerCli} images --format "{{.Repository}}:{{.Tag}} {{.ID}}" | grep "${image}" |awk '{print $1}')
    imageDetail=$(${containerCli} images --digests |grep ${image})
    if [ -z "${kubernetesFile}" ]; then
      echo $imageDetail | awk -v a="${artifact}" '{printf "%s,%s,%s,%s\n", a,$1,$2,$3}' >> "${reportFile}"
    else 
      nodeNames="${nodeName[*]}"
      node=$(echo "${nodeNames//${IFS:0:1}/|}")
      echo $imageDetail | awk -v n="${node::-1}" -v a="${artifact}" '{printf "%s,%s,%s,%s,%s\n", n,a,$1,$2,$3}' >> "${reportFile}"
    fi
    info "Artifact $artifact found in image $imageName ."
    eval $__matchFound=true
  fi
}


function runTool {
  initialize
  scanAndSearchImageList
  printReport
}
    
runTool
