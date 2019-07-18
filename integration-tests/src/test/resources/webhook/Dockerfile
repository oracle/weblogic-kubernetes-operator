#FROM store/oracle/serverjre:8
FROM container-registry.oracle.com/java/serverjre:latest

COPY webhook /bin/webhook
COPY hooks.json /etc/webhook/
COPY scalingAction.sh /var/scripts/
COPY scaleUpAction.sh /var/scripts/
COPY scaleDnAction.sh /var/scripts/
CMD ["-verbose", "-hooks=/etc/webhook/hooks.json", "-hotreload"]
ENTRYPOINT ["/bin/webhook"]