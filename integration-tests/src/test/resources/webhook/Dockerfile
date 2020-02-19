# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upload
FROM openjdk:11-oracle
COPY webhook /bin/webhook
RUN chmod +x /bin/webhook
COPY hooks.json /etc/webhook/
COPY scalingAction.sh /var/scripts/
COPY scaleUpAction.sh /var/scripts/
COPY scaleDnAction.sh /var/scripts/
CMD ["-verbose", "-hooks=/etc/webhook/hooks.json", "-hotreload"]
ENTRYPOINT ["/bin/webhook"]