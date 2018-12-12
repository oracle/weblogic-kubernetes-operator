> **WARNING** This documentation is for version 1.0 of the operator.  To view documenation for the current release, [please click here](/site).

# Starting a WebLogic domain

Startup of the domain is controlled by settings in the domain custom resource.  If used, the domain creation job will have created a domain custom resource YAML file. If the domain was created manually, this YAML file will also need to be created manually.

An example of the domain custom resource YAML file is shown below:

```
# Copyright 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# This is an example of how to define a Domain Custom Resource.
#
apiVersion: "weblogic.oracle/v1"
kind: Domain
metadata:
  name: domain1
  namespace: domain1
spec:
  # The domainUID must be unique across the entire Kubernetes Cluster.   
  # Each WebLogic Domain must have its own unique domainUID.  This does not have
  # to be the same as the Domain Name.  It is allowed to have multiple Domains with
  # the same Domain Name, but they MUST have different domainUID's.
  # The domainUID is also used to identify the Persistent Volume that belongs
  # to/with this Domain.
  domainUID: domain1
  # The WebLogic Domain Name
  domainName: base_domain
  # The Operator currently does not support other images
  image: "store/oracle/weblogic:12.2.1.3"
  # imagePullPolicy defaults to "Always" if image version is :latest
  imagePullPolicy: "IfNotPresent"
  # Identify which Secret contains the WebLogic Admin credentials (note
  # that there is an example of how to create that Secret at the end of this file)
  adminSecret:
    name: domain1-weblogic-credentials
  # The name of the Admin Server
  asName: "admin-server"
  # The Admin Server's ListenPort
  asPort: 7001
  # startupControl legal values are "ALL", "ADMIN", "SPECIFIED", or "AUTO"
  # This determines which WebLogic Servers the Operator will start up when
  # it discovers this Domain
  # - "ALL" will start up all defined servers
  # - "ADMIN" will start up only the AdminServer (no managed servers will be started)
  # - "SPECIFIED" will start the AdminServer and then will look at the "serverStartup" and
  #   "clusterStartup" entries below to work out which servers to start
  # - "AUTO" will start the servers as with "SPECIFIED", but then also start servers from
  #   other clusters up to the replicas count
  startupControl: "AUTO"
  # serverStartup is used to list the desired behavior for starting servers.  
  # The Operator will use this field only if startupControl is set to "SPECIFIED"
  # or "AUTO".  You may provide a list of
  # entries, each entry should contain the keys should below:
  serverStartup:
  # desiredState legal values are "RUNNING" or "ADMIN"
  # "RUNNING" means the listed server(s) will be started up to "RUNNING" mode
  # "ADMIN" means the listed server(s) will be start up to "ADMIN" mode
  - desiredState: "RUNNING"
    # a list of the server(s) to apply these rules to
    servers:
    - "admin-server"
    # an (optional) list of environment variable to be set on the server(s) in this group
    env:
    - name: JAVA_OPTIONS
      value: "-Dweblogic.StdoutDebugEnabled=false"
    - name: USER_MEM_ARGS
      value: "-Xms64m -Xmx256m "
  # clusterStartup has the same structure as serverStartup, but it allows you to
  # specify the names of cluster(s) instead of individual servers.  If you use
  # this entry, then the rules will be applied to ALL servers that are members
  # of the named clusters.
  clusterStartup:
  - desiredState: "RUNNING"
    clusters:
    - "cluster-1"
    replicas: 2
    env:
    - name: JAVA_OPTIONS
      value: "-Dweblogic.StdoutDebugEnabled=false"
    - name: USER_MEM_ARGS
      value: "-Xms64m -Xmx256m "
  # The number of managed servers to start from clusters not listed by clusterStartup
  # replicas: 1

  # Uncomment to export the T3Channel as a service
  # exportT3Channels:
  # - T3Channel
```


## How the operator determines which servers to start

The operator determines which servers to start using the following logic:

* If `startupControl` is set to `ALL`, then all servers will be started.
* If `startupControl` is set to `SPECIFIED`, then:

  * The Administration Server will be started.
  * Each server listed in a `serverStartup` section will be brought up to the state that is specified in the `desiredState` in that section, `RUNNING` or `ADMIN`.
  * For each cluster listed in a `clusterStartup` section, a number of servers in that cluster, equal to the `replicas` setting, will be brought up to the state that is specified in the `desiredState` in that section, `RUNNING` or `ADMIN`.  If `replicas` is not specified in `clusterStartup`, then the top-level `replicas` field in the domain custom resource will be used instead.

* If `startupControl` is set to `AUTO`, then:

  * The operator will perform as if `startupControl` were set to `SPECIFIED`.
  * For all clusters that do not have a `clusterStartup` section, the number of servers in that cluster equal to the top-level `replicas` setting, will be brought up to the `RUNNING` state.
