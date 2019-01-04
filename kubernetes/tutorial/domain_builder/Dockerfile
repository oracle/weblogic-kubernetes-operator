FROM store/oracle/weblogic:19.1.0.0

ARG ARG_DOMAIN_NAME="${ARG_DOMAIN_NAME:-domain1}"
ARG ARG_DOMAIN_HOME="${ARG_DOMAIN_HOME:-/u01/oracle/user-projects/domains/}"
ARG DOMAIN_ROOT="${DOMAIN_ROOT}"
ARG ADMIN_USER="${ADMIN_USER}"
ARG ADMIN_PWD="${ADMIN_PWD}"

ENV DOMAIN_NAME="${ARG_DOMAIN_NAME}" \
    DOMAIN_HOME="${ARG_DOMAIN_HOME}" \
    DOMAIN_ROOT="${DOMAIN_ROOT}" \
    ADMIN_USER="${ADMIN_USER}" \
    ADMIN_PWD="${ADMIN_PWD}"

COPY scripts/* /tmp/

USER root
RUN /tmp/prepare.sh
USER oracle
RUN wlst.sh -skipWLSModuleScanning /tmp/create-domain.py

CMD ["/bin/bash"]
