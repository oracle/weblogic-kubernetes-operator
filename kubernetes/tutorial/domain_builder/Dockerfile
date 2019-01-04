FROM store/oracle/weblogic:19.1.0.0

ARG ARG_DOMAIN_NAME="${ARG_DOMAIN_NAME}"
ARG ADMIN_USER="${ADMIN_USER}"
ARG ADMIN_PWD="${ADMIN_PWD}"

ENV DOMAIN_NAME="${ARG_DOMAIN_NAME}" \
    ADMIN_USER="${ADMIN_USER}" \
    ADMIN_PWD="${ADMIN_PWD}" 

ENV DOMAIN_HOME="/u01/oracle/user-projects/domains/${DOMAIN_NAME}"

COPY scripts/create-domain.py /tmp/

RUN wlst.sh -skipWLSModuleScanning /tmp/create-domain.py

CMD ["/bin/bash"]

