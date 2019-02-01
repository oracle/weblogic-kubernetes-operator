
FROM store/oracle/weblogic:12.2.1.3

ARG ARG_DOMAIN_NAME="${ARG_DOMAIN_NAME}"
ARG ADMIN_USER="${ADMIN_USER}"
ARG ADMIN_PWD="${ADMIN_PWD}"

ENV DOMAIN_NAME="${ARG_DOMAIN_NAME}" \
    ADMIN_USER="${ADMIN_USER}" \
    ADMIN_PWD="${ADMIN_PWD}" 

ENV WDT_HOME="/u01" \
    SCRIPT_DIR="/u01/scripts/" \
    DOMAIN_PARENT="/u01/oracle/user-projects/domains"

RUN mkdir -p $SCRIPT_DIR
COPY weblogic-deploy.zip $WDT_HOME
COPY scripts/* $SCRIPT_DIR
RUN $SCRIPT_DIR/create-domain.sh && \
    rm -rf ${SCRIPT_DIR}

WORKDIR $ORACLE_HOME


