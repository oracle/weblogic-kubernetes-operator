# Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# HOW TO BUILD THIS IMAGE
# -----------------------
# Run:
#      $ ./buildDockerImage.sh [-t <image-name>]
#
# -------------------------
FROM oraclelinux:7-slim

# Maintainer
# ----------
MAINTAINER Ryan Eberhard <ryan.eberhard@oracle.com>

RUN set -eux; \
    yum -y install gzip tar openssl; \
    rm -rf /var/cache/yum

# Default to UTF-8 file.encoding
ENV LANG en_US.UTF-8

ENV JAVA_HOME /usr/local/java
ENV PATH /operator:$JAVA_HOME/bin:$PATH

ENV JAVA_VERSION 15
ENV JAVA_URL https://download.java.net/java/GA/jdk15/779bf45e88a44cbd9ea6621d33e33db1/36/GPL/openjdk-15_linux-x64_bin.tar.gz

# Install Java and make the operator run with a non-root user id (1000 is the `oracle` user)
RUN set -eux; \
    curl -fL -o /jdk.tar.gz "$JAVA_URL"; \
    mkdir -p "$JAVA_HOME"; \
    tar --extract --file /jdk.tar.gz --directory "$JAVA_HOME" --strip-components 1; \
    rm /jdk.tar.gz; \
    mkdir /usr/java; \
    ln -sfT "$JAVA_HOME" /usr/java/default; \
    ln -sfT "$JAVA_HOME" /usr/java/latest; \
    rm -Rf "$JAVA_HOME/include" "$JAVA_HOME/jmods"; \
    rm -f "$JAVA_HOME/lib/src.zip"; \
    for bin in "$JAVA_HOME/bin/"*; do \
        base="$(basename "$bin")"; \
        [ ! -e "/usr/bin/$base" ]; \
        alternatives --install "/usr/bin/$base" "$base" "$bin" 20000; \
    done; \
    java -Xshare:dump; \
    useradd -d /operator -M -s /bin/bash -g root -u 1000 oracle; \
    mkdir -m 775 /operator; \
    mkdir -m 775 /logs; \
    mkdir /operator/lib; \
    chown -R oracle:root /operator /logs

USER oracle

COPY --chown=oracle:root src/scripts/* /operator/
COPY --chown=oracle:root operator/target/weblogic-kubernetes-operator.jar /operator/weblogic-kubernetes-operator.jar
COPY --chown=oracle:root operator/target/lib/*.jar /operator/lib/

HEALTHCHECK --interval=1m --timeout=10s \
  CMD /operator/livenessProbe.sh

WORKDIR /operator/

CMD ["/operator/operator.sh"]
