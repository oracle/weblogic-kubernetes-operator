ARG base_img 
FROM ${base_img}
COPY --chown=oracle:oracle *.yaml *.zip *.properties /tmp/delme/
RUN  mkdir -p /u01/model_home/models /u01/model_home/archives /u01/model_home/variables /tmp/delme && \
     cd /u01 && jar xvf /tmp/delme/weblogic-deploy.zip && rm /tmp/delme/weblogic-deploy.zip && \
     chmod +x /u01/weblogic-deploy/bin/*.sh && \
     stat -t -- /tmp/delme/*.yaml >/dev/null 2>&1 && for file in /tmp/delme/*.yaml ; do cp $file /u01/model_home/models ; done || true  && \ 
     stat -t -- /tmp/delme/*.zip >/dev/null 2>&1 && for file in /tmp/delme/*.zip ; do cp $file /u01/model_home/archives ; done || true && \ 
     stat -t -- /tmp/delme/*.properties >/dev/null 2>&1 && for file in /tmp/delme/*.properties ; do cp $file /u01/model_home/variables ; done || true && \ 
     rm -fr /tmp/delme
