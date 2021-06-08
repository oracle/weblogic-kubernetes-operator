# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This is a sample Dockerfile for supplying Model in Image model files
# and a WDT install in a small separate "common mount" 
# image. This is an alternative to supplying the files directly
# in the domain resource `domain.spec.image` image.

# COMMON_MOUNT_PATH arg:
#   Parent location for Model in Image model and WDT installation files.
#   Must match domain resource 'domain.spec.commonMountVolumes.mountPath'
#   For model-in-image, the following two domain resource attributes can
#   be a directory in the mount path:
#     1) 'domain.spec.configuration.model.modelHome'
#     2) 'domain.spec.configuration.model.wdtInstallHome'
#   Default '/common'.
#
# COMMON_MOUNT_STAGE arg:
#   Local directory containing files to be copied to the COMMON_MOUNT_PATH.
#   Default '.'.
#

FROM busybox
ARG COMMON_MOUNT_PATH=/common
ARG COMMON_MOUNT_STAGE=.
ARG USER=oracle
ARG USERID=1000
ARG GROUP=root
ENV COMMON_MOUNT_PATH=${COMMON_MOUNT_PATH}
RUN adduser -D -u ${USERID} -G $GROUP $USER
COPY ${COMMON_MOUNT_STAGE}/ ${COMMON_MOUNT_PATH}/
RUN chown -R $USER:$GROUP ${COMMON_MOUNT_PATH}/
USER $USER
