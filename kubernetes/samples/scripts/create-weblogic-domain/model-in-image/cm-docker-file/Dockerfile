# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

FROM busybox
# The COMMON_MOUNT_PATH arg is the path to copy the model in image files and WDT installation files in the common mount image.
# It maps to 'spec.commonMountVolumes.mountPath' field of the domain resource for the volume referenced by the common mount that uses
# the generated image. If not overridden by docker build-arg, it defaults to '/common'.
ARG COMMON_MOUNT_PATH=/common
# The WDT_MODEL_HOME arg is the location of the WebLogic Deploy Tooling model home. Maps to 'domain.spec.configuration.model.modelHome'
# field of the domain resource. Defaults to '${COMMON_MOUNT_PATH}/models'.
ARG WDT_MODEL_HOME=${COMMON_MOUNT_PATH}/models
# The WDT_INSTALL_HOME arg is the location of the WebLogic Deploy Tooling installation. Maps to 'domain.spec.configuration.model.wdtInstallHome'
# field of the domain resource. Defaults to '${COMMON_MOUNT_PATH}/weblogic-deploy'.
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
