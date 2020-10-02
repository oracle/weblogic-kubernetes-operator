#!/bin/bash
# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# Function to note that a validate error has occurred
#
function validationError {
  printError "$*"
  validateErrors=true
}

#
# Function to cause the script to fail if there were any validation errors
#
function failIfValidationErrors {
  if [ "$validateErrors" = true ]; then
    fail 'The errors listed above must be resolved before the script can continue'
  fi
}

#
# Function to exit and print an error message
# $1 - text of message
function fail {
  printError $*
  exit 1
}

# Function to print an error message
function printError {
  echo "[ERROR] $*"
}

# Function to print an info message
function info {
  if [ "$silentMode" == 'true' ]; then
   echo "[INFO] $*" >> "${logFile}"
  else 
   echo "[INFO] $*" | tee -a "${logFile}" 
  fi
}

# Function to print a warning message
function warning {
  echo "[WARNING] $*" | tee -a "${logFile}" 
}

function printReport {
  if [ -f ${imageValidationSkippedReport} ]; then
    skippedValidationCount=$(cat "${imageValidationSkippedReport}" | wc -l)
    if [ ${skippedValidationCount} -gt 1 ]; then
      info "There are $skippedValidationCount images file that were excluded from validation. Please see ${imageValidationSkippedReport} file for the list of images which were excluded from validation."
    fi
  fi
  if [ -f ${imageScanExcludedReport} ]; then
    imageScanExcludeCount=$(cat "${imageScanExcludedReport}" | wc -l)
    if [ ${imageScanExcludeCount} -gt 1 ]; then
      info "There are $imageScanExcludeCount images that were NOT scanned. Please see ${imageScanExcludedReport} file for the list of images which were excluded from scan."
    fi
  fi
  duration=$SECONDS
  info "Image scan ended at `date -u '+%Y-%m-%d %H.%M.%SUTC'`"
  info "Time Elapsed : $(($duration / 60)) minutes and $(($duration % 60)) seconds."
  rowCount=$(cat "${reportFile}" | wc -l)
  if [ $rowCount -gt 1 ]; then
    echo "==================================" | tee -a "${logFile}"
    echo "  Images Running Oracle Products  " | tee -a "${logFile}"
    echo "==================================" | tee -a "${logFile}"
    cat "${reportFile}" | column -t -s ',' |  tee -a "${logFile}"
  else 
    echo "No matching images found."
  fi
}

#
# Get image id using repository and tag
#
function getImageId {
  local imageRepoTag=$1

  imageId=$(${containerBinary} images --format "{{.ID}}" "$imageRepoTag")
  if [ -z "${imageId}" ]; then
    repository=$(echo $imageRepoTag | cut -d':' -f1)
    tag=$(echo $imageRepoTag | cut -d':' -f2)
    imageId=$(${containerBinary} images | awk '{print $1,$2,$3}' | grep "${repository} " | grep "${tag}" | awk '{print $3}')

  fi
  echo "$imageId"
}

#
# cleanup image directory 
#
function cleanUpImage {
  local imageDir=$1
  rm -rf "${imageDir}"
}

function generateReportHeader {
  if [ -z "${kubernetesFile}" ]; then
    awk 'BEGIN {printf "%s,%s,%s,%s\n", "Artifact", "Respository", "Tag", "Digest"}' >> "${reportFile}"
  else 
    awk 'BEGIN {printf "%s,%s,%s,%s,%s\n","Node(s)", "Artifact", "Respository", "Tag", "Digest"}' >> "${reportFile}"
  fi
}

#
# check if string passed as first argument is present in array passed as second argument
#
checkStringInArray() {
    local n=$1 h
    shift
    for h; do
      [[ $n = "$h" ]] && return
    done
    return 1
}

#
# check if string passed as first argument matches the pattern specified in array passed as second argument
#
checkStringMatchesArrayPattern() {
    local n=$1 h
    shift
    for h; do
      [[ $n = *"$h"* ]] && return
    done
    return 1
}

#
# Get list of images missing on this machine
#
function getMissingImagesList {
  missingImages=()
  imagesToVerify=()
  existingImagesOnHost=($(${containerBinary} images --digests --format "{{.Repository}}@{{.Digest}}" | grep -v "<none>:<none>"))

  getImagesToVerify
  for imageToVerify in ${imagesToVerify[@]}
  do
    if ! checkStringInArray  "${imageToVerify}" "${existingImagesOnHost[@]}"; then
      if [ -n ${imageToVerify} ]; then 
        missingImages+=(${imageToVerify})
      fi
    fi
  done

  sortedMissingImagesList=($(echo "${missingImages[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' '))
  missingImageCount=${#sortedMissingImagesList[@]}
  printf '%s\n' "${sortedMissingImagesList[@]}" > ${missingImagesReport}
  return ${missingImageCount}
}

#
# Get list of images that require availabilty verification (i.e. filter out images in ignoreImageVerificationList)
#
function getImagesToVerify {
  # Check if a file containing list of images to skip verification exists
  if [ -f ${ignoreImageVerificationList} ]; then
    IFS=$'\n' read -d '' -r -a ignoreImageVerificationList < "$ignoreImageVerificationList"
  else
    ignoreImageVerificationList=()
  fi

  # Get list of images present in Kubernetes Node Information file
  imagesInNodeFile=$(cat ${kubernetesFile} | jq '.items |[.[] | {name: .metadata.name, image:.status.images |.[].names | select(.[0] !="<none>@<none>") | .[0] }]' | jq -r '.[].image')

  # Create an array list of images that requires validation
  for imageInNodeFile in ${imagesInNodeFile}; do
    if ! checkStringMatchesArrayPattern "${imageInNodeFile}" "${ignoreImageVerificationList[@]}"; then
      imagesToVerify+=(${imageInNodeFile})
    else
      echo ${imageInNodeFile} >> ${imageValidationSkippedReport}
    fi
  done
}

#
# Get list of images from Kubernetes node information 'output of kubectl get node' file
#
function getImagesInNodeFile {
  IFS=$'\n'
  nodeToImageMapping=$(cat "${kubernetesFile}" | jq '.items |[.[] | {name: .metadata.name, images:.status.images | .[]}]' | jq '.[] | {node:.name, images:.images.names | .[], size:.images.sizeBytes}' | jq -r '"\(.node);\(.images);\(.size )"'  | grep -v "<none>:<none>" | grep -v "<none>@<none>")
}

function convertToBytes {
  local size=$1
  echo ${size} | awk '/[0-9]$/{print $1;next};/[mM]$/{printf "%u\n", $1*(1024*1024);next};/[kK]$/{printf "%u\n", $1*1024;next}'
}
