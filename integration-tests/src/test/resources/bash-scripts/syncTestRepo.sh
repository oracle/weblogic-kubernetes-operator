#!/bin/bash
# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/up

SOURCE_REPO=${BASE_IMAGES_REPO}
SOURCE_USER=${BASE_IMAGES_REPO_USERNAME}
SOURCE_PASSWORD=${BASE_IMAGES_REPO_PASSWORD}

TARGET_REPO=${TEST_IMAGES_REPO}
TARGET_USER=${TEST_IMAGES_REPO_USERNAME}
TARGET_PASSWORD=${TEST_IMAGES_REPO_PASSWORD}

echo ${SOURCE_PASSWORD} > pwd.txt
cat pwd.txt | docker login ${SOURCE_REPO} -u ${SOURCE_USER} --password-stdin
rm -rf pwd.txt
# Alternatively use 
# docker login ${SOURCE_REPO} -u ${SOURCE_USER} -p  ${SOURCE_PASSWORD}

echo ${TARGET_PASSWORD} > pwd.txt
cat pwd.txt | docker login ${TARGET_REPO} -u ${TARGET_USER} --password-stdin
rm -rf pwd.txt
# Alternatively use 
# docker login ${TARGET_REPO} -u ${TARGET_USER} -p  ${TARGET_PASSWORD}

dockerPullPushMiddelwareImage() {
 image=${1}
 docker pull ${SOURCE_REPO}/middleware/${image}
 docker tag ${SOURCE_REPO}/middelwarwe/${image} \
            ${TARGET_REPO}/weblogick8s/test-images/${image}
 docker push  ${TARGET_REPO}/weblogick8s/test-imgaes/${image}
 docker rmi -f ${SOURCE_REPO}/middleware/${image}
}

dockerPullPushDbImage() {
 image=${1}
 docker pull ${SOURCE_REPO}/database/${image}
 docker tag ${SOURCE_REPO}/database/${image} \
            ${TARGET_REPO}/weblogick8s/test-images/database/${image}
 docker push  ${TARGET_REPO}/weblogick8s/test-images/database/${image}
 docker rmi -f ${SOURCE_REPO}/database/${image}
}

dockerPullPushMiddelwareImage weblogic:14.1.1.0-11
dockerPullPushMiddelwareImage fmw-infrastructure:12.2.1.4

dockerPullPushDbImage enterprise:12.2.0.1-slim
dockerPullPushDbImage operator:0.1.0
