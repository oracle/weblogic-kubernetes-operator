---
title: "Log files"
date: 2019-02-23T17:39:19-05:00
draft: false
weight: 10
description: "Configure WebLogic Server and domain log settings."
---

{{< table_of_contents >}}

### Overview

The operator can automatically override WebLogic Server, domain, and introspector `.log` and `.out` locations.
This occurs if the Domain `logHomeEnabled` field is explicitly set to `true`, or if `logHomeEnabled` isn't set
and `domainHomeSourceType` is set to `PersistentVolume`.
When overriding, the log location will be the location specified by the `logHome` setting.

WebLogic Server `.out` files contain a subset of WebLogic Server `.log` files.
The operator, by default, echoes these `.out` files to each server pod log.
To disable this behavior, set the Domain `includeServerOutInPodLog` to `false`.

Optionally, you can monitor a WebLogic Server and its log using an [Elastic Stack](https://www.elastic.co/elastic-stack/)
(previously referred to as the ELK Stack, after Elasticsearch, Logstash, and Kibana).
For an example, see the WebLogic Server [Elastic Stack]({{<relref "/samples/elastic-stack/weblogic-domain/_index.md">}}) sample.

{{% notice warning %}}
Kubernetes stores pod logs on each of its nodes, and, depending on the Kubernetes implementation, extra steps may be necessary to limit their disk space usage.
For more information, see [Kubernetes Logging Architecture](https://kubernetes.io/docs/concepts/cluster-administration/logging/).
{{% /notice %}}

### WebLogic Server log file location

When `logHomeEnabled` is `false`,
WebLogic Server log files are placed in a subdirectory `<domain.spec.domainHome>/servers/<server name>/logs`.

When `logHomeEnabled` is `true`,
WebLogic Server log files are placed in a subdirectory `<domain.spec.logHome>/servers/<server name>/logs`
by default, or alternatively placed in subdirectory `<domain.spec.logHome>` when `logHomeLayout` is set to `Flat`.

For example, here is the default layout of the log files under the `logHome` root:

```text
/shared/logs/domain1$ ls -aRtl
-rw-r----- 1 docker root 291340 Apr 27 10:26 sample-domain1.log
-rw-r--r-- 1 docker root  24772 Apr 26 12:50 introspector_script.out
drwxr-xr-x 1 docker root    108 Apr 25 13:49 servers

./servers/managed-server2/logs:
-rw-r----- 1 docker root 921385 Apr 27 18:20 managed-server2.log
-rw-r----- 1 docker root  25421 Apr 27 10:26 managed-server2.out
-rw-r----- 1 docker root  14711 Apr 27 10:25 managed-server2_nodemanager.log
-rw-r--r-- 1 docker root  16829 Apr 27 10:25 managed-server2_nodemanager.out
-rw-r----- 1 docker root      5 Apr 27 10:25 managed-server2.pid

./servers/admin-server/logs:
-rw-r----- 1 docker root 903878 Apr 27 18:19 admin-server.log
-rw-r----- 1 docker root  16516 Apr 27 10:25 admin-server_nodemanager.log
-rw-r--r-- 1 docker root  18610 Apr 27 10:25 admin-server_nodemanager.out
-rw-r----- 1 docker root  25514 Apr 27 10:25 admin-server.out
-rw-r----- 1 docker root      5 Apr 27 10:25 admin-server.pid

```

### WebLogic Server log file rotation and size

If you want to fine tune the `.log` and `.out` rotation behavior for WebLogic Servers and domains, then
you can update the related `Log MBean` in your WebLogic configuration. Alternatively, for WebLogic
Servers, you can set corresponding system properties in `JAVA_OPTIONS`:

- Here are some WLST offline examples for creating and accessing commonly tuned Log MBeans:

  ```javascript
  # domain log
  cd('/')
  create(dname,'Log')
  cd('/Log/' + dname);

  # configured server log for a server named 'sname'
  cd('/Servers/' + sname)
  create(sname, 'Log')
  cd('/Servers/' + sname + '/Log/' + sname)

  # templated (dynamic) server log for a template named 'tname'
  cd('/ServerTemplates/' + tname)
  create(tname,'Log')
  cd('/ServerTemplates/' + tname + '/Log/' + tname)
  ```

- Here is sample WLST offline code for commonly tuned Log MBean attributes:

  ```javascript
  # minimum log file size before rotation in kilobytes
  set('FileMinSize', 1000)

  # maximum number of rotated files
  set('FileCount', 10)

  # set to true to rotate file every time on startup (instead of append)
  set('RotateLogOnStartup', 'true')
  ```

- Here are the defaults for commonly tuned Log MBean attributes:

  | Log MBean Attribute | Production Mode Default | Development Mode Default |
  | --------- | ----------------------- | ------------------------ |
  | FileMinSize (in kilobytes) | 5000 | 500 |
  | FileCount | 100 | 7 |
  | RotateLogOnStartup | false | true |

- For WebLogic Server `.log` and `.out` files (including both dynamic and configured servers), you can alternatively
set logging attributes using system properties that start with `weblogic.log.`
and that end with the corresponding Log MBean attribute name.

  For example, you can include `-Dweblogic.log.FileMinSize=1000 -Dweblogic.log.FileCount=10 -Dweblogic.log.RotateLogOnStartup=true` in `domain.spec.serverPod.env.name.JAVA_OPTIONS` to set the behavior for all WebLogic Servers in your domain. For information about setting `JAVA_OPTIONS`, see [Domain resource]({{< relref "/managing-domains/domain-resource/_index.md#jvm-memory-and-java-option-environment-variables" >}}).
