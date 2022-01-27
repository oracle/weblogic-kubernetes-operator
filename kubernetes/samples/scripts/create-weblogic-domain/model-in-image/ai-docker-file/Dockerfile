# Copyright (c) 2021, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This is a sample Dockerfile for supplying Model in Image model files
# and a WDT installation in a small separate auxiliary image
# image. This is an alternative to supplying the files directly
# in the domain resource `domain.spec.image` image.

# AUXILIARY_IMAGE_PATH arg:
#   Parent location for Model in Image model and WDT installation files.
#   The default is '/auxiliary', which matches the parent directory in the default values for
#   'domain.spec.configuration.model.auxiliaryImages.sourceModelHome' and
#   'domain.spec.configuration.model.auxiliaryImages.sourceWDTInstallHome', respectively.
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
