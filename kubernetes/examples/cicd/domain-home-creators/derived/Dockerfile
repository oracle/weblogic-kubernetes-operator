# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

FROM  domain1:base

USER root

RUN mkdir -p ${BUILD_TMP}

COPY model/model.yaml ${BUILD_TMP}
COPY wlsdeploy ${BUILD_TMP}/wlsdeploy
COPY weblogic-deploy.zip ${BUILD_TMP}

RUN cd ${BUILD_TMP} && \
    ${JAVA_HOME}/bin/jar xf weblogic-deploy.zip && \
    rm weblogic-deploy.zip && \
    chmod +x weblogic-deploy/bin/*.sh && \
    chmod -R +x weblogic-deploy/lib/python && \
    ${JAVA_HOME}/bin/jar cf apps.zip wlsdeploy && \
    ${BUILD_TMP}/weblogic-deploy/bin/updateDomain.sh \
    -oracle_home ${ORACLE_HOME} \
    -domain_home ${DOMAIN_HOME} \
    -domain_type WLS \
    -model_file ${BUILD_TMP}/model.yaml \
    -archive_file ${BUILD_TMP}/apps.zip && \
    cat ${DOMAIN_HOME}/config/config.xml && \
    rm -rf ${BUILD_TMP} && \
    chown -R oracle:oracle ${DOMAIN_HOME} && \
    chmod -R a+xwr ${DOMAIN_HOME}

USER oracle

WORKDIR ${DOMAIN_HOME}
