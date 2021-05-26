# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

FROM busybox
ARG MOUNT_PATH=/common
ARG USER=oracle
ARG USERID=1000
ARG GROUP=root
ENV MOUNT_PATH=$MOUNT_PATH
ENV WDT_MODEL_HOME=$MOUNT_PATH/models
ENV WDT_HOME=${MOUNT_PATH}/weblogic-deploy
        RUN adduser -D -u ${USERID} -G $GROUP $USER
        COPY weblogic-deploy/ ${WDT_HOME}/
        COPY models/ ${WDT_MODEL_HOME}/
        RUN chown -R $USER:$GROUP ${MOUNT_PATH}/ 
USER $USER
