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
  echo "  -w Working directory for the generated files. Defaults to '$scriptDir/work' dir."
  echo "  -s Silent mode."
  echo "  -h Help"
  exit 1
}

#
# Parse the command line options
#
silentMode=false
imageFile=""
kubernetesFile=""
artifactsFile="util/artifacts/weblogic.txt"
workDir=work
while getopts "s:i:k:a:w:h:" opt; do
  case $opt in
    i) imageFile="${OPTARG}"
    ;;
    k) kubernetesFile="${OPTARG}"
    ;;
    a) artifactsFile="${OPTARG}" #for testing only
    ;;
    s) silentMode=true;
    ;;
    w) workDir="${OPTARG}"
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

  # Read or create image file
  if [ -z "$imageFile" ]; then
    echo "Scanning all images on current machine, use '-i' option if you want to scan specific set of images."
    imageFile=$(docker images --format "{{.Repository}}:{{.Tag}}@{{.Digest}}" | grep -v "<none>:<none>")
  else 
    IFS=$'\n' read -d '' -r -a images < "${imageFile}"
    imageFile=${images[@]}
  fi

  # Read artifacts file
  IFS=$'\n' read -d '' -r -a artifacts < "$artifactsFile"
  artifactsFile=${artifacts[@]}

  # Initialize workspace dir and generate report header
  workDir=$scriptDir/work
  initWorkDir
  generateReportHeader
  
}

#
# Initialize workspace dir and generate report header
#
function initWorkDir {
  workDir="$scriptDir/work"
  rm -fr "${workDir}"
  mkdir -p "${workDir}"
  reportFile="${workDir}/report.txt"
  logFile=${workDir}/tool.log
  info "Log for this run can be found in ${logFile}"
}


#
# Read image list, verify hash, scan image and search for artifact
#
function readImageListAndProcess {

  for image in ${imageFile}
  do
    imageRepoTag=$(echo $image | cut -d'@' -f1)
    imageHash=$(echo $image | cut -d'@' -f2)
    repository=$(echo $imageRepoTag | cut -d':' -f1)
    tag=$(echo $imageRepoTag | cut -d':' -f2)
    
    result="`verifyHashAndGetNodeNames ${repository} ${imageHash}`"
    if [[ -n "${kubernetesFile}" && "${result}" == "NotAvailable" ]]; then
        continue;
    elif [ ${result} == "hashVerificationFailed" ]; then
        warning "Hash verification failed for ${image}."
        continue;
    fi

    imageId=$(getImageId "${imageRepoTag}")
    if [ -n "$imageId" ]; then
      imageDir="${workDir}/${imageId}"
      mkdir -p "${imageDir}"

      info "Scanning image ${imageRepoTag} "

      saveImageAndExtractLayers "${imageId}" "${imageDir}"
      searchArtifactInImage "${imageId}" "${imageDir}" "${result}"
      cleanUpImage "${imageId}"
    fi 
  done
}

#
# Verify image hash and get node names using Kubernetes Node information file
#
function verifyHashAndGetNodeNames {
  local imageRepository=$1
  local imageHash=$2
  hashVerificationFailed=false
  imageNode=""
  if [ -z "${kubernetesFile}" ]; then
    echo "NotAvailable"
    return
  fi

  IFS=$'\n'
  for nodeImage in $(cat "${kubernetesFile}" | jq '.items |[.[] | {name: .metadata.name, image:.status.images |.[].names | select(.[0] !="<none>@<none>" and .[1] != "<none>:<none>")| .[]}]' | jq -c '.[]'); do
    nodeName=$(echo $nodeImage | jq -r .name)
    image=$(echo $nodeImage | jq -r .image)
    imageRepoInK8sFile=$(echo $image | cut -d'@' -f1)
    imageHashInK8sFile=$(echo $image | cut -d'@' -f2)
    if [ "${imageRepoInK8sFile}" == "${imageRepository}" ]; then
      imageNode+="${nodeName},"
      if [[ -z ${imageHash} || ${imageHash} == "<none>" ]]; then
        break
      fi
      if [ ${imageHashInK8sFile} != ${imageHash} ]; then
          hashVerificationFailed=true
      fi
    fi
  done

  if [ -z ${imageNode} ]; then
     echo "NotAvailable"
  elif [ ${hashVerificationFailed} == 'true' ]; then
     echo "hashVerificationFailed"
  else 
     echo "${imageNode::-1}"
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
      echo $imageDetail | awk '{printf "%s,%s,%s\n", $1,$2,$3}' >> "${reportFile}"
    else 
      echo $imageDetail | awk -v a="${nodeName}" '{printf "%s|%s|%s|%s\n", a,$1,$2,$3}' >> "${reportFile}"
    fi
    info "Match found in $imageName"
  fi
}


function runTool {
  initialize
  readImageListAndProcess
  printReport
}
    
runTool
