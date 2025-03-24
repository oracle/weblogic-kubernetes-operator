// Copyright (c) 2017, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
//
def kind_k8s_map = [
    '0.23.0': [
        '1.30.0':  'kindest/node:v1.30.0@sha256:047357ac0cfea04663786a612ba1eaba9702bef25227a794b52890dd8bcd692e',
        '1.30':    'kindest/node:v1.30.0@sha256:047357ac0cfea04663786a612ba1eaba9702bef25227a794b52890dd8bcd692e',
        '1.29.4':  'kindest/node:v1.29.4@sha256:3abb816a5b1061fb15c6e9e60856ec40d56b7b52bcea5f5f1350bc6e2320b6f8',
        '1.29':    'kindest/node:v1.29.4@sha256:3abb816a5b1061fb15c6e9e60856ec40d56b7b52bcea5f5f1350bc6e2320b6f8',
        '1.28.9':  'kindest/node:v1.28.9@sha256:dca54bc6a6079dd34699d53d7d4ffa2e853e46a20cd12d619a09207e35300bd0',
        '1.28':    'kindest/node:v1.28.9@sha256:dca54bc6a6079dd34699d53d7d4ffa2e853e46a20cd12d619a09207e35300bd0',
        '1.27.13': 'kindest/node:v1.27.13@sha256:17439fa5b32290e3ead39ead1250dca1d822d94a10d26f1981756cd51b24b9d8',
        '1.27':    'kindest/node:v1.27.13@sha256:17439fa5b32290e3ead39ead1250dca1d822d94a10d26f1981756cd51b24b9d8',
        '1.26.15': 'kindest/node:v1.26.15@sha256:84333e26cae1d70361bb7339efb568df1871419f2019c80f9a12b7e2d485fe19',
        '1.26':    'kindest/node:v1.26.15@sha256:84333e26cae1d70361bb7339efb568df1871419f2019c80f9a12b7e2d485fe19',
        '1.25.16': 'kindest/node:v1.25.16@sha256:5da57dfc290ac3599e775e63b8b6c49c0c85d3fec771cd7d55b45fae14b38d3b',
        '1.25':    'kindest/node:v1.25.16@sha256:5da57dfc290ac3599e775e63b8b6c49c0c85d3fec771cd7d55b45fae14b38d3b'
    ]
]
def _kind_image = null

pipeline {
    agent { label 'large-ol9u4' }
    options {
        timeout(time: 800, unit: 'MINUTES')
    }

    tools {
        maven 'maven-3.8.7'
        jdk 'jdk21'
    }

    environment {
        ocir_host = "${env.WKT_OCIR_HOST}"
        wko_tenancy = "${env.WKT_TENANCY}"
        ocir_creds = 'wkt-ocir-creds'

        outdir = "${WORKSPACE}/staging"
        result_root = "${outdir}/wl_k8s_test_results"
        pv_root = "${outdir}/k8s-pvroot"
        kubeconfig_file = "${result_root}/kubeconfig"

        kind_name = "kind"
        kind_network = "kind"
        registry_name = "kind-registry"
        registry_host = "${registry_name}"
        registry_port = "5000"

        start_time = sh(script: 'date +"%Y-%m-%d %H:%M:%S"', returnStdout: true).trim()
        wle_download_url="https://github.com/oracle/weblogic-logging-exporter/releases/latest"
    }

    parameters {
        choice(name: 'MAVEN_PROFILE_NAME',
                description: 'Profile to use in mvn command to run the tests. Possible values are wls-srg (the default), integration-tests, toolkits-srg, kind-sequential and kind-upgrade. Refer to weblogic-kubernetes-operator/integration-tests/pom.xml on the branch.',
                choices: [
                        'wls-srg',
                        'integration-tests',
                        'kind-sequential',
                        'kind-upgrade',
                        'toolkits-srg'
                ]
        )
        string(name: 'IT_TEST',
               description: 'Comma separated list of individual It test classes to be run e.g., ItParameterizedDomain, ItMiiUpdateDomainConfig, ItMiiDynamicUpdate*, ItMiiMultiMode',
               defaultValue: ''
        )
        string(name: 'OPERATOR_LOG_LEVEL',
               description: 'The default log level is not set',
               defaultValue: ''
	)
        choice(name: 'KIND_VERSION',
               description: 'Kind version.',
               choices: [
                   '0.23.0'
               ]
        )
        choice(name: 'KUBE_VERSION',
               description: 'Kubernetes version. Supported values depend on the Kind version. Kind 0.23.0: 1.30, 1.30.0, 1.29, 1.29.4, 1.28, 1.28.9, 1.27, 1.27.13, 1.26, 1.26.15, 1.25, 1.25.16 ',
               choices: [
                    // The first item in the list is the default value...
                    '1.30.0',
                    '1.30',
                    '1.29.4',
                    '1.29',
                    '1.28.9',
                    '1.28',
                    '1.27.13',
                    '1.27',
                    '1.26.15',
                    '1.26',
                    '1.25.16',
                    '1.25'
               ]
        )
        string(name: 'HELM_VERSION',
               description: 'Helm version',
               defaultValue: '3.11.2'
        )
        choice(name: 'ISTIO_VERSION',
               description: 'Istio version',
               choices: [
                   '1.23.0',
                   '1.17.2',
                   '1.16.1',
                   '1.13.2',
                   '1.12.6',
                   '1.11.1',
                   '1.10.4',
                   '1.9.9'
               ]
        )	
        booleanParam(name: 'PARALLEL_RUN',
                     description: 'Runs tests in parallel. Default is true, test classes run in parallel.',
                     defaultValue: true
        )
        string(name: 'NUMBER_OF_THREADS',
               description: 'Number of threads to run the classes in parallel, default is 3.',
               defaultValue: "3"
        )
        string(name: 'WDT_DOWNLOAD_URL',
               description: 'URL to download WDT.',
               defaultValue: 'https://github.com/oracle/weblogic-deploy-tooling/releases/latest'
        )
        string(name: 'WIT_DOWNLOAD_URL',
               description: 'URL to download WIT.',
               defaultValue: 'https://github.com/oracle/weblogic-image-tool/releases/latest'
        )
        string(name: 'REMOTECONSOLE_VERSION',
               description: 'RemoteConsole version.',
               defaultValue: '2.4.7'
        )
        string(name: 'TEST_IMAGES_REPO',
               description: '',
               defaultValue: "${env.WKT_OCIR_HOST}"
        )
        choice(name: 'BASE_IMAGES_REPO',
               choices: ["${env.WKT_OCIR_HOST}", 'container-registry.oracle.com'],
               description: 'Repository to pull the base images. Make sure to modify the image names if you are modifying this parameter value.'
        )
        string(name: 'WEBLOGIC_IMAGE_NAME',
               description: 'WebLogic base image name. Default is the image name in BASE_IMAGES_REPO. Use middleware/weblogic for OCR.',
               defaultValue: "test-images/weblogic"
        )
        string(name: 'WEBLOGIC_IMAGE_TAG',
               description: '14.1.2.0-generic-jdk17-ol8, 14.1.2.0-generic-jdk17-ol9, 14.1.2.0-generic-jdk21-ol8, 14.1.2.0-generic-jdk21-ol9, 12.2.1.4,  12.2.1.4-dev(12.2.1.4-dev-ol7) , 12.2.1.4-slim(12.2.1.4-slim-ol7), 12.2.1.4-ol8, 12.2.1.4-dev-ol8, 12.2.1.4-slim-ol8, 14.1.1.0-11-ol7, 14.1.1.0-dev-11-ol7, 14.1.1.0-slim-11-ol7, 14.1.1.0-8-ol7, 14.1.1.0-dev-8-ol7, 14.1.1.0-slim-8-ol7, 14.1.1.0-11-ol8, 14.1.1.0-dev-11-ol8, 14.1.1.0-slim-11-ol8, 14.1.1.0-8-ol8, 14.1.1.0-dev-8-ol8, 14.1.1.0-slim-8-ol8',
               defaultValue: '14.1.2.0-generic-jdk17-ol8'
        )
        string(name: 'FMWINFRA_IMAGE_NAME',
               description: 'FWM Infra image name. Default is the image name in BASE_IMAGES_REPO. Use middleware/fmw-infrastructure for OCR.',
               defaultValue: "test-images/fmw-infrastructure"
        )
        string(name: 'FMWINFRA_IMAGE_TAG',
               description: '14.1.2.0-jdk17-ol8, 14.1.2.0-jdk17-ol9, 14.1.2.0-jdk21-ol8, 14.1.2.0-jdk21-ol9',
               defaultValue: '14.1.2.0-jdk17-ol8'
        )
        string(name: 'DB_IMAGE_NAME',
               description: 'Oracle DB image name. Default is the image name in BASE_IMAGES_REPO, use database/enterprise for OCR.',
               defaultValue: "test-images/database/enterprise"
        )
        string(name: 'DB_IMAGE_TAG',
               description: 'Oracle DB image tag',
               defaultValue: '12.2.0.1-slim'
        )
        string(name: 'MONITORING_EXPORTER_BRANCH',
               description: '',
               defaultValue: 'main'
        )
        string(name: 'MONITORING_EXPORTER_WEBAPP_VERSION',
               description: '',
               defaultValue: '2.3.0'
        )
        string(name: 'PROMETHEUS_CHART_VERSION',
               description: '',
               defaultValue: '17.0.0'
        )
        string(name: 'GRAFANA_CHART_VERSION',
               description: '',
               defaultValue: '6.38.6'
        )
        booleanParam(name: 'COLLECT_LOGS_ON_SUCCESS',
                     description: 'Collect logs for successful runs. Default is false.',
                     defaultValue: false
        )
    }

    stages {
        stage('Filter unwanted branches') {
            when {
                anyOf {
                    changeRequest()
                    branch 'main'
                    branch 'release/4.0'
                    branch 'release/3.4'
                }
            }
            stages {
                stage('Workaround JENKINS-41929 Parameters bug') {
                    steps {
                        echo 'Initialize parameters as environment variables due to https://issues.jenkins-ci.org/browse/JENKINS-41929'
                        evaluate """${def script = ""; params.each { k, v -> script += "env.${k} = '''${v}'''\n" }; return script}"""
                    }
                }
                stage ('Echo environment') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            env|sort
                            java -version
                            mvn --version
                            python --version
                            podman version
                            ulimit -a
                            ulimit -aH
                        '''
                        script {
                            def knd = params.KIND_VERSION
                            def k8s = params.KUBE_VERSION
                            if (knd != null && k8s != null) {
                                def k8s_map = kind_k8s_map.get(knd)
                                if (k8s_map != null) {
                                    _kind_image = k8s_map.get(k8s)
                                }
                                if (_kind_image == null) {
                                    currentBuild.result = 'ABORTED'
                                    error('Unable to compute _kind_image for Kind version ' +
                                            knd + ' and Kubernetes version ' + k8s)
                                }
                            } else {
                                currentBuild.result = 'ABORTED'
                                error('KIND_VERSION or KUBE_VERSION were null')
                            }
                            echo "Kind Image = ${_kind_image}"
                        }
                    }
                }

                stage('Build WebLogic Kubernetes Operator') {
                    steps {
                        withMaven(globalMavenSettingsConfig: 'wkt-maven-settings-xml', publisherStrategy: 'EXPLICIT') {
                            sh "mvn -DtrimStackTrace=false clean install"
                        }
                    }
                }

                stage('Make Workspace bin directory') {
                    steps {
                        sh "mkdir -m777 -p ${WORKSPACE}/bin"
                    }
                }

                stage('Install Helm') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            oci os object get --namespace=${wko_tenancy} --bucket-name=wko-system-test-files \
                                --name=helm/helm-v${HELM_VERSION}.tar.gz --file=helm.tar.gz \
                                --auth=instance_principal
                            tar zxf helm.tar.gz
                            mv linux-amd64/helm ${WORKSPACE}/bin/helm
                            rm -rf linux-amd64
                            helm version
                        '''
                    }
                }

                stage('Run Helm installation tests') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        withMaven(globalMavenSettingsConfig: 'wkt-maven-settings-xml', publisherStrategy: 'EXPLICIT') {
                            sh 'export PATH=${runtime_path} && mvn -pl kubernetes -P helm-installation-test verify'
                        }
                    }
                }

                stage ('Install kubectl') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                        KUBE_VERSION = "${params.KUBE_VERSION}"
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            oci os object get --namespace=${wko_tenancy} --bucket-name=wko-system-test-files \
                                --name=kubectl/kubectl-v${KUBE_VERSION} --file=${WORKSPACE}/bin/kubectl \
                                --auth=instance_principal
                            chmod +x ${WORKSPACE}/bin/kubectl
                            kubectl version --client=true
                        '''
                    }
                }

                stage('Install kind') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            oci os object get --namespace=${wko_tenancy} --bucket-name=wko-system-test-files \
                                --name=kind/kind-v${KIND_VERSION} --file=${WORKSPACE}/bin/kind \
                                --auth=instance_principal
                            chmod +x "${WORKSPACE}/bin/kind"
                            kind version
                        '''
                    }
                }

                stage('Preparing Integration Test Environment') {
                    steps {
                        sh 'mkdir -m777 -p ${result_root}'
                        echo "Results will be in ${result_root}"
                        sh 'mkdir -m777 -p ${pv_root}'
                        echo "Persistent volume files, if any, will be in ${pv_root}"
                    }
                }

                stage('Start registry container') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            running="$(docker inspect -f '{{.State.Running}}' "${registry_name}" 2>/dev/null || true)"
                            if [ "${running}" = 'true' ]; then
                              echo "Stopping the registry container ${registry_name}"
                              docker stop "${registry_name}"
                              docker rm --force "${registry_name}"
                            fi
        
                            docker run -d --restart=always -p "127.0.0.1:${registry_port}:5000" --name "${registry_name}" \
                                ${ocir_host}/${wko_tenancy}/test-images/docker/registry:2
                            echo "Registry Host: ${registry_host}"
                        '''
                    }
                }

                stage('Create kind cluster') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                        kind_image = sh(script: "echo -n ${_kind_image}", returnStdout: true).trim()
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            export KIND_EXPERIMENTAL_PROVIDER=podman

                            podman version
                            cat /etc/systemd/system/user@.service.d/delegate.conf
                            cat /etc/modules-load.d/iptables.conf
                            lsmod|grep -E "^ip_tables|^iptable_filter|^iptable_nat|^ip6"

                            if kind delete cluster --name ${kind_name} --kubeconfig "${kubeconfig_file}"; then
                                echo "Deleted orphaned kind cluster ${kind_name}"
                            fi
                            # settings needed by elastic logging tests
                            echo "running sudo sysctl -w vm.max_map_count=262144"
                            sudo sysctl -w vm.max_map_count=262144
                            cat <<EOF | kind create cluster --name "${kind_name}" --kubeconfig "${kubeconfig_file}" --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${registry_port}"]
    endpoint = ["http://${registry_host}:${registry_port}"]
nodes:
  - role: control-plane
    image: ${kind_image}
  - role: worker
    image: ${kind_image}
    extraPortMappings:
      - containerPort: 30511
        hostPort: 1511    
      - containerPort: 32480
        hostPort: 2480
      - containerPort: 32490
        hostPort: 2490    
      - containerPort: 30080
        hostPort: 2080
      - containerPort: 30443
        hostPort: 2043
      - containerPort: 30180
        hostPort: 2090
      - containerPort: 30143
        hostPort: 2053
      - containerPort: 31000
        hostPort: 2100
      - containerPort: 31004
        hostPort: 2104
      - containerPort: 31008
        hostPort: 2108
      - containerPort: 31012
        hostPort: 2112
      - containerPort: 31016
        hostPort: 2116
      - containerPort: 31020
        hostPort: 2120
      - containerPort: 31024
        hostPort: 2124
      - containerPort: 31028
        hostPort: 2128
      - containerPort: 31032
        hostPort: 2132
      - containerPort: 31036
        hostPort: 2136
      - containerPort: 31040
        hostPort: 2140
      - containerPort: 31044
        hostPort: 2144
      - containerPort: 31048
        hostPort: 2148
      - containerPort: 31052
        hostPort: 2152
      - containerPort: 31056
        hostPort: 2156
      - containerPort: 31060
        hostPort: 2160
      - containerPort: 31064
        hostPort: 2164
      - containerPort: 31068
        hostPort: 2168
      - containerPort: 31072
        hostPort: 2172
      - containerPort: 31076
        hostPort: 2176
      - containerPort: 31080
        hostPort: 2180
      - containerPort: 31084
        hostPort: 2184
      - containerPort: 31088
        hostPort: 2188
      - containerPort: 31092
        hostPort: 2192
      - containerPort: 31096 
        hostPort: 2196
      - containerPort: 31100
        hostPort: 2200
      - containerPort: 31104 
        hostPort: 2204
      - containerPort: 31108
        hostPort: 2208
      - containerPort: 31112
        hostPort: 2212
      - containerPort: 31116
        hostPort: 2216
      - containerPort: 31120
        hostPort: 2220
      - containerPort: 31124
        hostPort: 2224
      - containerPort: 31128
        hostPort: 2228
    extraMounts:
      - hostPath: ${pv_root}
        containerPath: ${pv_root}
kubeadmConfigPatches:
- |
  kind: KubeletConfiguration
  localStorageCapacityIsolation: true	
EOF

                            export KUBECONFIG=${kubeconfig_file}
                            kubectl cluster-info --context "kind-${kind_name}"

                            podman info
                            kubectl describe node

                            for node in $(kind get nodes --name "${kind_name}"); do
                                kubectl annotate node ${node} tilt.dev/registry=localhost:${registry_port};
                            done

                            # Document the local registry
                            # https://github.com/kubernetes/enhancements/tree/master/keps/sig-cluster-lifecycle/generic/1755-communicating-a-local-registry
                            cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:${registry_port}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF
                        '''
                    }
                }

                stage('Run integration tests') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        script {
                            def res = 0
                            res = sh(script: '''
                                    if [ -z "${IT_TEST}" ] && [ "${MAVEN_PROFILE_NAME}" = "integration-tests" ]; then
                                       echo 'ERROR: All tests cannot be run with integration-tests profile'
                                       exit 1
                                    fi
                                ''', returnStatus: true)
                            if (res != 0 ) {
                                currentBuild.result = 'ABORTED'
                                error('Profile/ItTests Validation Failed')
                            }
                        }

                        sh '''
                            export PATH=${runtime_path}
                            export KUBECONFIG=${kubeconfig_file}
                            mkdir -m777 -p "${WORKSPACE}/.mvn"
                            touch ${WORKSPACE}/.mvn/maven.config
                            K8S_NODEPORT_HOST=$(kubectl get node kind-worker -o jsonpath='{.status.addresses[?(@.type == "InternalIP")].address}')
                            if [ "${MAVEN_PROFILE_NAME}" == "kind-sequential" ]; then
                                PARALLEL_RUN='false'
                            elif [ "${MAVEN_PROFILE_NAME}" == "kind-upgrade" ]; then
                                PARALLEL_RUN='false'
                            elif [ -n "${IT_TEST}" ]; then
                                echo 'Overriding MAVEN_PROFILE_NAME to integration-test when running individual test(s)'
                                MAVEN_PROFILE_NAME="integration-tests"
                                echo "-Dit.test=\"${IT_TEST}\"" >> ${WORKSPACE}/.mvn/maven.config
                            fi
			    echo "-Dmaven.wagon.http.retryHandler.count=3"                                               >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.wle.download.url=\"${wle_download_url}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.result.root=\"${result_root}\""                                               >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.pv.root=\"${pv_root}\""                                                       >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.k8s.nodeport.host=\"${K8S_NODEPORT_HOST}\""                                   >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.kind.repo=\"localhost:${registry_port}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.istio.version=\"${ISTIO_VERSION}\""                                           >> ${WORKSPACE}/.mvn/maven.config
                            echo "-DPARALLEL_CLASSES=\"${PARALLEL_RUN}\""                                                >> ${WORKSPACE}/.mvn/maven.config
                            echo "-DNUMBER_OF_THREADS=\"${NUMBER_OF_THREADS}\""                                          >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.wdt.download.url=\"${WDT_DOWNLOAD_URL}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.wit.download.url=\"${WIT_DOWNLOAD_URL}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.base.images.repo=\"${BASE_IMAGES_REPO}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.base.images.tenancy=\"${wko_tenancy}\""                                       >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.test.images.repo=\"${TEST_IMAGES_REPO}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.test.images.tenancy=\"${wko_tenancy}\""                                       >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.weblogic.image.name=\"${WEBLOGIC_IMAGE_NAME}\""                               >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.weblogic.image.tag=\"${WEBLOGIC_IMAGE_TAG}\""                                 >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.fmwinfra.image.name=\"${FMWINFRA_IMAGE_NAME}\""                               >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.fmwinfra.image.tag=\"${FMWINFRA_IMAGE_TAG}\""                                 >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.db.image.name=\"${DB_IMAGE_NAME}\""                                           >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.db.image.tag=\"${DB_IMAGE_TAG}\""                                             >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.monitoring.exporter.branch=\"${MONITORING_EXPORTER_BRANCH}\""                 >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.monitoring.exporter.webapp.version=\"${MONITORING_EXPORTER_WEBAPP_VERSION}\"" >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.prometheus.chart.version=\"${PROMETHEUS_CHART_VERSION}\""                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.grafana.chart.version=\"${GRAFANA_CHART_VERSION}\""                           >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.collect.logs.on.success=\"${COLLECT_LOGS_ON_SUCCESS}\""                       >> ${WORKSPACE}/.mvn/maven.config
                            echo "-DWLSIMG_BUILDER=\"podman\""                                                           >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.remoteconsole.version=\"${REMOTECONSOLE_VERSION}\""                           >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Djdk.httpclient.allowRestrictedHeaders=\"host\""                                      >> ${WORKSPACE}/.mvn/maven.config    

                            echo "${WORKSPACE}/.mvn/maven.config contents:"
                            cat "${WORKSPACE}/.mvn/maven.config"
                            cp "${WORKSPACE}/.mvn/maven.config" "${result_root}"
                            kubectl describe node kind-worker
                        '''
                        withMaven(globalMavenSettingsConfig: 'wkt-maven-settings-xml', publisherStrategy: 'EXPLICIT') {
                            withCredentials([
                                usernamePassword(credentialsId: "${ocir_creds}", usernameVariable: 'OCIR_USER', passwordVariable: 'OCIR_PASS')
                            ]) {
                                sh '''
                                    export PATH=${runtime_path}
                                    export KUBECONFIG=${kubeconfig_file}
                                    export BASE_IMAGES_REPO_USERNAME="${OCIR_USER}"
                                    export BASE_IMAGES_REPO_PASSWORD="${OCIR_PASS}"
                                    export BASE_IMAGES_REPO_EMAIL="noreply@oracle.com"
                                    export TEST_IMAGES_REPO_USERNAME="${OCIR_USER}"
                                    export TEST_IMAGES_REPO_PASSWORD="${OCIR_PASS}"
                                    export TEST_IMAGES_REPO_EMAIL="noreply@oracle.com"
                                    if ! time mvn -pl integration-tests -P ${MAVEN_PROFILE_NAME} verify 2>&1 | tee "${result_root}/kindtest.log"; then
                                        echo "integration-tests failed"
                                        exit 1
                                    fi
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            sh '''
                                export PATH="${WORKSPACE}/bin:${PATH}"
                                export KUBECONFIG=${kubeconfig_file}
                                mkdir -m777 -p ${result_root}/kubelogs
                                if ! kind export logs "${result_root}/kubelogs" --name "${kind_name}" --verbosity 99; then
                                    echo "Failed to export kind logs for kind cluster ${kind_name}"
                                fi
                                if ! docker exec kind-worker journalctl --utc --dmesg --system > "${result_root}/journalctl-kind-worker.out"; then
                                    echo "Failed to run journalctl for kind worker"
                                fi
                                if ! docker exec kind-control-plane journalctl --utc --dmesg --system > "${result_root}/journalctl-kind-control-plane.out"; then
                                    echo "Failed to run journalctl for kind control plane"
                                fi
                                if ! journalctl --utc --dmesg --system --since "$start_time" > "${result_root}/journalctl-compute.out"; then
                                    echo "Failed to run journalctl for compute node"
                                fi

                                mkdir -m777 -p "${WORKSPACE}/logdir/${BUILD_TAG}/wl_k8s_test_results"
                                sudo mv -f ${result_root}/* "${WORKSPACE}/logdir/${BUILD_TAG}/wl_k8s_test_results"
                            '''
                            archiveArtifacts(artifacts:
                            "logdir/${BUILD_TAG}/wl_k8s_test_results/diagnostics/**/*,logdir/${BUILD_TAG}/wl_k8s_test_results/workdir/liftandshiftworkdir/**/*")
                            junit(testResults: 'integration-tests/target/failsafe-reports/*.xml', allowEmptyResults: true)
                        }
                    }
                }
            }
            post {
                always {
                    sh '''
                        export PATH="${WORKSPACE}/bin:${PATH}"
                        running="$(docker inspect -f '{{.State.Running}}' "${registry_name}" 2>/dev/null || true)"
                        if [ "${running}" = 'true' ]; then
                            echo "Stopping the registry container ${registry_name}"
                            docker stop "${registry_name}"
                            docker rm --force "${registry_name}"
                        fi
                        echo 'Remove old Kind cluster (if any)...'
                        if ! kind delete cluster --name ${kind_name} --kubeconfig "${kubeconfig_file}"; then
                            echo "Failed to delete kind cluster ${kind_name}"
                        fi
                    '''
                }
            }
        }
        stage ('Sync') {
            when {
                anyOf {
                    branch 'main'
                    branch 'release/4.0'
                    branch 'release/3.4'
                }
                anyOf {
                    not { triggeredBy 'TimerTrigger' }
                    tag 'v*'
                }
            }
            steps {
                build job: "wkt-sync", parameters: [ string(name: 'REPOSITORY', value: 'weblogic-kubernetes-operator') ]
            }
        }
    }
}

