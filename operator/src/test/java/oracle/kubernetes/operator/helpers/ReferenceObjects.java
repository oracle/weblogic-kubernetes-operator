// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

/**
 * To present pods from rolling when a customer upgrades the operator, we must test against the YAML that would
 * be generated in old operator versions for the settings in the PodHelper test.
 * The settings here are taken from 3.0.0 and 3.1.0.
 */
class ReferenceObjects {
  static final String ADMIN_MII_AUX_IMAGE_POD_3_3 =
      """
          metadata:
            annotations:
              prometheus.io/path: /wls-exporter/metrics
              prometheus.io/port: '7001'
              prometheus.io/scrape: 'true'
              weblogic.sha256: 6bc3726c327773e20be8856c0555d8b25d37fbb82846e7b96060c5b639b7cce2
            labels:
              weblogic.domainName: domain1
              weblogic.serverName: ADMIN_SERVER
              weblogic.domainRestartVersion: null
              weblogic.domainUID: uid1
              weblogic.createdByOperator: 'true'
              weblogic.operatorVersion: 3.3.8
              weblogic.clusterRestartVersion: null
              weblogic.serverRestartVersion: null
            name: uid1-admin-server
            namespace: namespace
          spec:
            containers:
            - command:
              - /weblogic-operator/scripts/startServer.sh
              env:
              - name: DOMAIN_NAME
                value: domain1
              - name: DOMAIN_HOME
                value: /u01/oracle/user_projects/domains
              - name: ADMIN_NAME
                value: ADMIN_SERVER
              - name: ADMIN_PORT
                value: '7001'
              - name: SERVER_NAME
                value: ADMIN_SERVER
              - name: DOMAIN_UID
                value: uid1
              - name: NODEMGR_HOME
                value: /u01/nodemanager
              - name: LOG_HOME
              - name: SERVER_OUT_IN_POD_LOG
                value: 'true'
              - name: SERVICE_NAME
                value: uid1-admin-server
              - name: AS_SERVICE_NAME
                value: uid1-admin-server
              - name: USER_MEM_ARGS
                value: -Djava.security.egd=file:/dev/./urandom
              - name: ADMIN_USERNAME
              - name: ADMIN_PASSWORD
              image: image:latest
              imagePullPolicy: Always
              lifecycle:
                preStop:
                  exec:
                    command:
                    - /weblogic-operator/scripts/stopServer.sh
              livenessProbe:
                exec:
                  command:
                  - /weblogic-operator/scripts/livenessProbe.sh
                failureThreshold: 1
                initialDelaySeconds: 4
                periodSeconds: 6
                timeoutSeconds: 5
              name: weblogic-server
              ports:
              - containerPort: 7001
                name: default
                protocol: TCP
              readinessProbe:
                failureThreshold: 1
                httpGet:
                  path: /weblogic/ready
                  port: 7001
                initialDelaySeconds: 1
                periodSeconds: 3
                timeoutSeconds: 2
              resources:
                limits: {}
                requests:
                  memory: 768Mi
                  cpu: 250m
              securityContext: {}
              volumeMounts:
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/debug
                name: weblogic-domain-debug-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/introspector
                name: weblogic-domain-introspect-cm-volume
              - mountPath: /auxiliary
                name: aux-image-volume-auxiliaryimagevolume1
            hostname: uid1-admin-server
            imagePullSecrets: []
            initContainers:
            - command:
              - /weblogic-operator/scripts/auxImage.sh
              env:
              - name: AUXILIARY_IMAGE_PATH
                value: /auxiliary
              - name: AUXILIARY_IMAGE_TARGET_PATH
                value: /tmpAuxiliaryImage
              - name: AUXILIARY_IMAGE_COMMAND
                value: cp -R $AUXILIARY_IMAGE_PATH/* $AUXILIARY_IMAGE_TARGET_PATH
              - name: AUXILIARY_IMAGE_CONTAINER_IMAGE
                value: model-in-image:WLS-AI-v1
              - name: AUXILIARY_IMAGE_CONTAINER_NAME
                value: operator-aux-container1
              image: model-in-image:WLS-AI-v1
              imagePullPolicy: IfNotPresent
              name: operator-aux-container1
              volumeMounts:
              - mountPath: /tmpAuxiliaryImage
                name: aux-image-volume-auxiliaryimagevolume1
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
            nodeSelector: {}
            securityContext: {}
            volumes:
            - configMap:
                defaultMode: 365
                name: weblogic-scripts-cm
              name: weblogic-scripts-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-debug-cm
                optional: true
              name: weblogic-domain-debug-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-introspect-cm
              name: weblogic-domain-introspect-cm-volume
            - emptyDir: {}
              name: aux-image-volume-auxiliaryimagevolume1
          """;

  static final String MANAGED_MII_AUX_IMAGE_POD_3_3 =
      """
          metadata:
            annotations:
              prometheus.io/path: /wls-exporter/metrics
              prometheus.io/port: '7001'
              prometheus.io/scrape: 'true'
              weblogic.sha256: e10514d80b1ebb8bf7f49b3cad19a249601ee32d2fc7e95686e6dfb7755b5682
            labels:
              weblogic.domainName: domain1
              weblogic.serverName: ess_server1
              weblogic.domainRestartVersion: null
              weblogic.domainUID: uid1
              weblogic.createdByOperator: 'true'
              weblogic.operatorVersion: 3.3.8
              weblogic.clusterRestartVersion: null
              weblogic.serverRestartVersion: null
            name: uid1-ess-server1
            namespace: namespace
          spec:
            containers:
            - command:
              - /weblogic-operator/scripts/startServer.sh
              env:
              - name: DOMAIN_NAME
                value: domain1
              - name: DOMAIN_HOME
                value: /u01/oracle/user_projects/domains
              - name: ADMIN_NAME
                value: ADMIN_SERVER
              - name: ADMIN_PORT
                value: '7001'
              - name: SERVER_NAME
                value: ess_server1
              - name: DOMAIN_UID
                value: uid1
              - name: NODEMGR_HOME
                value: /u01/nodemanager
              - name: LOG_HOME
              - name: SERVER_OUT_IN_POD_LOG
                value: 'true'
              - name: SERVICE_NAME
                value: uid1-ess-server1
              - name: AS_SERVICE_NAME
                value: uid1-admin-server
              - name: USER_MEM_ARGS
                value: -Djava.security.egd=file:/dev/./urandom
              - name: ADMIN_USERNAME
              - name: ADMIN_PASSWORD
              image: image:latest
              imagePullPolicy: Always
              lifecycle:
                preStop:
                  exec:
                    command:
                    - /weblogic-operator/scripts/stopServer.sh
              livenessProbe:
                exec:
                  command:
                  - /weblogic-operator/scripts/livenessProbe.sh
                failureThreshold: 1
                initialDelaySeconds: 4
                periodSeconds: 6
                timeoutSeconds: 5
              name: weblogic-server
              ports:
              - containerPort: 8001
                name: default
                protocol: TCP
              readinessProbe:
                failureThreshold: 1
                httpGet:
                  path: /weblogic/ready
                  port: 8001
                initialDelaySeconds: 1
                periodSeconds: 3
                timeoutSeconds: 2
              resources:
                limits: {}
                requests:
                  memory: 768Mi
                  cpu: 250m
              securityContext: {}
              volumeMounts:
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/debug
                name: weblogic-domain-debug-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/introspector
                name: weblogic-domain-introspect-cm-volume
              - mountPath: /auxiliary
                name: aux-image-volume-auxiliaryimagevolume1
            imagePullSecrets: []
            initContainers:
            - command:
              - /weblogic-operator/scripts/auxImage.sh
              env:
              - name: AUXILIARY_IMAGE_PATH
                value: /auxiliary
              - name: AUXILIARY_IMAGE_TARGET_PATH
                value: /tmpAuxiliaryImage
              - name: AUXILIARY_IMAGE_COMMAND
                value: cp -R $AUXILIARY_IMAGE_PATH/* $AUXILIARY_IMAGE_TARGET_PATH
              - name: AUXILIARY_IMAGE_CONTAINER_IMAGE
                value: model-in-image:WLS-AI-v1
              - name: AUXILIARY_IMAGE_CONTAINER_NAME
                value: operator-aux-container1
              image: model-in-image:WLS-AI-v1
              imagePullPolicy: IfNotPresent
              name: operator-aux-container1
              volumeMounts:
              - mountPath: /tmpAuxiliaryImage
                name: aux-image-volume-auxiliaryimagevolume1
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
            nodeSelector: {}
            securityContext: {}
            volumes:
            - configMap:
                defaultMode: 365
                name: weblogic-scripts-cm
              name: weblogic-scripts-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-debug-cm
                optional: true
              name: weblogic-domain-debug-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-introspect-cm
              name: weblogic-domain-introspect-cm-volume
            - emptyDir: {}
              name: aux-image-volume-auxiliaryimagevolume1
          """;

  static final String ADMIN_MII_CONVERTED_AUX_IMAGE_POD_3_4 =
      """
          metadata:
            annotations:
              prometheus.io/path: /wls-exporter/metrics
              prometheus.io/port: '7001'
              prometheus.io/scrape: 'true'
              weblogic.sha256: 6bc3726c327773e20be8856c0555d8b25d37fbb82846e7b96060c5b639b7cce2
            labels:
              weblogic.domainName: domain1
              weblogic.serverName: ADMIN_SERVER
              weblogic.domainRestartVersion: null
              weblogic.domainUID: uid1
              weblogic.createdByOperator: 'true'
              weblogic.operatorVersion: 3.4.0
              weblogic.clusterRestartVersion: null
              weblogic.serverRestartVersion: null
            name: uid1-admin-server
            namespace: namespace
          spec:
            containers:
            - command:
              - /weblogic-operator/scripts/startServer.sh
              env:
              - name: DOMAIN_NAME
                value: domain1
              - name: DOMAIN_HOME
                value: /u01/oracle/user_projects/domains
              - name: ADMIN_NAME
                value: ADMIN_SERVER
              - name: ADMIN_PORT
                value: '7001'
              - name: SERVER_NAME
                value: ADMIN_SERVER
              - name: DOMAIN_UID
                value: uid1
              - name: NODEMGR_HOME
                value: /u01/nodemanager
              - name: LOG_HOME
              - name: SERVER_OUT_IN_POD_LOG
                value: 'true'
              - name: SERVICE_NAME
                value: uid1-admin-server
              - name: AS_SERVICE_NAME
                value: uid1-admin-server
              - name: USER_MEM_ARGS
                value: -Djava.security.egd=file:/dev/./urandom
              - name: WDT_INSTALL_HOME
                value: /auxiliary/weblogic-deploy
              - name: WDT_MODEL_HOME
                value: /auxiliary/models
              - name: AUXILIARY_IMAGE_PATHS
                value: /auxiliary
              - name: ADMIN_USERNAME
              - name: ADMIN_PASSWORD
              image: image:latest
              imagePullPolicy: Always
              lifecycle:
                preStop:
                  exec:
                    command:
                    - /weblogic-operator/scripts/stopServer.sh
              livenessProbe:
                exec:
                  command:
                  - /weblogic-operator/scripts/livenessProbe.sh
                failureThreshold: 1
                initialDelaySeconds: 4
                periodSeconds: 6
                timeoutSeconds: 5
              name: weblogic-server
              ports:
              - containerPort: 7001
                name: default
                protocol: TCP
              readinessProbe:
                failureThreshold: 1
                httpGet:
                  path: /weblogic/ready
                  port: 7001
                initialDelaySeconds: 1
                periodSeconds: 3
                timeoutSeconds: 2
              resources:
                limits: {}
                requests:
                  memory: 768Mi
                  cpu: 250m
              securityContext: {}
              volumeMounts:
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/debug
                name: weblogic-domain-debug-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/introspector
                name: weblogic-domain-introspect-cm-volume
              - mountPath: /auxiliary
                name: compat-ai-vol-auxiliaryimagevolume1
            hostname: uid1-admin-server
            imagePullSecrets: []
            initContainers:
            - command:
              - /weblogic-operator/scripts/auxImage.sh
              env:
              - name: AUXILIARY_IMAGE_PATH
                value: /auxiliary
              - name: AUXILIARY_IMAGE_TARGET_PATH
                value: /tmpAuxiliaryImage
              - name: AUXILIARY_IMAGE_COMMAND
                value: cp -R $AUXILIARY_IMAGE_PATH/* $AUXILIARY_IMAGE_TARGET_PATH
              - name: AUXILIARY_IMAGE_CONTAINER_IMAGE
                value: model-in-image:WLS-AI-v1
              - name: AUXILIARY_IMAGE_CONTAINER_NAME
                value: compat-operator-aux-container1
              image: model-in-image:WLS-AI-v1
              imagePullPolicy: IfNotPresent
              name: compat-operator-aux-container1
              volumeMounts:
              - mountPath: /tmpAuxiliaryImage
                name: compat-ai-vol-auxiliaryimagevolume1
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
            nodeSelector: {}
            securityContext: {}
            volumes:
            - configMap:
                defaultMode: 365
                name: weblogic-scripts-cm
              name: weblogic-scripts-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-debug-cm
                optional: true
              name: weblogic-domain-debug-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-introspect-cm
              name: weblogic-domain-introspect-cm-volume
            - emptyDir: {}
              name: compat-ai-vol-auxiliaryimagevolume1
          """;

  static final String MANAGED_MII_CONVERTED_AUX_IMAGE_POD_3_4 =
      """
          metadata:
            annotations:
              prometheus.io/path: /wls-exporter/metrics
              prometheus.io/port: '7001'
              prometheus.io/scrape: 'true'
              weblogic.sha256: e10514d80b1ebb8bf7f49b3cad19a249601ee32d2fc7e95686e6dfb7755b5682
            labels:
              weblogic.domainName: domain1
              weblogic.serverName: ess_server1
              weblogic.domainRestartVersion: null
              weblogic.domainUID: uid1
              weblogic.createdByOperator: 'true'
              weblogic.operatorVersion: 3.4.0
              weblogic.clusterRestartVersion: null
              weblogic.serverRestartVersion: null
            name: uid1-ess-server1
            namespace: namespace
          spec:
            containers:
            - command:
              - /weblogic-operator/scripts/startServer.sh
              env:
              - name: DOMAIN_NAME
                value: domain1
              - name: DOMAIN_HOME
                value: /u01/oracle/user_projects/domains
              - name: ADMIN_NAME
                value: ADMIN_SERVER
              - name: ADMIN_PORT
                value: '7001'
              - name: SERVER_NAME
                value: ess_server1
              - name: DOMAIN_UID
                value: uid1
              - name: NODEMGR_HOME
                value: /u01/nodemanager
              - name: LOG_HOME
              - name: SERVER_OUT_IN_POD_LOG
                value: 'true'
              - name: SERVICE_NAME
                value: uid1-ess-server1
              - name: AS_SERVICE_NAME
                value: uid1-admin-server
              - name: USER_MEM_ARGS
                value: -Djava.security.egd=file:/dev/./urandom
              - name: WDT_INSTALL_HOME
                value: /auxiliary/weblogic-deploy
              - name: WDT_MODEL_HOME
                value: /auxiliary/models
              - name: AUXILIARY_IMAGE_PATHS
                value: /auxiliary
              - name: ADMIN_USERNAME
              - name: ADMIN_PASSWORD
              image: image:latest
              imagePullPolicy: Always
              lifecycle:
                preStop:
                  exec:
                    command:
                    - /weblogic-operator/scripts/stopServer.sh
              livenessProbe:
                exec:
                  command:
                  - /weblogic-operator/scripts/livenessProbe.sh
                failureThreshold: 1
                initialDelaySeconds: 4
                periodSeconds: 6
                timeoutSeconds: 5
              name: weblogic-server
              ports:
              - containerPort: 8001
                name: default
                protocol: TCP
              readinessProbe:
                failureThreshold: 1
                httpGet:
                  path: /weblogic/ready
                  port: 8001
                initialDelaySeconds: 1
                periodSeconds: 3
                timeoutSeconds: 2
              resources:
                limits: {}
                requests:
                  memory: 768Mi
                  cpu: 250m
              securityContext: {}
              volumeMounts:
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/debug
                name: weblogic-domain-debug-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/introspector
                name: weblogic-domain-introspect-cm-volume
              - mountPath: /auxiliary
                name: compat-ai-vol-auxiliaryimagevolume1
            imagePullSecrets: []
            initContainers:
            - command:
              - /weblogic-operator/scripts/auxImage.sh
              env:
              - name: AUXILIARY_IMAGE_PATH
                value: /auxiliary
              - name: AUXILIARY_IMAGE_TARGET_PATH
                value: /tmpAuxiliaryImage
              - name: AUXILIARY_IMAGE_COMMAND
                value: cp -R $AUXILIARY_IMAGE_PATH/* $AUXILIARY_IMAGE_TARGET_PATH
              - name: AUXILIARY_IMAGE_CONTAINER_IMAGE
                value: model-in-image:WLS-AI-v1
              - name: AUXILIARY_IMAGE_CONTAINER_NAME
                value: compat-operator-aux-container1
              image: model-in-image:WLS-AI-v1
              imagePullPolicy: IfNotPresent
              name: compat-operator-aux-container1
              volumeMounts:
              - mountPath: /tmpAuxiliaryImage
                name: aux-image-volume-auxiliaryimagevolume1
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
            nodeSelector: {}
            securityContext: {}
            volumes:
            - configMap:
                defaultMode: 365
                name: weblogic-scripts-cm
              name: weblogic-scripts-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-debug-cm
                optional: true
              name: weblogic-domain-debug-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-introspect-cm
              name: weblogic-domain-introspect-cm-volume
            - emptyDir: {}
              name: compat-ai-vol-auxiliaryimagevolume1
          """;

  static final String ADMIN_ISTIO_MONITORING_EXPORTER_TCP_PROTOCOL =
      """
          metadata:
            annotations:
              prometheus.io/path: /metrics
              prometheus.io/port: '8080'
              prometheus.io/scrape: 'true'
              weblogic.sha256: fc8e3c36de352b0419844b393fd42bf01f32c67932a635cdedce230122d44d0a
            labels:
              weblogic.operatorVersion: 3.4.0
              weblogic.domainName: domain1
              weblogic.serverName: ADMIN_SERVER
              weblogic.domainRestartVersion: null
              weblogic.domainUID: uid1
              weblogic.createdByOperator: 'true'
              weblogic.clusterRestartVersion: null
              weblogic.serverRestartVersion: null
            name: uid1-admin-server
            namespace: namespace
          spec:
            containers:
            - args:
               - proxy
               - sidecar
              name: istio-proxy
              env:
              - name: ISTIO_KUBE_APP_PROBERS
                value: '{"/app-health/weblogic-server/readyz":{"httpGet":{"path":"/weblogic/ready","port":8888,"scheme":
                  "HTTP"},"timeoutSeconds":2, "failureThreshold":1, "initialDelaySeconds":1, "periodSeconds":3}}'
            - env:
              - name: JAVA_OPTS
                value: -DDOMAIN=uid1 -DWLS_PORT=7001
              image: monexp:latest
              imagePullPolicy: Always
              name: monitoring-exporter
              ports:
              - containerPort: 8080
                name: tcp-metrics
                protocol: TCP
            - command:
              - /weblogic-operator/scripts/startServer.sh
              env:
              - name: DOMAIN_NAME
                value: domain1
              - name: DOMAIN_HOME
                value: /u01/oracle/user_projects/domains
              - name: ADMIN_NAME
                value: ADMIN_SERVER
              - name: ADMIN_PORT
                value: '7001'
              - name: SERVER_NAME
                value: ADMIN_SERVER
              - name: DOMAIN_UID
                value: uid1
              - name: NODEMGR_HOME
                value: /u01/nodemanager
              - name: LOG_HOME
              - name: SERVER_OUT_IN_POD_LOG
                value: 'true'
              - name: SERVICE_NAME
                value: uid1-admin-server
              - name: AS_SERVICE_NAME
                value: uid1-admin-server
              - name: USER_MEM_ARGS
                value: -Djava.security.egd=file:/dev/./urandom
              - name: ADMIN_USERNAME
              - name: ADMIN_PASSWORD
              - name: LOCAL_ADMIN_PORT
                value: '7001'
              - name: LOCAL_ADMIN_PROTOCOL
                value: t3
              - name: SHUTDOWN_TYPE
                value: Graceful
              - name: SHUTDOWN_TIMEOUT
                value: '30'
              - name: SHUTDOWN_IGNORE_SESSIONS
                value: 'false'
              - name: DYNAMIC_CONFIG_OVERRIDE
                value: 'true'
              - name: DOMAIN_SOURCE_TYPE
                value: Image
              - name: INTERNAL_OPERATOR_CERT
                value: encoded-cert-data
              image: image:latest
              imagePullPolicy: Always
              lifecycle:
                preStop:
                  exec:
                    command:
                    - /weblogic-operator/scripts/stopServer.sh
              livenessProbe:
                exec:
                  command:
                  - /weblogic-operator/scripts/livenessProbe.sh
                failureThreshold: 1
                initialDelaySeconds: 4
                periodSeconds: 6
                timeoutSeconds: 5
              name: weblogic-server
              ports:
              - containerPort: 31400
                name: tcp-t3channel
                protocol: TCP
              - containerPort: 7001
                name: tcp-ldap
                protocol: TCP
              - containerPort: 7001
                name: tcp-default
                protocol: TCP
              - containerPort: 7001
                name: http-default
                protocol: TCP
              - containerPort: 7001
                name: tcp-snmp
                protocol: TCP
              - containerPort: 7001
                name: tcp-iiop
                protocol: TCP
              readinessProbe:
                failureThreshold: 1
                httpGet:
                  path: /weblogic/ready
                  port: 8888
                initialDelaySeconds: 1
                periodSeconds: 3
                timeoutSeconds: 2
              resources:
                limits: {}
                requests: {}
              securityContext: {}
              volumeMounts:
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/debug
                name: weblogic-domain-debug-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/introspector
                name: weblogic-domain-introspect-cm-volume
            hostname: uid1-admin-server
            imagePullSecrets: []
            initContainers: []
            nodeSelector: {}
            securityContext: {}
            terminationGracePeriodSeconds: 40
            volumes:
            - configMap:
                defaultMode: 365
                name: weblogic-scripts-cm
              name: weblogic-scripts-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-debug-cm
                optional: true
              name: weblogic-domain-debug-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-introspect-cm
              name: weblogic-domain-introspect-cm-volume
          """;

  static final String MANAGED_ISTIO_MONITORING_EXPORTER_TCP_PROTOCOL =
      """
          metadata:
            annotations:
              prometheus.io/path: /metrics
              prometheus.io/port: '8080'
              prometheus.io/scrape: 'true'
              weblogic.sha256: d3423494f6f84b64cb7a677c6c1825fcc8b6ae37722a73f74d99c5da6b936c16
            labels:
              weblogic.operatorVersion: 3.4.0
              weblogic.domainName: domain1
              weblogic.serverName: ess_server1
              weblogic.domainRestartVersion: null
              weblogic.domainUID: uid1
              weblogic.createdByOperator: 'true'
              weblogic.clusterRestartVersion: null
              weblogic.serverRestartVersion: null
            name: uid1-ess-server1
            namespace: namespace
          spec:
            containers:
            - args:
               - proxy
               - sidecar
              name: istio-proxy
              env:
              - name: ISTIO_KUBE_APP_PROBERS
                value: '{"/app-health/weblogic-server/readyz":{"httpGet":{"path":"/weblogic/ready","port":8888,"scheme":
                  "HTTP"},"timeoutSeconds":2, "failureThreshold":1, "initialDelaySeconds":1, "periodSeconds":3}}'
            - env:
              - name: JAVA_OPTS
                value: -DDOMAIN=uid1 -DWLS_PORT=8001
              image: monexp:latest
              imagePullPolicy: Always
              name: monitoring-exporter
              ports:
              - containerPort: 8080
                name: tcp-metrics
                protocol: TCP
            - command:
              - /weblogic-operator/scripts/startServer.sh
              env:
              - name: DOMAIN_NAME
                value: domain1
              - name: DOMAIN_HOME
                value: /u01/oracle/user_projects/domains
              - name: ADMIN_NAME
                value: ADMIN_SERVER
              - name: ADMIN_PORT
                value: '7001'
              - name: SERVER_NAME
                value: ess_server1
              - name: DOMAIN_UID
                value: uid1
              - name: NODEMGR_HOME
                value: /u01/nodemanager
              - name: LOG_HOME
              - name: SERVER_OUT_IN_POD_LOG
                value: 'true'
              - name: SERVICE_NAME
                value: uid1-ess-server1
              - name: AS_SERVICE_NAME
                value: uid1-admin-server
              - name: USER_MEM_ARGS
                value: -Djava.security.egd=file:/dev/./urandom
              - name: ADMIN_USERNAME
              - name: ADMIN_PASSWORD
              - name: LOCAL_ADMIN_PORT
                value: '8001'
              - name: LOCAL_ADMIN_PROTOCOL
                value: t3
              - name: SHUTDOWN_TYPE
                value: Graceful
              - name: SHUTDOWN_TIMEOUT
                value: '30'
              - name: SHUTDOWN_IGNORE_SESSIONS
                value: 'false'
              - name: DYNAMIC_CONFIG_OVERRIDE
                value: 'true'
              - name: DOMAIN_SOURCE_TYPE
                value: Image
              image: image:latest
              imagePullPolicy: Always
              lifecycle:
                preStop:
                  exec:
                    command:
                    - /weblogic-operator/scripts/stopServer.sh
              livenessProbe:
                exec:
                  command:
                  - /weblogic-operator/scripts/livenessProbe.sh
                failureThreshold: 1
                initialDelaySeconds: 4
                periodSeconds: 6
                timeoutSeconds: 5
              name: weblogic-server
              ports:
              - containerPort: 31400
                name: tcp-t3channel
                protocol: TCP
              - containerPort: 7001
                name: tcp-ldap
                protocol: TCP
              - containerPort: 7102
                name: tls-ldaps
                protocol: TCP
              - containerPort: 7001
                name: tcp-default
                protocol: TCP
              - containerPort: 7102
                name: tls-default
                protocol: TCP
              - containerPort: 7001
                name: http-default
                protocol: TCP
              - containerPort: 7102
                name: https-secure
                protocol: TCP
              - containerPort: 7001
                name: tcp-snmp
                protocol: TCP
              - containerPort: 7001
                name: tcp-iiop
                protocol: TCP
              - containerPort: 7102
                name: tls-iiops
                protocol: TCP
              readinessProbe:
                failureThreshold: 1
                httpGet:
                  path: /weblogic/ready
                  port: 8888
                initialDelaySeconds: 1
                periodSeconds: 3
                timeoutSeconds: 2
              resources:
                limits: {}
                requests: {}
              securityContext: {}
              volumeMounts:
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/debug
                name: weblogic-domain-debug-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/introspector
                name: weblogic-domain-introspect-cm-volume
            imagePullSecrets: []
            initContainers: []
            nodeSelector: {}
            securityContext: {}
            terminationGracePeriodSeconds: 40
            volumes:
            - configMap:
                defaultMode: 365
                name: weblogic-scripts-cm
              name: weblogic-scripts-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-debug-cm
                optional: true
              name: weblogic-domain-debug-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-introspect-cm
              name: weblogic-domain-introspect-cm-volume
          """;

  static final String ADMIN_MII_CONVERTED_AUX_IMAGE_POD_3_4_1 =
      """
          metadata:
            annotations:
              prometheus.io/path: /wls-exporter/metrics
              prometheus.io/port: '7001'
              prometheus.io/scrape: 'true'
              weblogic.sha256: 6bc3726c327773e20be8856c0555d8b25d37fbb82846e7b96060c5b639b7cce2
            labels:
              weblogic.domainName: domain1
              weblogic.serverName: ADMIN_SERVER
              weblogic.domainRestartVersion: null
              weblogic.domainUID: uid1
              weblogic.createdByOperator: 'true'
              weblogic.operatorVersion: 3.4.1
              weblogic.clusterRestartVersion: null
              weblogic.serverRestartVersion: null
            name: uid1-admin-server
            namespace: namespace
          spec:
            containers:
            - command:
              - /weblogic-operator/scripts/startServer.sh
              env:
              - name: DOMAIN_NAME
                value: domain1
              - name: DOMAIN_HOME
                value: /u01/oracle/user_projects/domains
              - name: ADMIN_NAME
                value: ADMIN_SERVER
              - name: ADMIN_PORT
                value: '7001'
              - name: SERVER_NAME
                value: ADMIN_SERVER
              - name: DOMAIN_UID
                value: uid1
              - name: NODEMGR_HOME
                value: /u01/nodemanager
              - name: LOG_HOME
              - name: SERVER_OUT_IN_POD_LOG
                value: 'true'
              - name: SERVICE_NAME
                value: uid1-admin-server
              - name: AS_SERVICE_NAME
                value: uid1-admin-server
              - name: USER_MEM_ARGS
                value: -Djava.security.egd=file:/dev/./urandom
              - name: WDT_INSTALL_HOME
                value: /auxiliary/weblogic-deploy
              - name: WDT_MODEL_HOME
                value: /auxiliary/models
              - name: AUXILIARY_IMAGE_PATHS
                value: /auxiliary
              - name: ADMIN_USERNAME
              - name: ADMIN_PASSWORD
              image: image:latest
              imagePullPolicy: Always
              lifecycle:
                preStop:
                  exec:
                    command:
                    - /weblogic-operator/scripts/stopServer.sh
              livenessProbe:
                exec:
                  command:
                  - /weblogic-operator/scripts/livenessProbe.sh
                failureThreshold: 1
                initialDelaySeconds: 4
                periodSeconds: 6
                timeoutSeconds: 5
              name: weblogic-server
              ports:
              - containerPort: 7001
                name: default
                protocol: TCP
              readinessProbe:
                failureThreshold: 1
                httpGet:
                  path: /weblogic/ready
                  port: 7001
                initialDelaySeconds: 1
                periodSeconds: 3
                timeoutSeconds: 2
              resources:
                limits: {}
                requests:
                  memory: 768Mi
                  cpu: 250m
              securityContext: {}
              volumeMounts:
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/debug
                name: weblogic-domain-debug-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/introspector
                name: weblogic-domain-introspect-cm-volume
              - mountPath: /auxiliary
                name: compat-ai-vol-auxiliaryimagevolume1
            hostname: uid1-admin-server
            imagePullSecrets: []
            initContainers:
            - command:
              - /weblogic-operator/scripts/auxImage.sh
              env:
              - name: AUXILIARY_IMAGE_PATH
                value: /auxiliary
              - name: AUXILIARY_IMAGE_TARGET_PATH
                value: /tmpAuxiliaryImage
              - name: AUXILIARY_IMAGE_COMMAND
                value: cp -R $AUXILIARY_IMAGE_PATH/* $AUXILIARY_IMAGE_TARGET_PATH
              - name: AUXILIARY_IMAGE_CONTAINER_IMAGE
                value: model-in-image:WLS-AI-v1
              - name: AUXILIARY_IMAGE_CONTAINER_NAME
                value: compat-operator-aux-container1
              image: model-in-image:WLS-AI-v1
              imagePullPolicy: IfNotPresent
              name: compat-operator-aux-container1
              resources:
                limits: {}
                requests:
                  memory: 768Mi
                  cpu: 250m
              volumeMounts:
              - mountPath: /tmpAuxiliaryImage
                name: compat-ai-vol-auxiliaryimagevolume1
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
            nodeSelector: {}
            securityContext: {}
            volumes:
            - configMap:
                defaultMode: 365
                name: weblogic-scripts-cm
              name: weblogic-scripts-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-debug-cm
                optional: true
              name: weblogic-domain-debug-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-introspect-cm
              name: weblogic-domain-introspect-cm-volume
            - emptyDir: {}
              name: compat-ai-vol-auxiliaryimagevolume1
          """;

  static final String MANAGED_MII_CONVERTED_AUX_IMAGE_POD_3_4_1 =
      """
          metadata:
            annotations:
              prometheus.io/path: /wls-exporter/metrics
              prometheus.io/port: '7001'
              prometheus.io/scrape: 'true'
              weblogic.sha256: e10514d80b1ebb8bf7f49b3cad19a249601ee32d2fc7e95686e6dfb7755b5682
            labels:
              weblogic.domainName: domain1
              weblogic.serverName: ess_server1
              weblogic.domainRestartVersion: null
              weblogic.domainUID: uid1
              weblogic.createdByOperator: 'true'
              weblogic.operatorVersion: 3.4.1
              weblogic.clusterRestartVersion: null
              weblogic.serverRestartVersion: null
            name: uid1-ess-server1
            namespace: namespace
          spec:
            containers:
            - command:
              - /weblogic-operator/scripts/startServer.sh
              env:
              - name: DOMAIN_NAME
                value: domain1
              - name: DOMAIN_HOME
                value: /u01/oracle/user_projects/domains
              - name: ADMIN_NAME
                value: ADMIN_SERVER
              - name: ADMIN_PORT
                value: '7001'
              - name: SERVER_NAME
                value: ess_server1
              - name: DOMAIN_UID
                value: uid1
              - name: NODEMGR_HOME
                value: /u01/nodemanager
              - name: LOG_HOME
              - name: SERVER_OUT_IN_POD_LOG
                value: 'true'
              - name: SERVICE_NAME
                value: uid1-ess-server1
              - name: AS_SERVICE_NAME
                value: uid1-admin-server
              - name: USER_MEM_ARGS
                value: -Djava.security.egd=file:/dev/./urandom
              - name: WDT_INSTALL_HOME
                value: /auxiliary/weblogic-deploy
              - name: WDT_MODEL_HOME
                value: /auxiliary/models
              - name: AUXILIARY_IMAGE_PATHS
                value: /auxiliary
              - name: ADMIN_USERNAME
              - name: ADMIN_PASSWORD
              image: image:latest
              imagePullPolicy: Always
              lifecycle:
                preStop:
                  exec:
                    command:
                    - /weblogic-operator/scripts/stopServer.sh
              livenessProbe:
                exec:
                  command:
                  - /weblogic-operator/scripts/livenessProbe.sh
                failureThreshold: 1
                initialDelaySeconds: 4
                periodSeconds: 6
                timeoutSeconds: 5
              name: weblogic-server
              ports:
              - containerPort: 8001
                name: default
                protocol: TCP
              readinessProbe:
                failureThreshold: 1
                httpGet:
                  path: /weblogic/ready
                  port: 8001
                initialDelaySeconds: 1
                periodSeconds: 3
                timeoutSeconds: 2
              resources:
                limits: {}
                requests:
                  memory: 768Mi
                  cpu: 250m
              securityContext: {}
              volumeMounts:
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/debug
                name: weblogic-domain-debug-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/introspector
                name: weblogic-domain-introspect-cm-volume
              - mountPath: /auxiliary
                name: compat-ai-vol-auxiliaryimagevolume1
            imagePullSecrets: []
            initContainers:
            - command:
              - /weblogic-operator/scripts/auxImage.sh
              env:
              - name: AUXILIARY_IMAGE_PATH
                value: /auxiliary
              - name: AUXILIARY_IMAGE_TARGET_PATH
                value: /tmpAuxiliaryImage
              - name: AUXILIARY_IMAGE_COMMAND
                value: cp -R $AUXILIARY_IMAGE_PATH/* $AUXILIARY_IMAGE_TARGET_PATH
              - name: AUXILIARY_IMAGE_CONTAINER_IMAGE
                value: model-in-image:WLS-AI-v1
              - name: AUXILIARY_IMAGE_CONTAINER_NAME
                value: compat-operator-aux-container1
              image: model-in-image:WLS-AI-v1
              imagePullPolicy: IfNotPresent
              name: compat-operator-aux-container1
              resources:
                limits: {}
                requests:
                  memory: 768Mi
                  cpu: 250m
              volumeMounts:
              - mountPath: /tmpAuxiliaryImage
                name: aux-image-volume-auxiliaryimagevolume1
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
            nodeSelector: {}
            securityContext: {}
            volumes:
            - configMap:
                defaultMode: 365
                name: weblogic-scripts-cm
              name: weblogic-scripts-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-debug-cm
                optional: true
              name: weblogic-domain-debug-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-introspect-cm
              name: weblogic-domain-introspect-cm-volume
            - emptyDir: {}
              name: compat-ai-vol-auxiliaryimagevolume1
          """;

  static final String ADMIN_MII_AUX_IMAGE_POD_4_0 =
      """
          metadata:
            annotations:
              prometheus.io/path: /wls-exporter/metrics
              prometheus.io/port: '7001'
              prometheus.io/scrape: 'true'
              weblogic.sha256: f8ce3566270c93f214545fd8398012c8ce7e48c9e591e3c502f71b3f83259407
            labels:
              weblogic.domainName: domain1
              weblogic.serverName: ADMIN_SERVER
              weblogic.domainRestartVersion: null
              weblogic.domainUID: uid1
              weblogic.createdByOperator: 'true'
              weblogic.operatorVersion: 4.0
              weblogic.clusterRestartVersion: null
              weblogic.serverRestartVersion: null
            name: uid1-admin-server
            namespace: namespace
          spec:
            containers:
            - command:
              - /weblogic-operator/scripts/startServer.sh
              env:
              - name: DOMAIN_NAME
                value: domain1
              - name: DOMAIN_HOME
                value: /u01/oracle/user_projects/domains
              - name: ADMIN_NAME
                value: ADMIN_SERVER
              - name: ADMIN_PORT
                value: '7001'
              - name: SERVER_NAME
                value: ADMIN_SERVER
              - name: DOMAIN_UID
                value: uid1
              - name: NODEMGR_HOME
                value: /u01/nodemanager
              - name: LOG_HOME
              - name: SERVER_OUT_IN_POD_LOG
                value: 'true'
              - name: SERVICE_NAME
                value: uid1-admin-server
              - name: AS_SERVICE_NAME
                value: uid1-admin-server
              - name: USER_MEM_ARGS
                value: -Djava.security.egd=file:/dev/./urandom
              - name: ADMIN_USERNAME
              - name: ADMIN_PASSWORD
              image: image:latest
              imagePullPolicy: Always
              lifecycle:
                preStop:
                  exec:
                    command:
                    - /weblogic-operator/scripts/stopServer.sh
              livenessProbe:
                exec:
                  command:
                  - /weblogic-operator/scripts/livenessProbe.sh
                failureThreshold: 1
                initialDelaySeconds: 4
                periodSeconds: 6
                timeoutSeconds: 5
              name: weblogic-server
              ports:
              - containerPort: 7001
                name: default
                protocol: TCP
              readinessProbe:
                failureThreshold: 1
                httpGet:
                  path: /weblogic/ready
                  port: 7001
                initialDelaySeconds: 1
                periodSeconds: 3
                timeoutSeconds: 2
              resources:
                limits: {}
                requests:
                  memory: 768Mi
                  cpu: 250m
              securityContext: {}
              volumeMounts:
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/debug
                name: weblogic-domain-debug-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/introspector
                name: weblogic-domain-introspect-cm-volume
              - mountPath: /auxiliary
                name: aux-image-volume-auxiliaryimagevolume1
            hostname: uid1-admin-server
            imagePullSecrets: []
            initContainers:
            - command:
              - /weblogic-operator/scripts/auxImage.sh
              env:
              - name: AUXILIARY_IMAGE_PATH
                value: /auxiliary
              - name: AUXILIARY_IMAGE_TARGET_PATH
                value: /tmpAuxiliaryImage
              - name: AUXILIARY_IMAGE_COMMAND
                value: cp -R $AUXILIARY_IMAGE_PATH/* $AUXILIARY_IMAGE_TARGET_PATH
              - name: AUXILIARY_IMAGE_CONTAINER_IMAGE
                value: model-in-image:WLS-AI-v1
              - name: AUXILIARY_IMAGE_CONTAINER_NAME
                value: operator-aux-container1
              image: model-in-image:WLS-AI-v1
              imagePullPolicy: IfNotPresent
              name: operator-aux-container1
              volumeMounts:
              - mountPath: /tmpAuxiliaryImage
                name: aux-image-volume-auxiliaryimagevolume1
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
            nodeSelector: {}
            securityContext: {}
            volumes:
            - configMap:
                defaultMode: 365
                name: weblogic-scripts-cm
              name: weblogic-scripts-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-debug-cm
                optional: true
              name: weblogic-domain-debug-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-introspect-cm
              name: weblogic-domain-introspect-cm-volume
            - emptyDir: {}
              name: aux-image-volume-auxiliaryimagevolume1
          """;

  static final String MANAGED_MII_AUX_IMAGE_POD_4_0 =
      """
          metadata:
            annotations:
              prometheus.io/path: /wls-exporter/metrics
              prometheus.io/port: '7001'
              prometheus.io/scrape: 'true'
              weblogic.sha256: 82ec0ede6f40b4cd03d5cc40c593f8552677a89b73e602df33afe967b378881a
            labels:
              weblogic.domainName: domain1
              weblogic.serverName: ess_server1
              weblogic.domainRestartVersion: null
              weblogic.domainUID: uid1
              weblogic.createdByOperator: 'true'
              weblogic.operatorVersion: 4.0
              weblogic.clusterRestartVersion: null
              weblogic.serverRestartVersion: null
            name: uid1-ess-server1
            namespace: namespace
          spec:
            containers:
            - command:
              - /weblogic-operator/scripts/startServer.sh
              env:
              - name: DOMAIN_NAME
                value: domain1
              - name: DOMAIN_HOME
                value: /u01/oracle/user_projects/domains
              - name: ADMIN_NAME
                value: ADMIN_SERVER
              - name: ADMIN_PORT
                value: '7001'
              - name: SERVER_NAME
                value: ess_server1
              - name: DOMAIN_UID
                value: uid1
              - name: NODEMGR_HOME
                value: /u01/nodemanager
              - name: LOG_HOME
              - name: SERVER_OUT_IN_POD_LOG
                value: 'true'
              - name: SERVICE_NAME
                value: uid1-ess-server1
              - name: AS_SERVICE_NAME
                value: uid1-admin-server
              - name: USER_MEM_ARGS
                value: -Djava.security.egd=file:/dev/./urandom
              - name: ADMIN_USERNAME
              - name: ADMIN_PASSWORD
              image: image:latest
              imagePullPolicy: Always
              lifecycle:
                preStop:
                  exec:
                    command:
                    - /weblogic-operator/scripts/stopServer.sh
              livenessProbe:
                exec:
                  command:
                  - /weblogic-operator/scripts/livenessProbe.sh
                failureThreshold: 1
                initialDelaySeconds: 4
                periodSeconds: 6
                timeoutSeconds: 5
              name: weblogic-server
              ports:
              - containerPort: 8001
                name: default
                protocol: TCP
              readinessProbe:
                failureThreshold: 1
                httpGet:
                  path: /weblogic/ready
                  port: 8001
                initialDelaySeconds: 1
                periodSeconds: 3
                timeoutSeconds: 2
              resources:
                limits: {}
                requests:
                  memory: 768Mi
                  cpu: 250m
              securityContext: {}
              volumeMounts:
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/debug
                name: weblogic-domain-debug-cm-volume
                readOnly: true
              - mountPath: /weblogic-operator/introspector
                name: weblogic-domain-introspect-cm-volume
              - mountPath: /auxiliary
                name: aux-image-volume-auxiliaryimagevolume1
            imagePullSecrets: []
            initContainers:
            - command:
              - /weblogic-operator/scripts/auxImage.sh
              env:
              - name: AUXILIARY_IMAGE_PATH
                value: /auxiliary
              - name: AUXILIARY_IMAGE_TARGET_PATH
                value: /tmpAuxiliaryImage
              - name: AUXILIARY_IMAGE_COMMAND
                value: cp -R $AUXILIARY_IMAGE_PATH/* $AUXILIARY_IMAGE_TARGET_PATH
              - name: AUXILIARY_IMAGE_CONTAINER_IMAGE
                value: model-in-image:WLS-AI-v1
              - name: AUXILIARY_IMAGE_CONTAINER_NAME
                value: operator-aux-container1
              image: model-in-image:WLS-AI-v1
              imagePullPolicy: IfNotPresent
              name: operator-aux-container1
              volumeMounts:
              - mountPath: /tmpAuxiliaryImage
                name: aux-image-volume-auxiliaryimagevolume1
              - mountPath: /weblogic-operator/scripts
                name: weblogic-scripts-cm-volume
            nodeSelector: {}
            securityContext: {}
            volumes:
            - configMap:
                defaultMode: 365
                name: weblogic-scripts-cm
              name: weblogic-scripts-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-debug-cm
                optional: true
              name: weblogic-domain-debug-cm-volume
            - configMap:
                defaultMode: 365
                name: uid1-weblogic-domain-introspect-cm
              name: weblogic-domain-introspect-cm-volume
            - emptyDir: {}
              name: aux-image-volume-auxiliaryimagevolume1
          """;
}
