+++
title = "Domain events"
date = 2020-11-30T16:43:45-05:00
weight = 8
description = "Monitor domain resources using operator-generated events about resources that it manages."
+++

{{< table_of_contents >}}

### Overview

This document describes Kubernetes events that the operator generates about resources that it manages, during key points of its processing workflow. These events provide an additional way of monitoring your domain resources. Most of the operator-generated events are associated with a domain resource, and those events are included in the Domain resource object as well. Note that the Kubernetes server also generates events for standard Kubernetes resources, such as pods, services, and jobs that the operator generates on behalf of deployed domain custom resources.

### What's new

The domain events have been enhanced in 4.0. Here is a summary of the changes in this area:
* Removed two event types: `DomainProcessingStarting` and `DomainProcessingRetrying`.
* Simplified the event type names with the following changes:
  * `DomainProcessingFailed` to `Failed`.
  * `DomainProcessingCompleted` to `Completed`.
  * `DomainCreated` to `Created`.
  * `DomainChanged` to `Changed`.
  * `DomainDeleted` to `Deleted`.
  * `DomainRollStarting` to `RollStarting`.
  * `DomainRollCompleted` to `RollCompleted`.
* Changed `DomainProcessingAborted` event to `Failed` event with an explicit message indicating that no retry will occur.
* Changed `DomainValidationError` event to `Failed` event.
* Enhanced `Failed` event to:
    * Have a better failure categorization (see [Operator-generated event types](#operator-generated-event-types) for more details).
    * Include the categorization information in the event message.
    * Provide more information in the event message to indicate what has gone wrong, what you need to do to resolve the problem, and if the operator will [retry]({{< relref "/managing-domains/domain-lifecycle/retry.md" >}}) the failed operation.
* Added four event types: `Available`, `Unavailable`, `Incomplete`, and `FailureResolved`, to record
  the transition of their corresponding [Domain resource status conditions]({{< relref "/managing-domains/accessing-the-domain/status-conditions#types-of-domain-conditions" >}}).
* Added seven event types: `ClusterAvailable`, `ClusterChanged`, `ClusterCompleted`, `ClusterCreated`,
  `ClusterDeleted`, `ClusterIncomplete`, and `ClusterUnavailable`, to record the transition of their
  corresponding [Cluster resource status conditions]({{< relref "/managing-domains/accessing-the-domain/status-conditions#types-of-cluster-conditions" >}}).

### Operator-generated event types

The operator generates these event types in a domain namespace, which indicate the following:

 * `Created`: A new domain is created.
 * `Changed`: A change has been made to an existing domain.
 * `Deleted`: An existing domain has been deleted.
 * `Available`: An existing domain is available, which means that a sufficient number of servers are ready such that the customer's applications are available.    For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#available" >}}).
 * `Failed`: The domain resource encountered a problem which prevented it from becoming fully up.
   For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#failed" >}}).
   The possible failure could be one or more of the following conditions:
   * Invalid configurations in the domain resource.
   * A Kubernetes API call error.
   * Introspection failures.
   * An unexpected error in a server pod.
   * A topology mismatch between the Domain resource configuration and the WebLogic domain configuration.
   * The replicas of a cluster in the Domain resource exceeds the maximum number of servers configured for the WebLogic cluster.
   * An internal error.
   * A failure that retries will not help, or has been retried and has exceeded the [pre-defined maximum retry time]({{< relref "/managing-domains/domain-lifecycle/retry#retry-behavior" >}}).
 * `Completed`:  The domain resource is complete because all of the following are true: there is no failure detected, there are no pending server shutdowns, and all servers expected to be running are ready and at their target image, auxiliary images, restart version, and introspect version.all servers that are supposed to be started are up running.
    For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#completed" >}}).
 * `Unavailable`: The domain resource is unavailable, which means that the domain does not have a sufficient number of servers active. For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#available" >}}).
 * `Incomplete`: The domain resource is incomplete for one or more of the following reasons: there are failures detected, there are pending server shutdowns, or not all servers expected to be running are ready and at their target image, auxiliary images, restart version, and introspect version.
    For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#completed" >}}).
 * `FailureResolved`: The failure condition that the domain was in, has been resolved. For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#failed" >}}).
 * `RollStarting`:  The operator has detected domain resource or Model in Image model
    updates that require it to perform a rolling restart of the domain. For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#rolling" >}}).
 * `RollCompleted`:  The operator has successfully completed a rolling restart of a domain. For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#rolling" >}}).
 * `ClusterCreated`: A new Cluster resource is created.
 * `ClusterChanged`: A change has been made to an existing Cluster resource.
 * `ClusterDeleted`: An existing Cluster resource has been deleted.
 * `ClusterAvailable`: An existing cluster is available, which means that a sufficient number of its servers have reached the ready state. For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#cluster-available" >}}).
 * `ClusterCompleted`: The cluster is complete because all of the following are true: there is no failure detected, there are no pending server shutdowns, and all servers expected to be running are ready and at their target image, auxiliary images, restart version, and introspect version. For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#cluster-completed" >}}).
 * `ClusterIncomplete`: The cluster is incomplete for one or more of the following reasons: there are failures detected, there are pending server shutdowns, or not all servers expected to be running are ready and at their target image, auxiliary images, restart version, or introspect version. For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#cluster-completed" >}}).
 * `ClusterUnavailable`: The cluster is unavailable because an insufficient number of its servers that are expected to be running are ready. For details, see the corresponding [condition]({{< relref "/managing-domains/accessing-the-domain/status-conditions#cluster-available" >}}).
 * `PodCycleStarting`:  The operator has started to replace a server pod after it detects that the current pod does not conform to the current domain resource or WebLogic domain configuration.
 * `NamespaceWatchingStarted`: The operator has started watching for domains in a namespace.
 * `NamespaceWatchingStopped`: The operator has stopped watching for domains in a namespace. Note that the creation of this event in a domain namespace is the operator's best effort only; the event will not be generated if the required Kubernetes privilege is removed when a namespace is no longer managed by the operator.

The operator also generates these event types in the operator's namespace, which indicate the following:

*  `StartManagingNamespace`: The operator has started managing domains in a namespace.
*  `StopManagingNamespace`: The operator has stopped managing domains in a namespace.
*  `StartManagingNamespaceFailed`: The operator failed to start managing domains in a namespace because it does not have the required privileges.


### Operator-generated event details

Each operator-generated event contains the following fields:
 *  `metadata`
    *  `namespace`:  Namespace in which the event is generated.
    *  `labels`:   `weblogic.createdByOperator=true` and, for a domain event, `weblogic.domainUID=<domainUID>`.
 *  `type`:  String that describes the type of the event. Possible values are `Normal` or `Warning`.
 *  `count`: Integer that indicates the number of occurrences of the event. Note that the events are matched by the combination of the `reason`, `involvedObject`, and `message` fields.
 *  `reportingComponent`:  String that describes the component that reports the event. The value is `weblogic.operator` for all operator-generated events.
 *  `reportingInstance`:  String that describes the instance that reports the event. The value is the Kubernetes pod name of the operator instance that generates the event.
 *  `firstTimestamp`:  `DateTime` field that presents the timestamp of the first occurrence of this event.
 *  `lastTimestamp`:  `DateTime` field that presents the timestamp of the last occurrence of this event.
 *  `reason`:  Short, machine understandable string that gives the reason for the transition to the object's current status.
 *  `message`:  String that describes the details of the event.
 *  `involvedObject`:  `V1ObjectReference` object that describes the Kubernetes resources with which this event is associated.
    *  `name`:  String that describes the name of the resource with which the event is associated. It may be the `domainUID`, the name of the namespace that the operator watches, or the name of the operator pod.
    *  `namespace`:  String that describes the namespace of the event, which is either the namespace of the domain resource or the namespace of the operator.
    *  `kind`:  String that describes the kind of resource this object represents. The value is `Domain` for a domain event, `Namespace` for a namespace event in the domain namespace, or `Pod` for the operator pod.
    *  `apiVersion`:  String that describes the `apiVersion` of the involved object, which is the `apiVersion` of the domain resource, for example, `weblogic.oracle/v9`, for a domain event or unset for a namespace event.
    *  `UID`: String that describes the unique identifier of the object that is generated by the Kubernetes server.

### How to access the events

To access the events that are associated with all resources in a particular namespace, run:

 ```shell
$ kubectl get events -n [namespace]
 ```

To get the events and sort them by their last timestamp, run:

```shell
$ kubectl get events -n [namespace] --sort-by=lastTimestamp
```

To get all the events that are generated by the operator, run:

```shell
$ kubectl get events -n [namespace] --selector=weblogic.createdByOperator=true
```

To get all the events that are generated by the operator for a particular domain resource, for example `sample-domain1`, run:

```shell
$ kubectl get events -n [namespace] --selector=weblogic.domainUID=sample-domain1,weblogic.createdByOperator=true --sort-by=lastTimestamp
```

### Examples of generated events

Here are some examples of operator-generated events from the output of the `kubectl describe event` or `kubectl get events` commands for `sample-domain1` in namespace `sample-domain1-ns`.

Example of a `Available` event:
```
Name:             sample-domain1.Available.b9c1ddf08e489867
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Count:            2
Event Time:       <nil>
First Timestamp:  2021-12-14T16:23:49Z
Involved Object:
  API Version:   weblogic.oracle/v9
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
  UID:           358f9335-61b2-499a-9d2a-61ae625db2ea
Kind:            Event
Last Timestamp:  2021-12-14T16:23:53Z
Message:         Domain sample-domain1 is available: a sufficient number of its servers have reached the ready state.
Metadata:
  Creation Timestamp:  2021-12-14T16:23:49Z
  Resource Version:   5366831
  Self Link:          /api/v1/namespaces/sample-domain1-ns/events/sample-domain1.Available.b9c1ddf08e489867
  UID:                5240d6c6-bfbe-4f06-8ffa-c62cd776cd28
Reason:               Available
Reporting Component:  weblogic.operator
Reporting Instance:   weblogic-operator-588b9794f5-757b9
Source:
Type:    Normal
Events:  <none>

```

Example of a `Incomplete` event:

```
Name:             sample-domain1.Incomplete.b9c1dc2a2977cd95
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Count:            1
Event Time:       <nil>
First Timestamp:  2021-12-14T16:23:49Z
Involved Object:
  API Version:   weblogic.oracle/v9
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
  UID:           358f9335-61b2-499a-9d2a-61ae625db2ea
Kind:            Event
Last Timestamp:  2021-12-14T16:23:49Z
Message:         Domain sample-domain1 is incomplete for one or more of the following reasons: there are failures detected, there are pending server shutdowns, or not all servers expected to be running are ready and at their target image, auxiliary images, restart version, and introspect version.
Metadata:
  Creation Timestamp:  2021-12-14T16:23:49Z
  Resource Version:   5366820
  Self Link:          /api/v1/namespaces/sample-domain1-ns/events/sample-domain1.Incomplete.b9c1dc2a2977cd95
  UID:                97a425b9-175c-43ac-81b0-86edff04fd2b
Reason:               Incomplete
Reporting Component:  weblogic.operator
Reporting Instance:   weblogic-operator-588b9794f5-757b9
Source:
Type:    Warning
Events:  <none>
```

Example of a `Failed` event:

```
Name:             sample-domain1.Failed.b9431b0717a5fc57
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Count:            42
Event Time:       <nil>
First Timestamp:  2021-12-14T14:05:22Z
Involved Object:
  API Version:   weblogic.oracle/v9
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
  UID:           c64d95c5-a8b9-4236-a2ab-43879192972b
Kind:            Event
Last Timestamp:  2021-12-14T15:26:56Z
Message:         Domain sample-domain1 failed due to 'Domain validation error': WebLogicCredentials secret 'sample-domain1-weblogic-credentials2' not found in namespace 'sample-domain1-ns'. Update the domain resource to correct the validation error.
Metadata:
  Creation Timestamp:  2021-12-14T14:05:22Z
  Resource Version:   5358407
  Self Link:          /api/v1/namespaces/sample-domain1-ns/events/sample-domain1.Failed.b9431b0717a5fc57
  UID:                6e7e877c-6440-4c0b-888b-bb47ea618400
Reason:               Failed
Reporting Component:  weblogic.operator
Reporting Instance:   weblogic-operator-588b9794f5-lff29
Source:
Type:    Warning
Events:  <none>

```

Example of domain processing completed after failure and retries:

The scenario is that the operator initially failed to process the domain resource because the specified image was missing, and then completed the processing during a retry after the image was recreated.
Note that this is not a full list of events; some of the events that are generated by the Kubernetes server have been removed to make the list less cluttered.

The output of command `kubectl get events -n sample-domain1-ns --sort-by=lastTimestamp`

```
LAST SEEN   TYPE      REASON             OBJECT                                         MESSAGE
7m51s       Normal    Created            domain/sample-domain1                          Domain sample-domain1 was created.
7m51s       Normal    SuccessfulCreate   job/sample-domain1-introspector                Created pod: sample-domain1-introspector-5ggh6
7m50s       Normal    Pulled             pod/sample-domain1-introspector-5ggh6          Container image "model-in-image:WLS-v1" already present on machine
7m50s       Normal    Created            pod/sample-domain1-introspector-5ggh6          Created container sample-domain1-introspector
7m50s       Normal    Scheduled          pod/sample-domain1-introspector-5ggh6          Successfully assigned sample-domain1-ns/sample-domain1-introspector-5ggh6 to doxiao-1
7m49s       Normal    Started            pod/sample-domain1-introspector-5ggh6          Started container sample-domain1-introspector
6m36s       Warning   DNSConfigForming   pod/sample-domain1-introspector-5ggh6          Search Line limits were exceeded, some search paths have been omitted, the applied search line is: sample-domain1-ns.svc.cluster.local svc.cluster.local cluster.local subnet1ad3phx.devweblogicphx.oraclevcn.com us.oracle.com oracle.com
6m36s       Normal    Completed          job/sample-domain1-introspector                Job completed
6m35s       Normal    Scheduled          pod/sample-domain1-admin-server                Successfully assigned sample-domain1-ns/sample-domain1-admin-server to doxiao-1
6m35s       Normal    Created            pod/sample-domain1-admin-server                Created container weblogic-server
6m35s       Normal    Pulled             pod/sample-domain1-admin-server                Container image "model-in-image:WLS-v1" already present on machine
6m34s       Normal    Started            pod/sample-domain1-admin-server                Started container weblogic-server
6m3s        Normal    Scheduled          pod/sample-domain1-managed-server2             Successfully assigned sample-domain1-ns/sample-domain1-managed-server2 to doxiao-1
6m3s        Normal    Scheduled          pod/sample-domain1-managed-server1             Successfully assigned sample-domain1-ns/sample-domain1-managed-server1 to doxiao-1
6m3s        Normal    NoPods             poddisruptionbudget/sample-domain1-cluster-1   No matching pods found
6m2s        Normal    Started            pod/sample-domain1-managed-server1             Started container weblogic-server
6m2s        Normal    Pulled             pod/sample-domain1-managed-server1             Container image "model-in-image:WLS-v1" already present on machine
6m2s        Normal    Started            pod/sample-domain1-managed-server2             Started container weblogic-server
6m2s        Normal    Pulled             pod/sample-domain1-managed-server2             Container image "model-in-image:WLS-v1" already present on machine
6m2s        Normal    Created            pod/sample-domain1-managed-server2             Created container weblogic-server
6m2s        Normal    Created            pod/sample-domain1-managed-server1             Created container weblogic-server
5m28s       Warning   Unhealthy          pod/sample-domain1-managed-server2             Readiness probe failed: Get "http://192.168.0.162:8001/weblogic/ready": dial tcp 192.168.0.162:8001: connect: connection refused
5m24s       Warning   Unhealthy          pod/sample-domain1-managed-server1             Readiness probe failed: Get "http://192.168.0.161:8001/weblogic/ready": dial tcp 192.168.0.161:8001: connect: connection refused
4m30s       Warning   Unavailable        domain/sample-domain1                          Domain sample-domain1 is unavailable: an insufficient number of its servers that are expected to be running are ready.
4m30s       Normal    SuccessfulCreate   job/sample-domain1-introspector                Created pod: sample-domain1-introspector-845w9
4m30s       Warning   Incomplete         domain/sample-domain1                          Domain sample-domain1 is incomplete for one or more of the following reasons: there are failures detected, there are pending server shutdowns, or not all servers expected to be running are ready and at their target image, auxiliary images, restart version, and introspect version.
4m30s       Normal    Scheduled          pod/sample-domain1-introspector-845w9          Successfully assigned sample-domain1-ns/sample-domain1-introspector-845w9 to doxiao-1
3m44s       Normal    Pulling            pod/sample-domain1-introspector-845w9          Pulling image "model-in-image:WLS-v2"
3m43s       Warning   Failed             pod/sample-domain1-introspector-845w9          Failed to pull image "model-in-image:WLS-v2": rpc error: code = Unknown desc = pull access denied for model-in-image, repository does not exist or may require 'docker login'
3m43s       Warning   Failed             pod/sample-domain1-introspector-845w9          Error: ErrImagePull
3m36s       Normal    Changed            domain/sample-domain1                          Domain sample-domain1 was changed.
3m16s       Normal    BackOff            pod/sample-domain1-introspector-845w9          Back-off pulling image "model-in-image:WLS-v2"
3m16s       Warning   DNSConfigForming   pod/sample-domain1-introspector-845w9          Search Line limits were exceeded, some search paths have been omitted, the applied search line is: sample-domain1-ns.svc.cluster.local svc.cluster.local cluster.local subnet1ad3phx.devweblogicphx.oraclevcn.com us.oracle.com oracle.com
3m16s       Warning   Failed             pod/sample-domain1-introspector-845w9          Error: ImagePullBackOff
3m16s       Warning   Failed             domain/sample-domain1                          Domain sample-domain1 failed due to 'Server pod error': Failure on pod 'sample-domain1-introspector-845w9' in namespace 'sample-domain1-ns': Back-off pulling image "model-in-image:WLS-v2".
2m30s       Warning   Failed             domain/sample-domain1                          Domain sample-domain1 failed due to 'Internal error': Job sample-domain1-introspector failed due to reason: DeadlineExceeded. ActiveDeadlineSeconds of the job is configured with 120 seconds. The job was started 120 seconds ago. Ensure all domain dependencies have been deployed (any secrets, config-maps, PVs, and PVCs that the domain resource references). Use kubectl describe for the job and its pod for more job failure information. The job may be retried by the operator with longer `ActiveDeadlineSeconds` value in each subsequent retry. Use spec.configuration.introspectorJobActiveDeadlineSeconds to increase the job timeout interval if the job still fails after the retries are exhausted. The time limit for retries can be configured in `domain.spec.failureRetryLimitMinutes`.. Will retry next at 2022-10-06T23:17:03.051414370Z and approximately every 120 seconds afterward until 2022-10-06T23:20:03.051414370Z if the failure is not resolved.. Will retry.
2m30s       Warning   DeadlineExceeded   job/sample-domain1-introspector                Job was active longer than specified deadline
2m30s       Normal    SuccessfulDelete   job/sample-domain1-introspector                Deleted pod: sample-domain1-introspector-845w9
2m29s       Warning   Failed             domain/sample-domain1                          Domain sample-domain1 failed due to 'Server pod error': Failure on pod 'sample-domain1-introspector-845w9' in namespace 'sample-domain1-ns': rpc error: code = Unknown desc = pull access denied for model-in-image, repository does not exist or may require 'docker login'.
2m25s       Normal    FailureResolved    domain/sample-domain1                          Domain sample-domain1 encountered some failures before, and those failures have been resolved
2m20s       Warning   Failed             domain/sample-domain1                          Domain sample-domain1 failed due to 'Kubernetes Api call error': Failure invoking 'create' on job  in namespace sample-domain1-ns: : object is being deleted: jobs.batch "sample-domain1-introspector" already exists.
2m19s       Warning   Failed             domain/sample-domain1                          Domain sample-domain1 failed due to 'Internal error': io.kubernetes.client.openapi.ApiException: . Introspection failed on try 2 of 5.
Introspection Error:
io.kubernetes.client.openapi.ApiException:  Will retry.
2m9s        Normal    Available          domain/sample-domain1                          Domain sample-domain1 is available: a sufficient number of its servers have reached the ready state.
2m8s        Normal    Completed          domain/sample-domain1                          Domain sample-domain1 is complete because all of the following are true: there is no failure detected, there are no pending server shutdowns, and all servers expected to be running are ready and at their target image, auxiliary images, restart version, and introspect version.

```

Example of a `StartManagingNamespace` event in the operator's namespace:

```
Name:             weblogic-operator-588b9794f5-fwstz.StartManagingNamespace.sample-domain1-ns.ba7fca932263194
Namespace:        sample-weblogic-operator-ns
Labels:           weblogic.createdByOperator=true
Annotations:      <none>
API Version:      v1
Count:            1
Event Time:       <nil>
First Timestamp:  2021-12-14T19:51:17Z
Involved Object:
  Kind:          Pod
  Name:          weblogic-operator-588b9794f5-fwstz
  Namespace:     sample-weblogic-operator-ns
  UID:           dd454033-f334-49de-8013-2955cf00449e
Kind:            Event
Last Timestamp:  2021-12-14T19:51:17Z
Message:         Start managing namespace sample-domain1-ns
Metadata:
  Creation Timestamp:  2021-12-14T19:51:17Z
  Resource Version:   5395096
  Self Link:          /api/v1/namespaces/sample-weblogic-operator-ns/events/weblogic-operator-588b9794f5-fwstz.StartManagingNamespace.sample-domain1-ns.ba7fca932263194
  UID:                a19800f5-12d5-43bc-a694-2f25710da8d4
Reason:               StartManagingNamespace
Reporting Component:  weblogic.operator
Reporting Instance:   weblogic-operator-588b9794f5-fwstz
Source:
Type:    Normal
Events:  <none>

```

Example of the sequence of operator generated events in a domain rolling restart after the domain resource's `image` changed, which is the output of the command `kubectl get events -n sample-domain1-ns --selector=weblogic.domainUID=sample-domain1,weblogic.createdByOperator=true --sort-by=lastTimestamp'.

```
LAST SEEN   TYPE      REASON             OBJECT                                         MESSAGE
4m31s       Normal    Changed            domain/sample-domain1                          Domain sample-domain1 was changed.
4m28s       Warning   Incomplete         domain/sample-domain1                          Domain sample-domain1 is incomplete for one or more of the following reasons: there are failures detected, there are pending server shutdowns, or not all servers expected to be running are ready and at their target image, auxiliary images, restart version, and introspect version.
4m28s       Warning   Unavailable        domain/sample-domain1                          Domain sample-domain1 is unavailable: an insufficient number of its servers that are expected to be running are ready.
4m27s       Normal    PodCycleStarting   domain/sample-domain1                          Replacing pod sample-domain1-admin-server
4m27s       Normal    RollStarting       domain/sample-domain1                          Rolling restart WebLogic server pods in domain sample-domain1
3m28s       Normal    Available          domain/sample-domain1                          Domain sample-domain1 became available
3m27s       Normal    PodCycleStarting   domain/sample-domain1                          Replacing pod sample-domain1-managed-server1
22m         Normal    PodCycleStarting   domain/sample-domain1                          Replacing pod sample-domain1-managed-server2
64s         Normal    RollCompleted      domain/sample-domain1                          Rolling restart of domain sample-domain1 completed
12s         Normal    Completed          domain/sample-domain1                          Domain sample-domain1 is complete because all of the following are true: there is no failure detected, there are no pending server shutdowns, and all servers expected to be running are ready and at their target image, auxiliary images, restart version, and introspect version.
```

Example of a `RollStarting` event:

```
Name:             sample-domain1.RollStarting.ba923815e652c0c9
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Count:            1
Event Time:       <nil>
First Timestamp:  2021-12-14T20:11:24Z
Involved Object:
  API Version:   weblogic.oracle/v9
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
  UID:           86e65656-39cc-4cd5-af61-a7cbaef51b83
Kind:            Event
Last Timestamp:  2021-12-14T20:11:24Z
Message:         Rolling restart WebLogic server pods in domain sample-domain1
Metadata:
  Creation Timestamp:  2021-12-14T20:11:24Z

  Resource Version:   5398296
  Self Link:          /api/v1/namespaces/sample-domain1-ns/events/sample-domain1.RollStarting.ba923815e652c0c9
  UID:                394d6cab-86d8-4686-bd6b-2d906bc4eac7
Reason:               RollStarting
Reporting Component:  weblogic.operator
Reporting Instance:   weblogic-operator-588b9794f5-fwstz
Source:
Type:    Normal
Events:  <none>


```

Example of a `PodCycleStarting` event:

```
Name:             sample-domain1.PodCycleStarting.7d34bc3232231f49
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Count:            1
Event Time:       <nil>
First Timestamp:  2021-05-18T02:01:18Z
Involved Object:
  API Version:   weblogic.oracle/v9
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
  UID:           5df7dcda-d606-4509-9a06-32f25e16e166
Kind:            Event
Last Timestamp:  2021-05-18T02:01:18Z
Message:         Replacing pod sample-domain1-managed-server1
Metadata:
  Creation Timestamp:  2021-05-18T02:01:18Z
  Resource Version:   12842530
  Self Link:          /api/v1/namespaces/sample-domain1-ns/events/sample-domain1.PodCycleStarting.7d34bc3232231f49
  UID:                4c6a203e-9b93-4b46-b9e3-1a448b52c7ca
Reason:               PodCycleStarting
Reporting Component:  weblogic.operator
Reporting Instance:   weblogic-operator-fc4ccc8b5-rh4v6
Source:
Type:    Normal
Events:  <none>

```
