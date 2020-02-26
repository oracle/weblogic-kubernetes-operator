# Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# HOW TO BUILD THIS IMAGE
# -----------------------
# Run:
#      $ docker build -t weblogic-kubernetes-operator:latest .
#
# Pull base image
# From the Docker store
# -------------------------
FROM openjdk:11-oracle
RUN yum -y install openssl && yum clean all

# Maintainer
# ----------
MAINTAINER Ryan Eberhard <ryan.eberhard@oracle.com>

# make the operator run with a non-root user id (1000 is the `oracle` user)
RUN groupadd -g 1000 oracle && \
    useradd -d /operator -M -s /bin/bash -g 1000 -u 1000 oracle && \
    mkdir /operator && \
    mkdir /operator/lib && \
    mkdir /logs && \
    chown -R 1000:1000 /operator /logs
USER 1000

ENV PATH=$PATH:/operator

ARG VERSION
COPY src/scripts/* /operator/
COPY operator/target/weblogic-kubernetes-operator-$VERSION.jar /operator/weblogic-kubernetes-operator.jar
COPY operator/target/lib/*.jar /operator/lib/

HEALTHCHECK --interval=1m --timeout=10s \
  CMD /operator/livenessProbe.sh

WORKDIR /operator/

CMD ["/operator/operator.sh"]
