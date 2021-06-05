# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This is a sample Dockerfile for supplying Model in Image model files
# and a WDT install in a small separate "common mount" 
# image. This is an alternative to supplying the files directly
# in the domain resource `domain.spec.image` image.

# COMMON_MOUNT_PATH arg:
#   Parent location for Model in Image model and WDT installation files.
#   Must match domain resource 'domain.spec.commonMountVolumes.mountPath'
#   Default '/common'.
#
# WDT_MODEL_HOME arg:
#   Location for WebLogic Deploy Tooling model home.
#   Must match domain resource 'domain.spec.configuration.model.modelHome'
#   Must be a directory within COMMON_MOUNT_PATH.
#   Defaults to '${COMMON_MOUNT_PATH}/models'.
#
# WDT_INSTALL_HOME arg: 
#   Location of the WebLogic Deploy Tooling installation.
#   Must match domain resource 'domain.spec.configuration.model.wdtInstallHome'
#   Must be a directory within COMMON_MOUNT_PATH.
#   Defaults to '${COMMON_MOUNT_PATH}/weblogic-deploy'.

FROM busybox
ARG COMMON_MOUNT_PATH=/common
ARG WDT_MODEL_HOME=${COMMON_MOUNT_PATH}/models
ARG WDT_INSTALL_HOME=${COMMON_MOUNT_PATH}/weblogic-deploy
ARG USER=oracle
ARG USERID=1000
ARG GROUP=root
ENV COMMON_MOUNT_PATH=${COMMON_MOUNT_PATH}
ENV WDT_MODEL_HOME=${WDT_MODEL_HOME}
ENV WDT_INSTALL_HOME=${WDT_INSTALL_HOME}
RUN adduser -D -u ${USERID} -G $GROUP $USER
COPY weblogic-deploy/ ${WDT_INSTALL_HOME}/
COPY models/ ${WDT_MODEL_HOME}/
RUN chown -R $USER:$GROUP ${COMMON_MOUNT_PATH}/
USER $USER
