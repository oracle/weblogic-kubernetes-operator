# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.


FROM store/oracle/weblogic:12.2.1.3

# There is already an env named 'DOMAIN_NAME' defined in the base image, 
# so we need to choose another name for domain name.
ARG ARG_DOMAIN_NAME
ARG ADMIN_USER
ARG ADMIN_PWD

ENV DOMAIN_NAME="${ARG_DOMAIN_NAME}" \
    ADMIN_USER="${ADMIN_USER}"

ENV DOMAIN_HOME="/u01/oracle/user-projects/domains/${DOMAIN_NAME}"

COPY scripts/create-domain.py /tmp/

RUN wlst.sh -skipWLSModuleScanning /tmp/create-domain.py

CMD ["/bin/bash"]

