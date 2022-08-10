// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
//
import groovy.json.JsonSlurper
import java.time.Instant
import java.time.temporal.ChronoUnit

def kind_k8s_map = [
    '0.11.1': [
        '1.23.3':  'kindest/node:v1.23.3@sha256:0cb1a35ccd539118ce38d29a97823bae8fcef22fc94e9e33c0f4fadcdf9d4059',
        '1.23':    'kindest/node:v1.23.3@sha256:0cb1a35ccd539118ce38d29a97823bae8fcef22fc94e9e33c0f4fadcdf9d4059',
        '1.22.5':  'kindest/node:v1.22.5@sha256:a2b3127dd056f04e9fef46cc153a9452f5a4a09e818528da746f4a3b45148c74',
        '1.22':    'kindest/node:v1.22.5@sha256:a2b3127dd056f04e9fef46cc153a9452f5a4a09e818528da746f4a3b45148c74',
        '1.21.1':  'kindest/node:v1.21.1@sha256:69860bda5563ac81e3c0057d654b5253219618a22ec3a346306239bba8cfa1a6',
        '1.21':    'kindest/node:v1.21.1@sha256:69860bda5563ac81e3c0057d654b5253219618a22ec3a346306239bba8cfa1a6',
        '1.20.7':  'kindest/node:v1.20.7@sha256:cbeaf907fc78ac97ce7b625e4bf0de16e3ea725daf6b04f930bd14c67c671ff9',
        '1.20':    'kindest/node:v1.20.7@sha256:cbeaf907fc78ac97ce7b625e4bf0de16e3ea725daf6b04f930bd14c67c671ff9',
        '1.19.11': 'kindest/node:v1.19.11@sha256:07db187ae84b4b7de440a73886f008cf903fcf5764ba8106a9fd5243d6f32729',
        '1.19':    'kindest/node:v1.19.11@sha256:07db187ae84b4b7de440a73886f008cf903fcf5764ba8106a9fd5243d6f32729'
    ],
    '0.12.0': [
        '1.23.4':  'kindest/node:v1.23.4@sha256:0e34f0d0fd448aa2f2819cfd74e99fe5793a6e4938b328f657c8e3f81ee0dfb9',
        '1.23':    'kindest/node:v1.23.4@sha256:0e34f0d0fd448aa2f2819cfd74e99fe5793a6e4938b328f657c8e3f81ee0dfb9',
        '1.22.7':  'kindest/node:v1.22.7@sha256:1dfd72d193bf7da64765fd2f2898f78663b9ba366c2aa74be1fd7498a1873166',
        '1.22':    'kindest/node:v1.22.7@sha256:1dfd72d193bf7da64765fd2f2898f78663b9ba366c2aa74be1fd7498a1873166',
        '1.21.10': 'kindest/node:v1.21.10@sha256:84709f09756ba4f863769bdcabe5edafc2ada72d3c8c44d6515fc581b66b029c',
        '1.21':    'kindest/node:v1.21.10@sha256:84709f09756ba4f863769bdcabe5edafc2ada72d3c8c44d6515fc581b66b029c',
        '1.20.15': 'kindest/node:v1.20.15@sha256:393bb9096c6c4d723bb17bceb0896407d7db581532d11ea2839c80b28e5d8deb',
        '1.20':    'kindest/node:v1.20.15@sha256:393bb9096c6c4d723bb17bceb0896407d7db581532d11ea2839c80b28e5d8deb'
    ],
    '0.13.0': [
        '1.24.0':  'kindest/node:v1.24.0@sha256:406fd86d48eaf4c04c7280cd1d2ca1d61e7d0d61ddef0125cb097bc7b82ed6a1',
        '1.24':    'kindest/node:v1.24.0@sha256:406fd86d48eaf4c04c7280cd1d2ca1d61e7d0d61ddef0125cb097bc7b82ed6a1',
        '1.23.6':  'kindest/node:v1.23.6@sha256:1af0f1bee4c3c0fe9b07de5e5d3fafeb2eec7b4e1b268ae89fcab96ec67e8355',
        '1.23':    'kindest/node:v1.23.6@sha256:1af0f1bee4c3c0fe9b07de5e5d3fafeb2eec7b4e1b268ae89fcab96ec67e8355',
        '1.22.9':  'kindest/node:v1.22.9@sha256:6e57a6b0c493c7d7183a1151acff0bfa44bf37eb668826bf00da5637c55b6d5e',
        '1.22':    'kindest/node:v1.22.9@sha256:6e57a6b0c493c7d7183a1151acff0bfa44bf37eb668826bf00da5637c55b6d5e',
        '1.21.12': 'kindest/node:v1.21.12@sha256:ae05d44cc636ee961068399ea5123ae421790f472c309900c151a44ee35c3e3e',
        '1.21':    'kindest/node:v1.21.12@sha256:ae05d44cc636ee961068399ea5123ae421790f472c309900c151a44ee35c3e3e',
        '1.20.15': 'kindest/node:v1.20.15@sha256:a6ce604504db064c5e25921c6c0fffea64507109a1f2a512b1b562ac37d652f3',
        '1.20':    'kindest/node:v1.20.15@sha256:a6ce604504db064c5e25921c6c0fffea64507109a1f2a512b1b562ac37d652f3'
    ],
    '0.14.0': [
        '1.24.0':  'kindest/node:v1.24.0@sha256:0866296e693efe1fed79d5e6c7af8df71fc73ae45e3679af05342239cdc5bc8e',
        '1.24':    'kindest/node:v1.24.0@sha256:0866296e693efe1fed79d5e6c7af8df71fc73ae45e3679af05342239cdc5bc8e',
        '1.23.6':  'kindest/node:v1.23.6@sha256:b1fa224cc6c7ff32455e0b1fd9cbfd3d3bc87ecaa8fcb06961ed1afb3db0f9ae',
        '1.23':    'kindest/node:v1.23.6@sha256:b1fa224cc6c7ff32455e0b1fd9cbfd3d3bc87ecaa8fcb06961ed1afb3db0f9ae',
        '1.22.9':  'kindest/node:v1.22.9@sha256:8135260b959dfe320206eb36b3aeda9cffcb262f4b44cda6b33f7bb73f453105',
        '1.22':    'kindest/node:v1.22.9@sha256:8135260b959dfe320206eb36b3aeda9cffcb262f4b44cda6b33f7bb73f453105',
        '1.21.12': 'kindest/node:v1.21.12@sha256:f316b33dd88f8196379f38feb80545ef3ed44d9197dca1bfd48bcb1583210207',
        '1.21':    'kindest/node:v1.21.12@sha256:f316b33dd88f8196379f38feb80545ef3ed44d9197dca1bfd48bcb1583210207',
        '1.20.15': 'kindest/node:v1.20.15@sha256:6f2d011dffe182bad80b85f6c00e8ca9d86b5b8922cdf433d53575c4c5212248',
        '1.20':    'kindest/node:v1.20.15@sha256:6f2d011dffe182bad80b85f6c00e8ca9d86b5b8922cdf433d53575c4c5212248'
    ]
]
def _kind_image = null

def printLatestChanges() {
    // Show the latest changes for toolkit projects to help with troubleshooting build failures
    def projectMap = [ 'WIT': 'https://api.github.com/repos/oracle/weblogic-image-tool/commits',
                       'WDT': 'https://api.github.com/repos/oracle/weblogic-deploy-tooling/commits']
    def result = new StringBuilder().append("Project changes in last 2 days:")
    def since = Instant.now().minus(2, ChronoUnit.DAYS).toString()
    for ( def project in projectMap.entrySet() ) {
        def projectCommitsResp = httpRequest project.value + '?since=' + since
        if(projectCommitsResp.getStatus() == 200) {
            def projectCommits = new JsonSlurper().parseText( projectCommitsResp.getContent() )
            projectCommits.each{
                result.append('\n').append(project.key).append(' : ').append(it.commit.message)
            }
        } else {
            result.append('\n').append(project.key).append(' : HTTP ERROR, failed to get commits')
        }
    }
    print result
}

pipeline {
    agent { label 'VM.Standard2.8' }
    options {
        timeout(time: 800, unit: 'MINUTES')
    }

    tools {
        maven 'maven-3.8.5'
        jdk 'OpenJDK 17.0.2'
    }

    environment {
        github_url = "${env.GIT_URL}"
        github_creds = 'ecnj_github'
        dockerhub_username_creds = 'docker-username'
        dockerhub_password_creds = 'docker-password'
        ocr_username_creds = 'OCR username'
        ocr_password_creds = 'OCR Password'
        ocir_registry_creds = 'ocir-server'
        ocir_email_creds = 'ocir-email'
        ocir_username_creds = 'ocir-username'
        ocir_password_creds = 'ocir-token'
        image_pull_secret_weblogic_creds = 'image-pull-secret-weblogic'

        sonar_project_key = 'oracle_weblogic-kubernetes-operator'
        sonar_github_repo = 'oracle/weblogic-kubernetes-operator'
        sonar_webhook_secret_creds = 'SonarCloud WebHook Secret'

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
                description: 'Profile to use in mvn command to run the tests.  Possible values are wls-srg (the default), integration-tests, toolkits-srg, and kind-sequential.  Refer to weblogic-kubernetes-operator/integration-tests/pom.xml on the branch.',
                choices: [
                        'wls-srg',
                        'integration-tests',
                        'kind-sequential',
                        'toolkits-srg'
                ]
        )
        string(name: 'IT_TEST',
               description: 'Comma separated list of individual It test classes to be run e.g., ItParameterizedDomain, ItMiiUpdateDomainConfig, ItMiiDynamicUpdate*, ItMiiMultiMode',
               defaultValue: ''
        )
        choice(name: 'KIND_VERSION',
               description: 'Kind version.',
               choices: [
                   '0.14.0',
                   '0.13.0',
                   '0.12.0',
                   '0.11.1'
               ]
        )
        choice(name: 'KUBE_VERSION',
               description: 'Kubernetes version. Supported values depend on the Kind version. Kind 0.13.0 and 0.14.0: 1.24, 1.24.0, 1.23, 1.23.6, 1.22, 1.22.9, 1.21, 1.21.12, 1.20, 1.20.15, Kind 0.12.0: 1.23, 1.23.4, 1.22, 1.22.7, 1.21, 1.21.10, 1.20, 1.20.15. Kind 0.11.1: 1.23, 1.23.3, 1.22, 1.22.5, 1.21, 1.21.1, 1.20, 1.20.7, 1.19, 1.19.11.',
               choices: [
                    // The first item in the list is the default value...
                    '1.21.12',
                    '1.24',
                    '1.24.0',
                    '1.23.6',
                    '1.23.4',
                    '1.23.3',
                    '1.23',
                    '1.22.9',
                    '1.22.7',
                    '1.22.5',
                    '1.22',
                    '1.21.10',
                    '1.21.1',
                    '1.21',
                    '1.20.15',
                    '1.20.7',
                    '1.20',
                    '1.19.11',
                    '1.19'
               ]
        )
        string(name: 'KUBECTL_VERSION',
               description: 'kubectl version',
               defaultValue: '1.21.5'
        )
        string(name: 'HELM_VERSION',
               description: 'Helm version',
               defaultValue: '3.7.2'
        )
        choice(name: 'ISTIO_VERSION',
               description: 'Istio version',
               choices: [
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
        string(name: 'TEST_IMAGES_REPO',
               description: '',
               defaultValue: 'phx.ocir.io'
        )
        choice(name: 'BASE_IMAGES_REPO',
               choices: ['phx.ocir.io', 'container-registry.oracle.com'],
               description: 'Repository to pull the base images. Make sure to modify the image names if you are modifying this parameter value.'
        )
        string(name: 'WEBLOGIC_IMAGE_NAME',
               description: 'WebLogic base image name. Default is the image name in BASE_IMAGES_REPO. Use middleware/weblogic for OCR.',
               defaultValue: 'weblogick8s/test-images/weblogic'
        )
        string(name: 'WEBLOGIC_IMAGE_TAG',
               description: '12.2.1.3  (12.2.1.3-ol7) , 12.2.1.3-dev  (12.2.1.3-dev-ol7), 12.2.1.3-ol8, 12.2.1.3-dev-ol8, 12.2.1.4,  12.2.1.4-dev(12.2.1.4-dev-ol7) , 12.2.1.4-slim(12.2.1.4-slim-ol7), 12.2.1.4-ol8, 12.2.1.4-dev-ol8, 12.2.1.4-slim-ol8, 14.1.1.0-11-ol7, 14.1.1.0-dev-11-ol7, 14.1.1.0-slim-11-ol7, 14.1.1.0-8-ol7, 14.1.1.0-dev-8-ol7, 14.1.1.0-slim-8-ol7, 14.1.1.0-11-ol8, 14.1.1.0-dev-11-ol8, 14.1.1.0-slim-11-ol8, 14.1.1.0-8-ol8, 14.1.1.0-dev-8-ol8, 14.1.1.0-slim-8-ol8',
               defaultValue: '12.2.1.4'
        )
        string(name: 'FMWINFRA_IMAGE_NAME',
               description: 'FWM Infra image name. Default is the image name in BASE_IMAGES_REPO. Use middleware/fmw-infrastructure for OCR.',
               defaultValue: 'weblogick8s/test-images/fmw-infrastructure'
        )
        string(name: 'FMWINFRA_IMAGE_TAG',
               description: 'FWM Infra image tag',
               defaultValue: '12.2.1.4'
        )
        string(name: 'DB_IMAGE_NAME',
               description: 'Oracle DB image name. Default is the image name in BASE_IMAGES_REPO, use database/enterprise for OCR.',
               defaultValue: 'weblogick8s/test-images/database/enterprise'
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
               defaultValue: '2.0.7'
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
                    branch 'release/3.3'
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
                            docker version
                            ulimit -a
                            ulimit -aH
                        '''
                        printLatestChanges()
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

                // Use explicit checkout so that we can clean up the workspace.
                // Cannot use skipDefaultCheckout option since we want to use
                // the GIT_COMMIT environment variable to make sure that we are
                // checking out the exactly commit that triggered the build.
                //
                stage('GitHub Checkout') {
                    steps {
                        sh "sudo rm -rf ${WORKSPACE}/*"
                        checkout([$class: 'GitSCM', branches: [[name: "${GIT_COMMIT}"]],
                                  doGenerateSubmoduleConfigurations: false,
                                  extensions: [], submoduleCfg: [],
                                  userRemoteConfigs: [[credentialsId: "${github_creds}", url: "${github_url}"]]])
                    }
                }

                stage('Build WebLogic Kubernetes Operator') {
                    environment {
                        DOCKERHUB_USERNAME = credentials("${dockerhub_username_creds}")
                        DOCKERHUB_PASSWORD = credentials("${dockerhub_password_creds}")
                    }
                    steps {
                        sh 'echo ${DOCKERHUB_PASSWORD} | docker login -u ${DOCKERHUB_USERNAME} --password-stdin'
                        sh "mvn -DtrimStackTrace=false clean install"
                    }
                }

                stage('Run Sonar Analysis') {
                    steps {
                        sh '''
                            rm -rf ${WORKSPACE}/.mvn/maven.config
                            mkdir -p ${WORKSPACE}/.mvn
                            touch ${WORKSPACE}/.mvn/maven.config
                            echo "-Dsonar.projectKey=${sonar_project_key}"                        >> ${WORKSPACE}/.mvn/maven.config
                            if [ -z "${CHANGE_ID}" ]; then
                                echo "-Dsonar.branch.name=${BRANCH_NAME}"                         >> ${WORKSPACE}/.mvn/maven.config
                            else
                                echo "-Dsonar.pullrequest.provider=GitHub"                        >> ${WORKSPACE}/.mvn/maven.config
                                echo "-Dsonar.pullrequest.github.repository=${sonar_github_repo}" >> ${WORKSPACE}/.mvn/maven.config
                                echo "-Dsonar.pullrequest.key=${CHANGE_ID}"                       >> ${WORKSPACE}/.mvn/maven.config
                                echo "-Dsonar.pullrequest.branch=${CHANGE_BRANCH}"                >> ${WORKSPACE}/.mvn/maven.config
                                echo "-Dsonar.pullrequest.base=${CHANGE_TARGET}"                  >> ${WORKSPACE}/.mvn/maven.config
                            fi
                            echo "${WORKSPACE}/.mvn/maven.config contents:"
                            cat "${WORKSPACE}/.mvn/maven.config"
                        '''
                        withSonarQubeEnv('SonarCloud') {
                            // For whatever reason, defining this property in the maven.config file is not working...
                            //
                            sh "mvn sonar:sonar"
                        }
                    }
                }

                 stage('Verify Sonar Quality Gate') {
                    steps {
                        timeout(time: 10, unit: 'MINUTES') {
                            // Set abortPipeline to true to stop the build if the Quality Gate is not met.
                            waitForQualityGate(abortPipeline: false, webhookSecretId: "${sonar_webhook_secret_creds}")
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
                            curl -Lo "helm.tar.gz" "https://objectstorage.us-phoenix-1.oraclecloud.com/n/weblogick8s/b/wko-system-test-files/o/helm%2Fhelm-v${HELM_VERSION}.tar.gz"
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
                        sh '''
                            export PATH=${runtime_path}
                            mvn -pl kubernetes -P helm-installation-test verify
                        '''
                    }
                }

                stage ('Install kubectl') {
                    environment {
                        runtime_path = "${WORKSPACE}/bin:${PATH}"
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            curl -Lo "${WORKSPACE}/bin/kubectl" "https://objectstorage.us-phoenix-1.oraclecloud.com/n/weblogick8s/b/wko-system-test-files/o/kubectl%2Fkubectl-v${KUBECTL_VERSION}"
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
                            curl -Lo "${WORKSPACE}/bin/kind" "https://objectstorage.us-phoenix-1.oraclecloud.com/n/weblogick8s/b/wko-system-test-files/o/kind%2Fkind-v${KIND_VERSION}"
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
        
                            docker run -d --restart=always -p "127.0.0.1:${registry_port}:5000" --name "${registry_name}" phx.ocir.io/weblogick8s/test-images/docker/registry:2
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
                            if kind delete cluster --name ${kind_name} --kubeconfig "${kubeconfig_file}"; then
                                echo "Deleted orphaned kind cluster ${kind_name}"
                            fi
                            cat <<EOF | kind create cluster --name "${kind_name}" --kubeconfig "${kubeconfig_file}" --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
networking:
  podSubnet: 192.168.0.0/16
containerdConfigPatches:
- |-
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${registry_port}"]
    endpoint = ["http://${registry_host}:${registry_port}"]
nodes:
  - role: control-plane
    image: ${kind_image}
  - role: worker
    image: ${kind_image}
    extraMounts:
      - hostPath: ${pv_root}
        containerPath: ${pv_root}
EOF

                            export KUBECONFIG=${kubeconfig_file}
                            kubectl cluster-info --context "kind-${kind_name}"

                            for node in $(kind get nodes --name "${kind_name}"); do
                                kubectl annotate node ${node} tilt.dev/registry=localhost:${registry_port};
                            done

                            if [ "${kind_network}" != "bridge" ]; then
                                containers=$(docker network inspect ${kind_network} -f "{{range .Containers}}{{.Name}} {{end}}")
                                needs_connect="true"
                                for c in ${containers}; do
                                    if [ "$c" = "${registry_name}" ]; then
                                        needs_connect="false"
                                    fi
                                done
                                if [ "${needs_connect}" = "true" ]; then
                                    docker network connect "${kind_network}" "${registry_name}" || true
                                fi
                            fi

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
                        IMAGE_PULL_SECRET_WEBLOGIC = credentials("${image_pull_secret_weblogic_creds}")
                        BASE_IMAGES_REPO = credentials("${ocir_registry_creds}")
                        BASE_IMAGES_REPO_USERNAME = credentials("${ocir_username_creds}")
                        BASE_IMAGES_REPO_PASSWORD = credentials("${ocir_password_creds}")
                        BASE_IMAGES_REPO_EMAIL = credentials("${ocir_email_creds}")
                        TEST_IMAGES_REPO_USERNAME = credentials("${ocir_username_creds}")
                        TEST_IMAGES_REPO_PASSWORD = credentials("${ocir_password_creds}")
                        TEST_IMAGES_REPO_EMAIL = credentials("${ocir_email_creds}")
                    }
                    steps {
                        sh '''
                            export PATH=${runtime_path}
                            mkdir -m777 -p "${WORKSPACE}/.mvn"
                            touch ${WORKSPACE}/.mvn/maven.config

                            export KUBECONFIG=${kubeconfig_file}
                            K8S_NODEPORT_HOST=$(kubectl get node kind-worker -o jsonpath='{.status.addresses[?(@.type == "InternalIP")].address}')
                            export NO_PROXY="${K8S_NODEPORT_HOST}"

                            if [ "${IT_TEST}" = '**/It*' ] && [ "${MAVEN_PROFILE_NAME}" = "integration-tests" ]; then
                                echo "-Dit.test=\"!ItOperatorWlsUpgrade,!ItAuxV8DomainImplicitUpgrade,!ItFmwDomainInPVUsingWDT,!ItFmwDynamicDomainInPV,!ItDedicatedMode,!ItT3Channel,!ItOperatorFmwUpgrade,!ItOCILoadBalancer,!ItMiiSampleFmwMain,!ItIstioCrossClusters*,!ItMultiDomainModels\"" >> ${WORKSPACE}/.mvn/maven.config
                            elif [ ! -z "${IT_TEST}" ]; then
                                echo "-Dit.test=\"${IT_TEST}\"" >> ${WORKSPACE}/.mvn/maven.config
                            fi
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
                            echo "-Dwko.it.test.images.repo=\"${TEST_IMAGES_REPO}\""                                           >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.base.images.repo=\"${BASE_IMAGES_REPO}\""                                     >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.weblogic.image.name=\"${WEBLOGIC_IMAGE_NAME}\""                               >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.weblogic.image.tag=\"${WEBLOGIC_IMAGE_TAG}\""                                 >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.fmwinfra.image.name=\"${FMWINFRA_IMAGE_NAME}\""                               >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.fmwinfra.image.tag=\"${FMWINFRA_IMAGE_TAG}\""                                 >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.db.image.name=\"${DB_IMAGE_NAME}\""                                           >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.db.image.tag=\"${DB_IMAGE_TAG}\""                                             >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.monitoring.exporter.branch=\"${MONITORING_EXPORTER_BRANCH}\""                 >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.monitoring.exporter.webapp.version=\"${MONITORING_EXPORTER_WEBAPP_VERSION}\"" >> ${WORKSPACE}/.mvn/maven.config
                            echo "-Dwko.it.collect.logs.on.success=\"${COLLECT_LOGS_ON_SUCCESS}\""                       >> ${WORKSPACE}/.mvn/maven.config

                            echo "${WORKSPACE}/.mvn/maven.config contents:"
                            cat "${WORKSPACE}/.mvn/maven.config"
                            cp "${WORKSPACE}/.mvn/maven.config" "${result_root}"

                            export BASE_IMAGES_REPO_USERNAME=${BASE_IMAGES_REPO_USERNAME}
                            export BASE_IMAGES_REPO_PASSWORD=${BASE_IMAGES_REPO_PASSWORD}
                            export BASE_IMAGES_REPO_EMAIL=${BASE_IMAGES_REPO_EMAIL}

                            if [ ! -z "${http_proxy}" ]; then
                                export http_proxy
                            elif [ ! -z "${HTTP_PROXY}" ]; then
                                export HTTP_PROXY
                            fi

                            if [ ! -z "${https_proxy}" ]; then
                                export https_proxy
                            elif [ ! -z "${HTTPS_PROXY}" ]; then
                                export HTTPS_PROXY
                            fi

                            if [ ! -z "${no_proxy}" ]; then
                                export no_proxy
                            elif [ ! -z "${NO_PROXY}" ]; then
                                export NO_PROXY
                            fi

                            if ! time mvn -pl integration-tests -P ${MAVEN_PROFILE_NAME} verify 2>&1 | tee "${result_root}/kindtest.log"; then
                                echo "integration-tests failed"
                            fi
                        '''
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
                            archiveArtifacts(artifacts: "logdir/**/*")
                            junit(testResults: 'integration-tests/target/failsafe-reports/*.xml')
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
    }
}
