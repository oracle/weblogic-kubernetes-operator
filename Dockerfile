# Copyright (c) 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
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

RUN mkdir /operator
RUN mkdir /operator/lib
ENV PATH=$PATH:/operator

ARG VERSION
COPY src/scripts/* /operator/
COPY operator/target/weblogic-kubernetes-operator-$VERSION.jar /operator/weblogic-kubernetes-operator.jar
COPY operator/target/lib/*.jar /operator/lib/

HEALTHCHECK --interval=1m --timeout=10s \
  CMD /operator/livenessProbe.sh

WORKDIR /operator/

CMD ["/operator/operator.sh"]
