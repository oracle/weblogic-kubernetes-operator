# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

FROM store/oracle/weblogic:12.2.1.3

ENV DOMAIN_NAME="domain1" \
    BUILD_TMP="${ORACLE_HOME}/docker-build-tmp" \
    WLS_CREDENTIALS="/weblogic-credentials"

ENV DOMAIN_HOME="${ORACLE_HOME}/user_projects/domains/${DOMAIN_NAME}"

ARG ENCODED_ADMIN_USERNAME
ARG ENCODED_ADMIN_PASSWORD

USER root

RUN mkdir -p ${DOMAIN_HOME} && \
    mkdir -p ${BUILD_TMP} && \
    mkdir -p ${WLS_CREDENTIALS} && \
    echo ${ENCODED_ADMIN_USERNAME} | base64 --decode > ${WLS_CREDENTIALS}/username && \
    echo ${ENCODED_ADMIN_PASSWORD} | base64 --decode > ${WLS_CREDENTIALS}/password

COPY model/model.py ${BUILD_TMP}
COPY wlsdeploy ${DOMAIN_HOME}/wlsdeploy

RUN wlst.sh -skipWLSModuleScanning ${BUILD_TMP}/model.py && \
    rm -rf ${BUILD_TMP} && \
    rm -rf ${WLS_CREDENTIALS} && \
    cat ${DOMAIN_HOME}/config/config.xml && \
    chown -R oracle:oracle ${DOMAIN_HOME} && \
    chmod -R a+xwr ${DOMAIN_HOME}

USER oracle

WORKDIR ${DOMAIN_HOME}
