FROM store/oracle/weblogic:19.1.0.0

ARG ARG_DOMAIN_NAME="${ARG_DOMAIN_NAME:-domain1}"
ARG ARG_DOMAIN_HOME="${ARG_DOMAIN_HOME:-/u01/oracle/user-projects/domains/}"
ARG ADMIN_USER="${ADMIN_USER}"
ARG ADMIN_PWD="${ADMIN_PWD}"

ENV DOMAIN_NAME="${ARG_DOMAIN_NAME}" \
    DOMAIN_HOME="${ARG_DOMAIN_HOME}" \
    ADMIN_USER="${ADMIN_USER}" \
    ADMIN_PWD="${ADMIN_PWD}"

COPY scripts/create-domain.py /tmp/

RUN wlst.sh -skipWLSModuleScanning /tmp/create-domain.py

CMD ["/bin/bash"]
