+++
title = "Status conditions"
date = 2022-10-24T16:43:45-05:00
weight = 9
description = "Monitor domain and cluster resources using operator-generated conditions about resources that it manages."
+++

{{< table_of_contents >}}

### Overview

Kubernetes conditions are commonly found in the status of Kuberentes resources, including Pod and Deployment. 
The pattern is that the status has a list of conditions that give a quick readout on the status of the resource. 
For instance, if a pod is ready, then the Pod resource will have a `Ready` condition in its status that indicates this. 
WebLogic Kubernetes Operator has elected to use conditions in its resources as well. 
Each Domain or Cluster resource contains a list of conditions that provide information about the status of the Domain or Cluster.

### Checking conditions

Conditions can be found under the `spec.status` field in a Domain or Cluster resource.

You can check the conditions in a Domain resource by using:
`kubectl -n MY_NAMESPACE describe domain MY_DOMAIN_RESOURCE_NAME`

Similarly, you can check the conditions in a Cluster resource by using:
`kubectl -n MY_NAMESPACE describe cluster MY_CLUSTER_NAME`

{{%expand "Click here for an example of status of a Cluster resource showing its conditions." %}}
```
Status:
  Cluster Name:  cluster-1
  Conditions:
    Last Transition Time:  2022-10-25T16:31:22.682605Z
    Status:                True
    Type:                  Available
    Last Transition Time:  2022-10-25T16:31:22.683156Z
    Status:                True
    Type:                  Completed
```
{{% /expand %}}

The cluster conditions are also listed in the Domain resource status under `domain.status.clusters`.

{{%expand "Click here for an example of status of a Domain resource with a cluster." %}}
```
Status:  
  Clusters:
    Cluster Name:  cluster-1
    Conditions:
      Last Transition Time:  2022-10-25T16:31:22.682605Z
      Status:                True
      Type:                  Available
      Last Transition Time:  2022-10-25T16:31:22.682605Z
      Status:                True
      Type:                  Completed
    Label Selector:          weblogic.domainUID=sample-domain1,weblogic.clusterName=cluster-1
    Maximum Replicas:        5
    Minimum Replicas:        0
    Observed Generation:     1
    Ready Replicas:          1
    Replicas:                1
    Replicas Goal:           1
  Conditions:
    Last Transition Time:  2022-10-25T16:44:27.104854Z
    Status:                True
    Type:                  Available
    Last Transition Time:  2022-10-25T16:44:27.104766Z
    Status:                True
    Type:                  Completed
```
{{% /expand %}}

### Attributes in a condition

The following attributes can be found in a condition:
- `Type` - type of the condition, such as `Failed` or `Available`. See [Types of Domain status conditions]({{< relref "#types-of-domain-status-conditions">}}) below.
- `Status` - status of the condition, such as `True` or `False`.
- `Message` - a human-readable message providing more details about the condition. Optional.
- `Reason` - [reason]({{< relref "managing-domains/domain-lifecycle/retry#domain-failure-reasons" >}}) for the `Failed` condition. Not applicable to other types.
- `Severity` - [severity]({{< relref "managing-domains/domain-lifecycle/retry#domain-failure-severities" >}}) for the `Failed` condition. Not applicable to other types.
- `Last Transition Time` - a timestamp of when the condition was created or the last time time the condition transitioned from one status to another.

### Types of Domain status conditions

The following is a list of status condition types for a Domain resource.

- `Failed`
    * The desired state of the Domain resource cannot be achieved.
      See [Retry behavior]({{< relref "managing-domains/domain-lifecycle/retry#retry-behavior" >}})
      on how the operator handle different types of failures.
    * The `Status` attribute is always `True` for a `Failed` condition. 
      The `Failed` condition is removed from the domain status when the underlying failure is resolved.
    * The `Message` attribute contains an error message with details of the failure.
    * The `Reason` attribute is set to one of the reasons listed in [Domain failure reasons]({{< relref "managing-domains/domain-lifecycle/retry#domain-failure-reasons" >}}).
    * The `Severity` attribute is set to one of the severity levels listed in [Domain failure severities]({{< relref "managing-domains/domain-lifecycle/retry#domain-failure-severities" >}}).
      {{%expand "Click here for an example of a Domain resource with a Failed condition." %}}
```
Status:
  ...
  Conditions:
    Last Transition Time:  2022-10-24T23:54:49.486543Z
    Message:               One or more server pods that are supposed to be available did not start within the period of time defined in 'serverPod.maxPendingWaitTimeSeconds' under "domain.spec', 'domain.adminServer', 'managedServer', or 'domain.cluster'. Check the server status in the domain status, the server pod status and logs, and WebLogic Server logs for possible reasons. One common cause of this issue is a problem pulling the WebLogicServer image. Adjust the value of 'serverPod.maxPendingWaitTimeSeconds' setting if needed."
    Reason:                ServerPod
    Severity:              Severe
    Status:                True
    Type:                  Failed
    Last Transition Time:  2022-10-24T23:49:35.974905Z
    Message:               No application servers are ready.
    Status:                False
    Type:                  Available
    Last Transition Time:  2022-10-24T23:49:35.974897Z
    Status:                False
    Type:                  Completed
    Last Transition Time:  2022-10-24T23:49:36.173977Z
    Status:                True
    Type:                  Rolling
```
{{% /expand %}}
- `Completed`
    * The `Status` attribute of a `Completed` condition indicates whether the desired state of the 
      Domain resource has been fully achieved.
    * The `Status` attribute is set to `True` when:
      * There are no `Failed` conditions, ie, no failures are detected.
      * One of the following conditions are met:
        * All server pods that are expected to be running are ready at their target images(s), 
          `restartVersion`, and `introspectVersion`, or
        * no servers are running if so configured in the Domain and the related Cluster resources, or
        * the Domain is configured to have a `spec.serverStartPolicy` value of `AdminOnly` and the 
          admin server is running.
      * There are no pending server shutdown requests.
- `Available`
    * `Status` attribute is set to  `True` when a sufficient number of pods are `ready`:
        * Processing successfully completes without error
          (introspection job, syntax checks, and such).
        * The operator is starting or has started all desired WebLogic Server pods
          (not including any servers that may be shutting down).
    * `Status` attribute can be `True` even when `Status` for the `Completed` condition is `False`,
      a `Failed` condition is reported, or a cluster has up to `cluster.spec.maxUnavailable` pods 
      that are not `ready`.
    * `Status` is set to `False`if servers are rolling or starting, or a failure has occurred.
    * _Note:_ This condition may 'blink' on and off while
      processing a domain resource change or during a roll.

- `ConfigChangesPendingRestart`
  * `Status` attribute is `True` if all of the following are true:
    * The domain resource attribute
      `domain.spec.configuration.model.onlineUpdate.onNonDynamicChanges` is `CommitUpdateOnly`.
    * The domain resource attribute
      `domain.spec.configuration.model.onlineUpdate.enabled` is `True`.
    * There were model changes and these changes modify non-dynamic WebLogic configuration.
    * Processing successfully completed, including the introspector job.
    * The administrator has not subsequently rolled/restarted each WebLogic Server pod
      (to propagate the pending non-dynamic changes).
  * See [Online update status and labels]({{< relref "managing-domains/model-in-image/runtime-updates#online-update-status-and-labels" >}})
    on how to see which pods are awaiting restart using WebLogic pod labels.
{{%expand "Click here for an example of a Domain resource with a ConfigChangesPendingRestart condition." %}}
```
Status:
  ...
  Conditions:
    Last Transition Time:  2021-01-20T15:09:15.209Z
    Message:               Online update completed successfully, but the changes require restart and the domain resource specified 'spec.configuration.model.onlineUpdate.onNonDynamicChanges=CommitUpdateOnly' or not set. The changes are committed but the domain require manually restart to  make the changes effective. The changes are: Server re-start is REQUIRED for the set of changes in progress.
    
    The following non-dynamic attribute(s) have been changed on MBeans
    that require server re-start:
    MBean Changed : com.bea:Name=oracle.jdbc.fanEnabled,Type=weblogic.j2ee.descriptor.wl.JDBCPropertyBean,Parent=[sample-domain1]/JDBCSystemResources[Bubba-DS],Path=JDBCResource[Bubba-DS]/JDBCDriverParams/Properties/Properties[oracle.jdbc.fanEnabled]
    Attributes changed : Value
    Reason:                      Online update applied, introspectVersion updated to 82
    Status:                      True
    Type:                        ConfigChangesPendingRestart
```
{{% /expand %}}

- `Rolling`
  * This condition indicates that the operator is rolling the server pods in a Domain, such as after it
    has detected an update to the Domain resource or Model in Image model that require it to 
    perform a rolling restart of the domain.
  * The `Status` attribute is always `True` for a `Rolling` condition. 
  * The `Rolling` condition is removed from the Domain status when the rolling is completed.


### Types of Cluster status conditions

The following is a list of status condition types for a Cluster resource.
- `Completed`
    * The `Status` attribute of a `Completed` condition indicates whether the desired state of the
      Cluster resource has been fully achieved.
    * The `Status` attribute is set to `True` when:
        * All server pods in the cluster that are expected to be running are ready at their target images(s),
          `restartVersion`, and `introspectVersion`
        * There are no pending server shutdown requests.
- `Available`
    * The `Status` attribute is set to  `True` when a sufficient number of pods are `ready` in the cluster,
      which can be configured using `cluster.spec.maxUnavailable`.
    * The `Status` attribute is set to `False` if servers are rolling/starting or a failure has occurred.
