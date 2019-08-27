FROM model-in-image:x0
#RUN mkdir -p /u01/model_home/models /u01/model_home/archives /u01/model_home/variables /u01/apps/$DOMAIN_UID /tmp/delme
#RUN mkdir -p /u01/apps/$DOMAIN_UID/
COPY --chown=oracle:oracle *.yaml *.zip *.properties /tmp/delme/
RUN  mkdir -p /u01/wdt/models /tmp/delme && \
     cd /u01/wdt && jar xvf /tmp/delme/weblogic-deploy.zip && rm /tmp/delme/weblogic-deploy.zip && \
     chmod +x /u01/weblogic-deploy/bin/*.sh && \
     stat -t -- /tmp/delme/*.yaml >/dev/null 2>&1 && for file in /tmp/delme/*.yaml ; do cp $file /u01/wdt/models ; done      || true  && \
     stat -t -- /tmp/delme/*.zip >/dev/null 2>&1 && for file in /tmp/delme/*.zip ; do cp $file /u01/wdt/models ; done|| true && \
     stat -t -- /tmp/delme/*.properties >/dev/null 2>&1 && for file in /tmp/delme/*.properties ; do cp $file/u01/wdt/models ; done || true && \
     rm -fr /tmp/delme
