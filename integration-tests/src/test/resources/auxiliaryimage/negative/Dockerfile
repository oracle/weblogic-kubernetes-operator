# Copyright (c) 2021, 2022 Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This is a sample Dockerfile for supplying Model in Image model files
# and a WDT installation in a small separate auxiliary
# image. This is an alternative to supplying the files directly
# in the domain resource `domain.spec.image` image.

# AUXILIARY_IMAGE_PATH arg:
#   Parent location for Model in Image model and WDT installation files.
#   Must match domain resource 'domain.spec.auxiliaryImageVolumes.mountPath'
#   For model-in-image, the following two domain resource attributes can
#   be a directory in the mount path:
#     1) 'domain.spec.configuration.model.modelHome'
#     2) 'domain.spec.configuration.model.wdtInstallHome'
#   Default '/auxiliary'.
#

FROM phx.ocir.io/weblogick8s/test-images/docker/busybox:1.34.1
ARG AUXILIARY_IMAGE_PATH=/auxiliary
ARG USER=tester
ARG USERID=1001
ARG GROUP=myroot
ENV AUXILIARY_IMAGE_PATH=${AUXILIARY_IMAGE_PATH}
RUN addgroup -g $USERID $GROUP
RUN adduser -D -u ${USERID} -G $GROUP $USER
COPY ./ ${AUXILIARY_IMAGE_PATH}/
RUN chgrp $GROUP ${AUXILIARY_IMAGE_PATH}/models/test1.properties
RUN chown -R $USER:$GROUP ${AUXILIARY_IMAGE_PATH}/models/test1.properties
RUN chmod 640 ${AUXILIARY_IMAGE_PATH}/models/test1.properties
ARG USER=oracle
ARG USERID=1000
ARG GROUP=root
RUN adduser -D -u ${USERID} -G $GROUP $USER
RUN chown -R $USER:$GROUP ${AUXILIARY_IMAGE_PATH}/weblogic-deploy
RUN chown -R $USER:$GROUP ${AUXILIARY_IMAGE_PATH}/models/*.zip
RUN chown -R $USER:$GROUP ${AUXILIARY_IMAGE_PATH}/models/*.yaml
USER $USER