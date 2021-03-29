+++
title = "Domain events"
date = 2020-11-30T16:43:45-05:00
weight = 9
pre = "<b> </b>"
+++

#### Contents

- [Overview](#overview)
- [Operator-generated event types](#operator-generated-event-types)
- [Operator-generated event details](#operator-generated-event-details)
- [How to access the events](#how-to-access-the-events)
- [Examples of generated events](#examples-of-generated-events)


#### Overview

This document describes Kubernetes events that the operator generates about resources that it manages, during key points of its processing workflow. These events provide an additional way of monitoring your domain resources. Note that the Kubernetes server also generates events for standard Kubernetes resources, such as pods, services, and jobs that the operator generates on behalf of deployed domain custom resources.

#### Operator-generated event types

The operator generates these event types in a domain namespace, which indicate the following:

 *  `DomainCreated`: A new domain is created.
 *  `DomainChanged`: A change has been made to an existing domain.
 *  `DomainDeleted`: An existing domain has been deleted.
 *  `DomainProcessingStarting`: The operator has started to process a new domain or to update an existing domain. This event may be a result of a `DomainCreate`, `DomainChanged`, or `DomainDeleted` event, or a result of a retry after a failed attempt.
 *  `DomainProcessingFailed`: The operator has encountered a problem while it was processing the domain resource. The failure either could be a configuration error or a Kubernetes API error.
 *  `DomainProcessingRetrying`: The operator is going to retry the processing of a domain after it encountered an failure.
 *  `DomainProcessingCompleted`:  The operator successfully completed the processing of a domain resource.
 *  `DomainProcessingAborted`:  The operator stopped processing a domain when the operator encountered a fatal error or a failure that persisted after the specified maximum number of retries.
 *  `DomainValidationError`:  A validation error or warning is found in a domain resource. Please refer to the event message for details.
 *  `NamespaceWatchingStarted`: The operator has started watching for domains in a namespace.
 *  `NamespaceWatchingStopped`: The operator has stopped watching for domains in a namespace. Note that the creation of this event in a domain namespace is the operator's best effort only; the event will not be generated if the required Kubernetes privilege is removed when a namespace is no longer managed by the operator.

The operator also generates these event types in the operator's namespace, which indicate the following:

*  `StartManagingNamespace`: The operator has started managing domains in a namespace.
*  `StopManagingNamespace`: The operator has stopped managing domains in a namespace. 

#### Operator-generated event details

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
    *  `apiVersion`:  String that describes the `apiVersion` of the involved object, which is the `apiVersion` of the domain resource, for example, `weblogic.oracle/v8`, for a domain event or unset for a namespace event.
    *  `UID`: String that describes the unique identifier of the object that is generated by the Kubernetes server.
    
#### How to access the events

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
$ kubectl get events -n [namespace] --selector=weblogic.domainUID=sample-domain1&&weblogic.createdByOperator=true --sort-by=lastTimestamp
```

#### Examples of generated events

Here are some examples of operator-generated events from the output of the `kubectl describe event` or `kubectl get events` commands for `sample-domain1` in namespace `sample-domain1-ns`.

Example of a `DomainProcessingStarting` event:
```
Name:             sample-domain1.DomainProcessingStarting.1c415c9cf54c0f2
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Count:            4
Event Time:       <nil>
First Timestamp:  2021-01-19T20:06:21Z
Involved Object:
  API Version:   weblogic.oracle/v8
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
  UID:           9dc647fb-b9d2-43f8-bac7-69258560a99a
Kind:            Event
Last Timestamp:  2021-01-19T20:12:29Z
Message:         Creating or updating Kubernetes presence for WebLogic Domain with UID sample-domain1
Metadata:
  Creation Timestamp:  2021-01-19T20:06:21Z
  Resource Version:   2635264
  Self Link:          /api/v1/namespaces/sample-domain1-ns/events/sample-domain1.DomainProcessingStarting.1c415c9cf54c0f2
  UID:                093383eb-6fc6-46c7-aaa4-c4ca8399bfab
Reason:               DomainProcessingStarting
Reporting Component:  weblogic.operator
Reporting Instance:   weblogic-operator-67c9999d99-rff62
Source:
Type:    Normal
Events:  <none>
```

Example of a `DomainProcessingFailed` event:

```
Name:             sample-domain1.DomainProcessingFailed.1c416683eb212c63
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Count:            12
Event Time:       <nil>
First Timestamp:  2021-01-19T20:06:24Z
Involved Object:
  API Version:   weblogic.oracle/v8
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
  UID:           9dc647fb-b9d2-43f8-bac7-69258560a99a
Kind:            Event
Last Timestamp:  2021-01-19T20:12:11Z
Message:         Failed to complete processing domain resource sample-domain1 due to: rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login', the processing will be retried if needed
Metadata:
  Creation Timestamp:  2021-01-19T20:06:24Z
  Resource Version:   2635213
  Self Link:          /api/v1/namespaces/sample-domain1-ns/events/sample-domain1.DomainProcessingFailed.1c416683eb212c63
  UID:                f3e017ea-1b38-4ba3-bd58-0ee731f3ae4e
Reason:               DomainProcessingFailed
Reporting Component:  weblogic.operator
Reporting Instance:   weblogic-operator-67c9999d99-rff62
Source:
Type:    Warning
Events:  <none>
```

Example of a `DomainProcessingCompleted` event:

```
Name:             sample-domain1.DomainProcessingCompleted.1c478d91fdada118
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Count:            1
Event Time:       <nil>
First Timestamp:  2021-01-19T20:13:07Z
Involved Object:
  API Version:   weblogic.oracle/v8
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
  UID:           9dc647fb-b9d2-43f8-bac7-69258560a99a
Kind:            Event
Last Timestamp:  2021-01-19T20:13:07Z
Message:         Successfully completed processing domain resource sample-domain1
Metadata:
  Creation Timestamp:  2021-01-19T20:13:07Z
  Resource Version:   2635401
  Self Link:          /api/v1/namespaces/sample-domain1-ns/events/sample-domain1.DomainProcessingCompleted.1c478d91fdada118
  UID:                ea7734af-31bc-4f8e-b02b-5ef6b240749e
Reason:               DomainProcessingCompleted
Reporting Component:  weblogic.operator
Reporting Instance:   weblogic-operator-67c9999d99-rff62
Source:
Type:    Normal
Events:  <none>
```

Example of domain processing completed after failure and retries:

The scenario is that the operator initially failed to process the domain resource because the specified image was missing, and then completed the processing during a retry after the image was recreated.
Note that this is not a full list of events; some of the events that are generated by the Kubernetes server have been removed to make the list less cluttered.

The output of command `kubectl get events -n sample-domain1-ns --sort-by=lastTimestamp`

```
LAST SEEN   TYPE      REASON                      OBJECT                                  MESSAGE
7m54s       Normal    NamespaceWatchingStarted    namespace/sample-domain1-ns             Started watching namespace sample-domain1-ns
7m44s       Normal    DomainCreated               domain/sample-domain1                   Domain resource sample-domain1 was created
7m43s       Normal    SuccessfulCreate            job/sample-domain1-introspector         Created pod: sample-domain1-introspector-d42rf
7m43s       Normal    Scheduled                   pod/sample-domain1-introspector-d42rf   Successfully assigned sample-domain1-ns/sample-domain1-introspector-d42rf to doxiao-1
7m4s        Normal    Pulling                     pod/sample-domain1-introspector-d42rf   Pulling image "domain-home-in-image:12.2.1.4"
7m2s        Warning   Failed                      pod/sample-domain1-introspector-d42rf   Error: ErrImagePull
7m2s        Warning   Failed                      pod/sample-domain1-introspector-d42rf   Failed to pull image "domain-home-in-image:12.2.1.4": rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login'
6m38s       Warning   Failed                      pod/sample-domain1-introspector-d42rf   Error: ImagePullBackOff
6m38s       Normal    BackOff                     pod/sample-domain1-introspector-d42rf   Back-off pulling image "domain-home-in-image:12.2.1.4"
5m43s       Warning   DeadlineExceeded            job/sample-domain1-introspector         Job was active longer than specified deadline
5m43s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: Job sample-domain1-introspector failed due to reason: DeadlineExceeded. ActiveDeadlineSeconds of the job is configured with 120 seconds. The job was started 120 seconds ago. Ensure all domain dependencies have been deployed (any secrets, config-maps, PVs, and PVCs that the domain resource references). Use kubectl describe for the job and its pod for more job failure information. The job may be retried by the operator up to 5 times with longer ActiveDeadlineSeconds value in each subsequent retry. Use tuning parameter domainPresenceFailureRetryMaxCount to configure max retries., the processing will be retried if needed
5m43s       Normal    SuccessfulDelete            job/sample-domain1-introspector         Deleted pod: sample-domain1-introspector-d42rf
5m32s       Normal    Scheduled                   pod/sample-domain1-introspector-cmxjs   Successfully assigned sample-domain1-ns/sample-domain1-introspector-cmxjs to doxiao-1
5m32s       Normal    SuccessfulCreate            job/sample-domain1-introspector         Created pod: sample-domain1-introspector-cmxjs 
4m52s       Normal    Pulling                     pod/sample-domain1-introspector-cmxjs   Pulling image "domain-home-in-image:12.2.1.4"
4m50s       Warning   Failed                      pod/sample-domain1-introspector-cmxjs   Failed to pull image "domain-home-in-image:12.2.1.4": rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login'
4m50s       Warning   Failed                      pod/sample-domain1-introspector-cmxjs   Error: ErrImagePull
4m27s       Warning   Failed                      pod/sample-domain1-introspector-cmxjs   Error: ImagePullBackOff
4m27s       Normal    BackOff                     pod/sample-domain1-introspector-cmxjs   Back-off pulling image "domain-home-in-image:12.2.1.4"
2m32s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: Job sample-domain1-introspector failed due to reason: DeadlineExceeded. ActiveDeadlineSeconds of the job is configured with 180 seconds. The job was started 180 seconds ago. Ensure all domain dependencies have been deployed (any secrets, config-maps, PVs, and PVCs that the domain resource references). Use kubectl describe for the job and its pod for more job failure information. The job may be retried by the operator up to 5 times with longer ActiveDeadlineSeconds value in each subsequent retry. Use tuning parameter domainPresenceFailureRetryMaxCount to configure max retries., the processing will be retried if needed
2m32s       Normal    SuccessfulDelete            job/sample-domain1-introspector         Deleted pod: sample-domain1-introspector-cmxjs
2m32s       Warning   DeadlineExceeded            job/sample-domain1-introspector         Job was active longer than specified deadline
2m22s       Normal    DomainProcessingRetrying    domain/sample-domain1                   Retrying the processing of domain resource sample-domain1 after one or more failed attempts
2m20s       Normal    SuccessfulCreate            job/sample-domain1-introspector         Created pod: sample-domain1-introspector-ght6p
2m20s       Normal    Scheduled                   pod/sample-domain1-introspector-ght6p   Successfully assigned sample-domain1-ns/sample-domain1-introspector-ght6p to doxiao-1
2m17s       Normal    BackOff                     pod/sample-domain1-introspector-ght6p   Back-off pulling image "domain-home-in-image:12.2.1.4"
2m17s       Warning   Failed                      pod/sample-domain1-introspector-ght6p   Error: ImagePullBackOff
2m7s        Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: Back-off pulling image "domain-home-in-image:12.2.1.4", the processing will be retried if needed
2m7s        Normal    Pulling                     pod/sample-domain1-introspector-ght6p   Pulling image "domain-home-in-image:12.2.1.4"
2m5s        Warning   Failed                      pod/sample-domain1-introspector-ght6p   Error: ErrImagePull
2m5s        Warning   Failed                      pod/sample-domain1-introspector-ght6p   Failed to pull image "domain-home-in-image:12.2.1.4": rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login'
114s        Normal    Created                     pod/sample-domain1-introspector-ght6p   Created container sample-domain1-introspector
114s        Normal    Pulled                      pod/sample-domain1-introspector-ght6p   Container image "domain-home-in-image:12.2.1.4" already present on machine
114s        Normal    Started                     pod/sample-domain1-introspector-ght6p   Started container sample-domain1-introspector
114s        Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login', the processing will be retried if needed
98s         Normal    Completed                   job/sample-domain1-introspector         Job completed
98s         Warning   DNSConfigForming            pod/sample-domain1-introspector-ght6p   Search Line limits were exceeded, some search paths have been omitted, the applied search line is: sample-domain1-ns.svc.cluster.local svc.cluster.local cluster.local subnet1ad3phx.devweblogicphx.oraclevcn.com us.oracle.com oracle.com
97s         Normal    Killing                     pod/sample-domain1-introspector-ght6p   Stopping container sample-domain1-introspector
96s         Normal    Scheduled                   pod/sample-domain1-admin-server         Successfully assigned sample-domain1-ns/sample-domain1-admin-server to doxiao-1
96s         Normal    DomainProcessingStarting    domain/sample-domain1                   Creating or updating Kubernetes presence for WebLogic Domain with UID sample-domain1
95s         Normal    Pulled                      pod/sample-domain1-admin-server         Container image "domain-home-in-image:12.2.1.4" already present on machine
95s         Normal    Started                     pod/sample-domain1-admin-server         Started container weblogic-server
95s         Normal    Created                     pod/sample-domain1-admin-server         Created container weblogic-server
59s         Normal    Scheduled                   pod/sample-domain1-managed-server1      Successfully assigned sample-domain1-ns/sample-domain1-managed-server1 to doxiao-1
58s         Normal    Scheduled                   pod/sample-domain1-managed-server2      Successfully assigned sample-domain1-ns/sample-domain1-managed-server2 to doxiao-1
58s         Normal    DomainProcessingCompleted   domain/sample-domain1                   Successfully completed processing domain resource sample-domain1
57s         Normal    Pulled                      pod/sample-domain1-managed-server1      Container image "domain-home-in-image:12.2.1.4" already present on machine
57s         Normal    Started                     pod/sample-domain1-managed-server1      Started container weblogic-server
57s         Normal    Created                     pod/sample-domain1-managed-server1      Created container weblogic-server
57s         Normal    Pulled                      pod/sample-domain1-managed-server2      Container image "domain-home-in-image:12.2.1.4" already present on machine
57s         Normal    Created                     pod/sample-domain1-managed-server2      Created container weblogic-server
57s         Normal    Started                     pod/sample-domain1-managed-server2      Started container weblogic-server
```

Example of a `StartManagingNamespace` event in the operator's namespace:

```
Name:             weblogic-operator-67c9999d99-clgpw.StartManagingNamespace.sample-domain1-ns.5f68d728281fcbaf
Namespace:        sample-weblogic-operator-ns
Labels:           weblogic.createdByOperator=true
Annotations:      <none>
API Version:      v1
Count:            1
Event Time:       <nil>
First Timestamp:  2021-02-01T21:04:02Z
Involved Object:
  Kind:          Pod
  Name:          weblogic-operator-67c9999d99-clgpw
  Namespace:     sample-weblogic-operator-ns
Kind:            Event
Last Timestamp:  2021-02-01T21:04:02Z
Message:         Start managing namespace sample-domain1-ns
Metadata:
  Creation Timestamp:  2021-02-01T21:04:02Z
  Resource Version:   5119747
  Self Link:          /api/v1/namespaces/sample-weblogic-operator-ns/events/weblogic-operator-67c9999d99-clgpw.StartManagingNamespace.sample-domain1-ns.5f68d728281fcbaf
  UID:                6d01f382-d36e-47fa-9222-6f26e90c5be2
Reason:               StartManagingNamespace
Reporting Component:  weblogic.operator
Reporting Instance:   weblogic-operator-67c9999d99-clgpw
Source:
Type:    Normal
Events:  <none>
```