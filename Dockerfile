# Copyright (c) 2017, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# -------------------------
FROM ghcr.io/oracle/oraclelinux:9-slim AS jre-build

ENV JAVA_URL_X64="https://download.java.net/java/GA/jdk20.0.1/b4887098932d415489976708ad6d1a4b/9/GPL/openjdk-20.0.1_linux-x64_bin.tar.gz"
ENV JAVA_URL_AARCH64="https://download.java.net/java/GA/jdk20.0.1/b4887098932d415489976708ad6d1a4b/9/GPL/openjdk-20.0.1_linux-aarch64_bin.tar.gz"

RUN set -eux; \
    microdnf -y install gzip tar; \
    MACHINE_TYPE=`uname -m`; \
    if [ ${MACHINE_TYPE} == 'x86_64' ]; then \
      JAVA_URL=$JAVA_URL_X64; \
    else \
      JAVA_URL=$JAVA_URL_AARCH64; \
    fi; \
    curl -fL -o jdk.tar.gz "$JAVA_URL"; \
    mkdir -p /jdk; \
    tar --extract --file jdk.tar.gz --directory /jdk --strip-components 1; \
    /jdk/bin/jlink --verbose --compress 2 --strip-java-debug-attributes --no-header-files --no-man-pages --output jre --add-modules java.base,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.sql,jdk.attach,jdk.jdi,jdk.unsupported,jdk.crypto.ec,jdk.zipfs

FROM ghcr.io/oracle/oraclelinux:9-slim

LABEL "org.opencontainers.image.authors"="Ryan Eberhard <ryan.eberhard@oracle.com>" \
      "org.opencontainers.image.url"="https://github.com/oracle/weblogic-kubernetes-operator" \
      "org.opencontainers.image.source"="https://github.com/oracle/weblogic-kubernetes-operator" \
      "org.opencontainers.image.vendor"="Oracle Corporation" \
      "org.opencontainers.image.title"="Oracle WebLogic Server Kubernetes Operator" \
      "org.opencontainers.image.description"="Oracle WebLogic Server Kubernetes Operator" \
      "org.opencontainers.image.documentation"="https://oracle.github.io/weblogic-kubernetes-operator/"

ENV LANG="en_US.UTF-8"

COPY --from=jre-build /jre jre

# Install Java and make the operator run with a non-root user id (1000 is the `oracle` user)
RUN set -eux; \
    microdnf -y update; \
    microdnf -y install jq; \
    microdnf clean all; \
    for bin in /jre/bin/*; do \
        base="$(basename "$bin")"; \
        [ ! -e "/usr/bin/$base" ]; \
        alternatives --install "/usr/bin/$base" "$base" "$bin" 20000; \
    done; \
    java -Xshare:dump; \
    useradd -d /operator -M -s /bin/bash -g root -u 1000 oracle; \
    mkdir -m 775 /operator; \
    mkdir -m 775 /deployment; \
    mkdir -m 775 /probes; \
    mkdir -m 775 /logs; \
    mkdir /operator/lib; \
    chown -R oracle:root /operator /deployment /probes /logs

USER oracle

COPY --chown=oracle:root operator/scripts/* /operator/
COPY --chown=oracle:root deployment/scripts/* /deployment/
COPY --chown=oracle:root probes/scripts/* /probes/
COPY --chown=oracle:root operator/target/weblogic-kubernetes-operator.jar /operator/weblogic-kubernetes-operator.jar
COPY --chown=oracle:root operator/target/lib/*.jar /operator/lib/

HEALTHCHECK --interval=1m --timeout=10s \
  CMD /probes/livenessProbe.sh

WORKDIR /deployment/

CMD ["/deployment/operator.sh"]
