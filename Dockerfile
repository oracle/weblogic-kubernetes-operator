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

ENV JAVA_VERSION 14.0.2
ENV JAVA_URL https://download.java.net/java/GA/jdk14.0.2/205943a0976c4ed48cb16f1043c5c647/12/GPL/openjdk-14.0.2_linux-x64_bin.tar.gz

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
    groupadd -g 1000 oracle; \
    useradd -d /operator -M -s /bin/bash -g 1000 -u 1000 oracle; \
    mkdir -p /operator/lib; \
    mkdir /logs; \
    chown -R 1000:1000 /operator /logs

USER 1000

COPY src/scripts/* /operator/
COPY operator/target/weblogic-kubernetes-operator.jar /operator/weblogic-kubernetes-operator.jar
COPY operator/target/lib/*.jar /operator/lib/

HEALTHCHECK --interval=1m --timeout=10s \
  CMD /operator/livenessProbe.sh

WORKDIR /operator/

CMD ["/operator/operator.sh"]
