FROM mysql:5.6

RUN groupadd -g 1000 oracle && \
    mkdir -p /u01 && \
    chmod a+xr /u01 && \
    useradd -b /u01 -d /u01/oracle -g 1000 -u 1000 -m -s /bin/bash oracle && \
    chown oracle:oracle -R /u01

USER oracle
