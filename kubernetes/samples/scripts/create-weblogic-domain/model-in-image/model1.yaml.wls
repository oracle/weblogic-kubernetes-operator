domainInfo:
    AdminUserName: '@@FILE:/weblogic-operator/secrets/username@@'
    AdminPassword: '@@FILE:/weblogic-operator/secrets/password@@'
    ServerStartMode: 'prod'

topology:
    Name: domain1
    AdminServerName: "admin-server"
    Cluster:
        "cluster-1":
            DynamicServers:
                ServerTemplate:  "cluster-1-template"
                ServerNamePrefix: "managed-server"
                DynamicClusterSize: 5
                MaxDynamicClusterSize: 5
                CalculatedListenPorts: false
    Server:
        "admin-server":
            ListenPort: 7001
    ServerTemplate:
        "cluster-1-template":
            Cluster: "cluster-1"
            ListenPort : '@@PROP:MANSVR_PORT@@'

appDeployments:
    Application:
        myear:
            SourcePath: wlsdeploy/applications/sample_app.ear
            ModuleType: ear
            Target: 'cluster-1'
