# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This is a sample Dockerfile for supplying Domain on PV model files
# and a WDT installation in a small domain creation image.

# AUXILIARY_IMAGE_PATH arg:
#   Parent location for Domain on PV model and WDT installation files.
#   The default is '/auxiliary', which matches the parent directory in the default values for
#   'domain.spec.configuration.initializeDomainOnPV.domain.domainCreationImages.sourceModelHome' and
#   'domain.spec.configuration.initializeDomainOnPV.domain.domainCreationImages.sourceWDTInstallHome', respectively.
#

FROM busybox
ARG AUXILIARY_IMAGE_PATH=/auxiliary
ARG USER=oracle
ARG USERID=1000
ARG GROUP=root
ENV AUXILIARY_IMAGE_PATH=${AUXILIARY_IMAGE_PATH}
RUN adduser -D -u ${USERID} -G $GROUP $USER
# ARG expansion in COPY command's --chown is available in docker version 19.03.1+.
# For older docker versions, change the Dockerfile to use separate COPY and 'RUN chown' commands.
COPY --chown=$USER:$GROUP ./ ${AUXILIARY_IMAGE_PATH}/
USER $USER
