# Copyright 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.

# using JRE 8 with support for container heap management
FROM store/oracle/serverjre:8

RUN mkdir /operator
RUN mkdir /operator/lib
ENV PATH=$PATH:/operator

COPY operator/src/main/scripts/* /operator/
COPY operator/target/weblogic-kubernetes-operator-0.1.0.jar /operator/weblogic-kubernetes-operator.jar
COPY operator/target/lib/*.jar /operator/lib/

HEALTHCHECK --interval=1m --timeout=10s \
  CMD /operator/livenessProbe.sh

WORKDIR /operator/

CMD ["/operator/operator.sh"]
