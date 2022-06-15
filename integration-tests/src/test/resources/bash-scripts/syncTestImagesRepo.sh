#!/bin/bash
# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

set -eu
set -o pipefail

dockerLogin() {

echo "docker login to src ${SOURCE_REPO}"
echo ${SOURCE_PASSWORD} > pwd.txt
cat pwd.txt | docker login ${SOURCE_REPO} -u ${SOURCE_USER} --password-stdin 
rm -rf pwd.txt

# Alternatively use 
# docker login ${SOURCE_REPO} -u ${SOURCE_USER} -p  ${SOURCE_PASSWORD}

echo "docker login to target ${TARGET_REPO}"
echo ${TARGET_PASSWORD} > pwd.txt
cat pwd.txt | docker login ${TARGET_REPO} -u ${TARGET_USER} --password-stdin
rm -rf pwd.txt

}

dockerPullPushImage() {
 
 # Here the source image contains absolute image path
 # and source image contains relative path wrt to TARGET_REPO

 src_image="${1}"
 tgt_image="${TARGET_REPO}/${2}"

 printf 'SRC[%s] TARGET[%s] \n' "${src_image}" "${tgt_image}"

 docker pull ${src_image}
 docker tag  ${src_image} ${tgt_image}
 docker push ${tgt_image}

 docker rmi -f ${src_image} 
 docker rmi -f ${tgt_image} 
}

dockerPullPushImages {
 file="images.properties"
 egrep -v '^#' $file | grep -v "^$" |
   while IFS=";" read -r f1 f2 
   do
     # printf 'Source Location : [%s], Target: [%s] \n' "$f1" "$f2"
     dockerPullPushImage $f1 $f2 
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

echo "SOURCE_REPO[$SOURCE_REPO] and TARGET_REPO[${TARGET_REPO}]"

if [ ${SOURCE_REPO} == ${TARGET_REPO} ]; then
  echo "SOURCE_REPO[$SOURCE_REPO], TARGET_REPO[${TARGET_REPO}] can't be same"
  exit -1
fi

dockerLogin 
dockerPullPushImages
