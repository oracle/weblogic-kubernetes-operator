FROM  oracle/weblogic:12213-patch-wls-for-k8s

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

COPY model/model.yaml ${BUILD_TMP}
COPY wlsdeploy ${BUILD_TMP}/wlsdeploy
COPY weblogic-deploy.zip ${BUILD_TMP}

RUN cd ${BUILD_TMP} && \
    ${JAVA_HOME}/bin/jar xf weblogic-deploy.zip && \
    rm weblogic-deploy.zip && \
    chmod +x weblogic-deploy/bin/*.sh && \
    chmod -R +x weblogic-deploy/lib/python && \
    ${JAVA_HOME}/bin/jar cf apps.zip wlsdeploy

RUN ${BUILD_TMP}/weblogic-deploy/bin/createDomain.sh \
    -oracle_home ${ORACLE_HOME} \
    -java_home ${JAVA_HOME} \
    -domain_home ${DOMAIN_HOME} \
    -domain_type WLS \
    -model_file ${BUILD_TMP}/model.yaml \
    -archive_file ${BUILD_TMP}/apps.zip && \
    rm -rf ${BUILD_TMP} && \
    rm -rf ${WLS_CREDENTIALS} && \
    cat ${DOMAIN_HOME}/config/config.xml && \
    chown -R oracle:oracle ${DOMAIN_HOME} && \
    chmod -R a+xwr ${DOMAIN_HOME}

USER oracle

WORKDIR ${DOMAIN_HOME}
