#!/bin/bash
# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -eu
set -o pipefail

repoLogin() {

echo "${WLSIMG_BUILDER:-docker} login to src ${SOURCE_REPO}"
echo ${SOURCE_PASSWORD} > pwd.txt
cat pwd.txt | ${WLSIMG_BUILDER:-docker} login ${SOURCE_REPO} -u ${SOURCE_USER} --password-stdin 
rm -rf pwd.txt

# Alternatively use 
# ${WLSIMG_BUILDER:-docker} login ${SOURCE_REPO} -u ${SOURCE_USER} -p  ${SOURCE_PASSWORD}

echo "${WLSIMG_BUILDER:-docker} login to target ${TARGET_REPO}"
echo ${TARGET_PASSWORD} > pwd.txt
cat pwd.txt | ${WLSIMG_BUILDER:-docker} login ${TARGET_REPO} -u ${TARGET_USER} --password-stdin
rm -rf pwd.txt

}

repoPullPushImage() {
 
 # Here the source image contains absolute image path
 # and source image contains relative path wrt to TARGET_REPO

 src_image="${1}"
 tgt_image="${TARGET_REPO}/${2}"

 if [ ${DRY_RUN} == "true" ]; then 
   echo "Executing a dry run ..."
   echo "${WLSIMG_BUILDER:-docker} pull ${src_image} "
   echo "${WLSIMG_BUILDER:-docker} tag  ${src_image} ${tgt_image} "
   echo "${WLSIMG_BUILDER:-docker} push ${tgt_image} "
 else 
   printf 'SRC[%s] TARGET[%s] \n' "${src_image}" "${tgt_image}"
   ${WLSIMG_BUILDER:-docker} pull ${src_image}
   ${WLSIMG_BUILDER:-docker} tag  ${src_image} ${tgt_image}
   ${WLSIMG_BUILDER:-docker} push ${tgt_image}

   ${WLSIMG_BUILDER:-docker} rmi -f ${src_image} 
   ${WLSIMG_BUILDER:-docker} rmi -f ${tgt_image} 
 fi
   
}

repoPullPushImages() {
 file="images.properties"
 grep -E -v '^#' $file | grep -v "^$" |
   while IFS=";" read -r f1 f2 
   do
     # printf 'Source Location : [%s], Target: [%s] \n' "$f1" "$f2"
     repoPullPushImage $f1 $f2 
   done
 }

#MAIN

if [ $# -ge 1 ]; then
 echo "Export the following Environment varaiables before running the script"
 echo "  SOURCE_REPO(ocr),  SOURCE_USER, SOURCE_PASSWORD (Base Image repository)"
 echo "  TARGET_REPO(ocir), TARGET_USER, TARGET_PASSWORD (Target Image repository)"
 exit 0
fi

SOURCE_REPO=${SOURCE_REPO:-container-registry.oracle.com}
SOURCE_USER=${SOURCE_USER:-oracle}
SOURCE_PASSWORD=${SOURCE_PASSWORD:-changeme}

TARGET_REPO=${TARGET_REPO:-phx.ocir.io}
TARGET_USER=${TARGET_USER:-oracle}
TARGET_PASSWORD=${TARGET_PASSWORD:-changeme}

DRY_RUN=${DRY_RUN:-false}

echo "SOURCE_REPO[$SOURCE_REPO] and TARGET_REPO[${TARGET_REPO}]"

if [ ${SOURCE_REPO} == ${TARGET_REPO} ]; then
  echo "SOURCE_REPO[$SOURCE_REPO], TARGET_REPO[${TARGET_REPO}] can't be same"
  exit -1
fi

repoLogin 
repoPullPushImages
