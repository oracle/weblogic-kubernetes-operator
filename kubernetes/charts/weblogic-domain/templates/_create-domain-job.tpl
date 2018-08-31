# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "domain.createDomainJob" }}
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .Release.Name }}-create-weblogic-domain-job-cm
  namespace: {{ .Release.Namespace }}
  labels:
    weblogic.resourceVersion: domain-v1
    weblogic.domainUID: {{ .Release.Name }}
    weblogic.domainName: {{ .domainName }}
  annotations:
    "helm.sh/hook": pre-install
    "helm.sh/hook-weight": "-5"
    "helm.sh/hook-delete-policy": hook-succeeded
data:
  utility.sh: |-
    #!/bin/bash
    #

    #
    # Report an error and fail the job
    # $1 - text of error
    function fail {
      echo ERROR: $1
      exit 1
    }

    #
    # Create a folder
    # $1 - path of folder to create
    function createFolder {
      mkdir -m 777 -p $1
      if [ ! -d $1 ]; then
        fail "Unable to create folder $1"
      fi
    }

    #
    # Check a file exists
    # $1 - path of file to check
    function checkFileExists {
      if [ ! -f $1 ]; then
        fail "The file $1 does not exist"
      fi
    }

  create-domain-job.sh: |-
    #!/bin/bash
    #

    # Include common utility functions
    source /u01/weblogic/utility.sh

    # Verify the script to create the domain exists
    script='/u01/weblogic/create-domain-script.sh'
    if [ -f $script ]; then
      echo The domain will be created using the script $script
    else
      fail "Could not locate the domain creation script ${script}"
    fi

    # Validate the domain secrets exist before proceeding.
    if [ ! -f /weblogic-operator/secrets/username ]; then
      fail "The domain secret /weblogic-operator/secrets/username was not found"
    fi
    if [ ! -f /weblogic-operator/secrets/password ]; then
      fail "The domain secret /weblogic-operator/secrets/password was not found"
    fi

    # Do not proceed if the domain already exists
    domainFolder=${SHARED_PATH}/domain/{{ .domainName }}
    if [ -d ${domainFolder} ]; then
      fail "The create domain job will not overwrite an existing domain. The domain folder ${domainFolder} already exists"
    fi

    # Create the base folders
    createFolder ${SHARED_PATH}/domain
    createFolder ${SHARED_PATH}/applications
    createFolder ${SHARED_PATH}/logs
    createFolder ${SHARED_PATH}/stores

    # Execute the script to create the domain
    source $script

  read-domain-secret.py: |-
    #
    # +++ Start of common code for reading domain secrets

    # Read username secret
    file = open('/weblogic-operator/secrets/username', 'r')
    admin_username = file.read()
    file.close()

    # Read password secret
    file = open('/weblogic-operator/secrets/password', 'r')
    admin_password = file.read()
    file.close()

    # +++ End of common code for reading domain secrets
    #

  create-domain-script.sh: |-
    #!/bin/bash
    #

    # Include common utility functions
    source /u01/weblogic/utility.sh

    export DOMAIN_HOME=${SHARED_PATH}/domain/{{ .domainName }}

    # Create the domain
    wlst.sh -skipWLSModuleScanning /u01/weblogic/create-domain.py

    echo "WebLogic domain configuration by create-domain-script.py successfully completed"

  create-domain.py: |-
    # This python script is used to create a WebLogic domain

    # Read the domain secrets from the common python file
    execfile("/u01/weblogic/read-domain-secret.py")

    server_port        = {{ .managedServerPort }}
    domain_path        = os.environ.get("DOMAIN_HOME")
    cluster_name       = "{{ .clusterName }}"
    number_of_ms       = {{ .configuredManagedServerCount }}
    cluster_type       = "{{ .clusterType }}"

    print('domain_path        : [%s]' % domain_path);
    print('domain_name        : [{{ .domainName }}]');
    print('admin_username     : [%s]' % admin_username);
    print('admin_port         : [{{ .adminPort }}]');
    print('cluster_name       : [%s]' % cluster_name);
    print('server_port        : [%s]' % server_port);
    print('cluster_type       : [%s]' % cluster_type);

    # Open default domain template
    # ============================
    readTemplate("/u01/oracle/wlserver/common/templates/wls/wls.jar")

    set('Name', '{{ .domainName }}')
    setOption('DomainName', '{{ .domainName }}')
    create('{{ .domainName }}','Log')
    cd('/Log/{{ .domainName }}');
    set('FileName', '/shared/logs/{{ .domainName }}.log')

    # Configure the Administration Server
    # ===================================
    cd('/Servers/AdminServer')
    set('ListenAddress', '{{ .Release.Name | lower | replace "_" "-"}}-{{ .adminServerName | lower | replace "_" "-"}}')
    set('ListenPort', {{ .adminPort }})
    set('Name', '{{ .adminServerName }}')

    create('T3Channel', 'NetworkAccessPoint')
    cd('/Servers/{{ .adminServerName }}/NetworkAccessPoints/T3Channel')
    set('PublicPort', {{ .t3ChannelPort }})
    set('PublicAddress', '{{ .t3PublicAddress }}')
    set('ListenAddress', '{{ .Release.Name }}-{{ .adminServerName }}')
    set('ListenPort', {{ .t3ChannelPort }})

    cd('/Servers/{{ .adminServerName }}')
    create('{{ .adminServerName }}', 'Log')
    cd('/Servers/{{ .adminServerName }}/Log/{{ .adminServerName }}')
    set('FileName', '/shared/logs/{{ .adminServerName }}.log')

    # Set the admin user's username and password
    # ==========================================
    cd('/Security/{{ .domainName }}/User/weblogic')
    cmo.setName(admin_username)
    cmo.setPassword(admin_password)

    # Write the domain and close the domain template
    # ==============================================
    setOption('OverwriteDomain', 'true')

    # Configure the node manager
    # ==========================
    cd('/NMProperties')
    set('ListenAddress','0.0.0.0')
    set('ListenPort',5556)
    set('CrashRecoveryEnabled', 'true')
    set('NativeVersionEnabled', 'true')
    set('StartScriptEnabled', 'false')
    set('SecureListener', 'false')
    set('LogLevel', 'INFO')
    set('DomainsDirRemoteSharingEnabled', 'true')

    # Set the Node Manager user name and password (domain name will change after writeDomain)
    cd('/SecurityConfiguration/base_domain')
    set('NodeManagerUsername', admin_username)
    set('NodeManagerPasswordEncrypted', admin_password)

    # Create a cluster
    # ======================
    cd('/')
    cl=create(cluster_name, 'Cluster')

    if cluster_type == "CONFIGURED":

      # Create managed servers
      for index in range(0, number_of_ms):
        cd('/')

        msIndex = index+1
        name = '{{ .managedServerNameBase }}%s' % msIndex

        create(name, 'Server')
        cd('/Servers/%s/' % name )
        print('managed server name is %s' % name);
        set('ListenAddress', '{{ .Release.Name }}-%s' % name)
        set('ListenPort', server_port)
        set('NumOfRetriesBeforeMSIMode', 0)
        set('RetryIntervalBeforeMSIMode', 1)
        set('Cluster', cluster_name)

        create(name,'Log')
        cd('/Servers/%s/Log/%s' % (name, name))
        set('FileName', '/shared/logs/%s.log' % name)
    else:
      print('Configuring Dynamic Cluster %s' % cluster_name)

      templateName = cluster_name + "-template"
      print('Creating Server Template: %s' % templateName)
      st1=create(templateName, 'ServerTemplate')
      print('Done creating Server Template: %s' % templateName)
      cd('/ServerTemplates/%s' % templateName)
      cmo.setListenPort(server_port)
      cmo.setListenAddress('{{ .Release.Name }}-{{ .managedServerNameBase }}${id}')
      cmo.setCluster(cl)
      print('Done setting attributes for Server Template: %s' % templateName);


      cd('/Clusters/%s' % cluster_name)
      create(cluster_name, 'DynamicServers')
      cd('DynamicServers/%s' % cluster_name)
      set('ServerTemplate', st1)
      set('ServerNamePrefix', "{{ .managedServerNameBase }}")
      set('DynamicClusterSize', number_of_ms)
      set('MaxDynamicClusterSize', number_of_ms)
      set('CalculatedListenPorts', false)
      set('Id', 1)

      print('Done setting attributes for Dynamic Cluster: %s' % cluster_name);

    # Write Domain
    # ============
    writeDomain(domain_path)
    closeTemplate()
    print 'Domain Created'

    # Update Domain
    readDomain(domain_path)
    cd('/')
    cmo.setProductionModeEnabled({{ .productionModeEnabled }})
    updateDomain()
    closeDomain()
    print 'Domain Updated'
    print 'Done'

    # Exit WLST
    # =========
    exit()

---
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ .Release.Name }}-create-weblogic-domain-job
  namespace: {{ .Release.Namespace }}
  annotations:
    "helm.sh/hook": pre-install
    "helm.sh/hook-weight": "0"
    "helm.sh/hook-delete-policy": hook-succeeded
spec:
  template:
    metadata:
      labels:
        weblogic.resourceVersion: domain-v1
        weblogic.domainUID: {{ .Release.Name }}
        weblogic.domainName: {{ .domainName }}
        app: {{ .Release.Name }}-create-weblogic-domain-job
    spec:
      restartPolicy: Never
      containers:
        - name: create-weblogic-domain-job
          image: {{ .weblogicImage }}
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 7001
          volumeMounts:
          - mountPath: /u01/weblogic
            name: create-weblogic-domain-job-cm-volume
          - mountPath: /shared
            name: weblogic-domain-storage-volume
          - mountPath: /weblogic-operator/secrets
            name: weblogic-credentials-volume
          command: ["/bin/sh"]
          args: ["/u01/weblogic/create-domain-job.sh"]
          env:
            - name: SHARED_PATH
              value: "/shared"
      volumes:
        - name: create-weblogic-domain-job-cm-volume
          configMap:
            name: {{ .Release.Name }}-create-weblogic-domain-job-cm
        - name: weblogic-domain-storage-volume
          persistentVolumeClaim:
            claimName: {{ .Release.Name }}-weblogic-domain-job-pvc
        - name: weblogic-credentials-volume
          secret:
            secretName: {{ .weblogicCredentialsSecretName }}
      {{- if .weblogicImagePullSecretName }}
      imagePullSecrets:
      - name: {{ .weblogicImagePullSecretName }}
      {{- end }}
{{- end }}
