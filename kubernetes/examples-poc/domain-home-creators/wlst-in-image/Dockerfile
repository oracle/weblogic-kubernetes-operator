FROM store/oracle/weblogic:12.2.1.3

ENV DOMAIN_NAME="domain1" \
    BUILD_TMP="${ORACLE_HOME}/docker-build-tmp"

ENV DOMAIN_HOME="${ORACLE_HOME}/user_projects/domains/${DOMAIN_NAME}"

USER root

RUN mkdir -p ${DOMAIN_HOME} && \
    mkdir -p ${BUILD_TMP}

COPY model/model.py ${BUILD_TMP}
COPY wlsdeploy ${DOMAIN_HOME}/wlsdeploy

RUN wlst.sh -skipWLSModuleScanning ${BUILD_TMP}/model.py && \
    rm -rf ${BUILD_TMP} && \
    cat ${DOMAIN_HOME}/config/config.xml && \
    chown -R oracle:oracle ${DOMAIN_HOME} && \
    chmod -R a+xwr ${DOMAIN_HOME}

USER oracle

WORKDIR ${DOMAIN_HOME}
