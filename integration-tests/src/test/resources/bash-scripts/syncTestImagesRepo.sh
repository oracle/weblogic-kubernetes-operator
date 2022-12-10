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

 src_image="${SOURCE_REPO}/${1}"
 tgt_image="${TARGET_REPO}/${2}"

 image=$(echo ${src_image} | awk -F"/" '{print $NF}')

 #printf 'SOURCE[%s] \n' "${src_image}" >> ${OUT}
 #printf 'TARGET[%s] \n' "${tgt_image}" >> ${OUT}

 docker pull ${src_image} || true
 sid=$(docker images ${src_image} -q)
 if [ "x$sid" == "x" ] ; then
     printf 'Could not download Source Image [%s] \n' ${src_image}
     exit -1 
 fi

 docker pull ${tgt_image} || true 
 tid=$(docker images ${tgt_image} -q)
 if [ -z ${tid} ]; then
   printf 'Could not download Target Image [%s] \n' ${tgt_image}
   if [ ${DRY_RUN} != "true" ]; then
     docker tag  ${src_image} ${tgt_image}
     docker push ${tgt_image}
     docker rmi -f ${src_image} 
     docker rmi -f ${tgt_image} 
     printf 'MISSING Uploaded missing [%s] to Target \n' "${image}" >> ${OUT}
   else
     printf 'MISSING [%s] image on Target Repositoty \n' "${image}" >> ${OUT}
   fi 
 fi
   
 # Compare the images id(s)
 # Update the image if needed based on id comparision 
 if [ ! -z ${tid} ] && [ ! -z ${sid} ]; then
   #printf 'SOURCE_IMAGEID[%s] TARGET_IMAGEID[%s]\n' "${sid}" "${tid}" >> ${OUT}
   if [ ${tid} == ${sid} ]; then
      printf 'SKIP [%s] image is up-to-date \n' "${image}"  >> ${OUT}
   else 
     td=$(date "+%Y-%m-%d")
     printf 'UPDATE [%s] image updated \n' "${image}"  >> ${OUT}
     if [ ${DRY_RUN} != "true" ]; then
       printf 'Updating  image [%s] on Target \n' "${image}" >> ${OUT}
       docker rmi -f ${tgt_image} 
       docker tag  ${src_image} ${tgt_image}
       docker push ${tgt_image}
       docker rmi -f ${src_image} 
       docker rmi -f ${tgt_image} 
     else 
      printf 'UPDATE [%s] image updated \n' "${image}"  >> ${OUT}
     fi
  fi 
 fi
 printf '\n'  >> ${OUT}
}

dockerPullPushImages() {
 grep -E -v '^#' ${PROP_FILE} | grep -v "^$" |
   while IFS=";" read -r f1 f2 
   do
     #printf 'Source Location : [%s], Target: [%s] \n' "$f1" "$f2"
     dockerPullPushImage $f1 $f2 
   done
 }

#MAIN

if [ $# -ge 1 ]; then
 echo "Export the following Environment varaiables before running the script"
 echo "  SOURCE_REPO(ocr),SOURCE_USER,SOURCE_PASSWORD (Source Image repository)"
 echo "  TARGET_REPO(ocir),TARGET_USER,TARGET_PASSWORD(Target Image repository)"
 exit 0
fi

OUT=update.out
echo -e "#### Image Upgrade Status on [`date`] \n" > ${OUT}

SOURCE_REPO=${SOURCE_REPO:-container-registry.oracle.com}
SOURCE_USER=${SOURCE_USER:-oracle}
SOURCE_PASSWORD=${SOURCE_PASSWORD:-changeme}

TARGET_REPO=${TARGET_REPO:-phx.ocir.io}
TARGET_USER=${TARGET_USER:-oracle}
TARGET_PASSWORD=${TARGET_PASSWORD:-changeme}

DRY_RUN=${DRY_RUN:-false}
PROP_FILE=${PROP_FILE:-images.properties}

if [ -f ${PROP_FILE} ]; then
  echo "Loading the Image properties file [${PROP_FILE}]"
else
  echo "Could not load Image properties file [${PROP_FILE}]"
  exit -1
fi

echo "SOURCE_REPO[$SOURCE_REPO] and TARGET_REPO[${TARGET_REPO}]"

if [ ${SOURCE_REPO} == ${TARGET_REPO} ]; then
  echo "SOURCE_REPO[$SOURCE_REPO], TARGET_REPO[${TARGET_REPO}] can't be same"
  exit -1
fi

dockerLogin 
dockerPullPushImages
cat ${OUT}
