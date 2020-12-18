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
- [How to access domain events](#how-to-access-the-events)
- [Examples of generated events](#examples-of-generated-events)

#### Overview

This document describes Kubernetes events that the operator generates about domain resources that it manages, during key points of its domain processing workflow. These events provide an additional way of monitoring your domain resources. Note that the Kubernetes server also generates events for standard Kubernetes resources, such as pods, services, and jobs that the operator generates on behalf of deployed domain custom resources.

#### Operator-generated event types

The operator generates these event types, which indicate the following:

 *  `DomainCreated`: A new domain is created.
 *  `DomainChanged`: A change has been made to an existing domain.
 *  `DomainDeleted`: An existing domain has been deleted.
 *  `DomainProcessingStarting`: The operator has started to process a new domain or to update an existing domain. This event may be a result of a `DomainCreate`, `DomainChanged`, or `DomainDeleted` event, or a result of a retry after a failed attempt.
 *  `DomainProcessingFailed`: The operator has encountered a problem while it was processing the domain resource. The failure either could be a configuration error or a Kubernetes API error.
 *  `DomainProcessingRetrying`: The operator is going to retry the processing of a domain after it encountered an failure.
 *  `DomainProcessingCompleted`:  The operator successfully completed the processing of a domain resource.
 *  `DomainProcessingAborted`:  The operator stopped processing a domain when the operator encountered a fatal error or a failure that persisted after the specified maximum number of retries.
 *  `DomainValidationError`:  A validation error or warning is found in a domain resource. Please refer to the event message for details.

#### Operator-generated event details

Each operator-generated event contains the following fields:
 *  `metadata`
    *  `namespace`:  Same as the domain resource namespace.
    *  `labels`:   `weblogic.createdByOperator=true` and `weblogic.domainUID=<domainUID>`.
 *  `type`:  String field that describes the type of the event. Possible values are `Normal` or `Warning`.
 *  `reportingComponent`:  String that describes the component that reports the event. The value is `weblogic.operator` for all operator-generated events.
 *  `reportingInstance`:  String that describes the instance that reports the event. The value is the Kubernetes pod name of the operator instance that generates the event.
 *  `lastTimestamp`:  `DateTime` field that presents the timestamp of the last occurrence of this event.
 *  `reason`:  Short, machine understandable string that gives the reason for the transition to the object's current status.
 *  `message`:  String that describes the details of the event.
 *  `involvedObject`:  `V1ObjectReference` object that describes the Kubernetes resources with which this event is associated.
    *  `name`:  String that describes the name of the domain resource, which is the `domainUID`.
    *  `namespace`:  String that describes the namespace of the event, which is the namespace of the domain resource.
    *  `kind`:  String that describes the kind of the Kubernetes resource with which this event is associated. The value is `Domain` for all operator-generated events.
    *  `apiVersion`:  String that describes the `apiVersion` of the involved object, which is the `apiVersion` of the domain resource, for example, `weblogic.oracle/v8`.

#### How to access the events

To access the events that are associated with all domain resources in a particular namespace, run:

 ```none
 $ kubectl get events -n [namespace]
 ```

To get the events and sort them by their last timestamp, run:

```none
 $ kubectl get events -n [namespace] --sort-by=lastTimestamp
```

Example output of the command:

```none

LAST SEEN   TYPE      REASON                      OBJECT                           MESSAGE
35m         Normal    DomainCreated               domain/domain2                   Domain resource domain2 was created
35m         Normal    Scheduled                   pod/domain2-introspector-8cjjr   Successfully assigned ns-xfue/domain2-introspector-8cjjr to doxiao-1
35m         Normal    DomainProcessingStarting    domain/domain2                   Creating or updating Kubernetes presence for WebLogic Domain with UID domain2
35m         Normal    SuccessfulCreate            job/domain2-introspector         Created pod: domain2-introspector-8cjjr
35m         Normal    Started                     pod/domain2-introspector-8cjjr   Started container domain2-introspector
35m         Normal    Created                     pod/domain2-introspector-8cjjr   Created container domain2-introspector
35m         Normal    Pulled                      pod/domain2-introspector-8cjjr   Container image "mii-basic-image:2020-12-01-1606832008650" already present on machine
34m         Warning   DNSConfigForming            pod/domain2-introspector-8cjjr   Search Line limits were exceeded, some search paths have been omitted, the applied search line is: ns-xfue.svc.cluster.local svc.cluster.local cluster.local subnet1ad3phx.devweblogicphx.oraclevcn.com us.oracle.com oracle.com
34m         Normal    Scheduled                   pod/domain2-admin-server         Successfully assigned ns-xfue/domain2-admin-server to doxiao-1
34m         Normal    Pulled                      pod/domain2-admin-server         Container image "mii-basic-image:2020-12-01-1606832008650" already present on machine
34m         Normal    Started                     pod/domain2-admin-server         Started container weblogic-server
34m         Normal    Created                     pod/domain2-admin-server         Created container weblogic-server
33m         Normal    Scheduled                   pod/domain2-managed-server1      Successfully assigned ns-xfue/domain2-managed-server1 to doxiao-1
33m         Normal    DomainProcessingCompleted   domain/domain2                   Successfully completed processing domain resource domain2
33m         Normal    Scheduled                   pod/domain2-managed-server2      Successfully assigned ns-xfue/domain2-managed-server2 to doxiao-1
33m         Normal    Pulled                      pod/domain2-managed-server1      Container image "mii-basic-image:2020-12-01-1606832008650" already present on machine
33m         Normal    Pulled                      pod/domain2-managed-server2      Container image "mii-basic-image:2020-12-01-1606832008650" already present on machine
33m         Normal    Started                     pod/domain2-managed-server1      Started container weblogic-server
33m         Normal    Started                     pod/domain2-managed-server2      Started container weblogic-server
33m         Normal    Created                     pod/domain2-managed-server2      Created container weblogic-server
33m         Normal    Created                     pod/domain2-managed-server1      Created container weblogic-server

```

To get all the events that are generated by the operator, run:

```none
 $ kubectl get events -n [namespace] --selector=weblogic.createdByOperator=true
```

```

To get all the events that are generated by the operator for a particular domain resource, for example `sample-domain1`, run:

```none
 $ kubectl get events -n [namespace] --selector=weblogic.domainUID=sample-domain1 --sort-by=lastTimestamp
```

#### Examples of generated events

Here are some examples of operator-generated events from the output of the `kubectl describe event` or `kubectl get events` commands.

Example of a `DomainProcessingStarting` event:

```none

Name:             sample-domain1.DomainProcessingStarting.1606844080179
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Event Time:       <nil>
First Timestamp:  <nil>
Involved Object:
  API Version:   weblogic.oracle/v8
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
Kind:            Event
Last Timestamp:  2020-12-01T17:34:40Z
Message:         Creating or updating Kubernetes presence for WebLogic Domain with UID sample-domain1
Metadata:
  Creation Timestamp:  2020-12-01T17:34:40Z
  Resource Version:    1545504
  Self Link:           /api/v1/namespaces/sample-domain1-ns/events/sample-domain1DomainProcessingStarting1606844080179
  UID:                 ec1322d0-20f0-4aa8-8af4-b9524fd81ee9
Reason:                DomainProcessingStarting
Reporting Component:   weblogic.operator
Reporting Instance:    weblogic-operator-7c5577bb75-kgqfj
Source:
Type:    Normal
Events:  <none>

```

Example of a `DomainProcessingFailed` event:

```none
Name:             sample-domain1.DomainProcessingFailed.1606844109483
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Event Time:       <nil>
First Timestamp:  <nil>
Involved Object:
  API Version:   weblogic.oracle/v8
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
Kind:            Event
Last Timestamp:  2020-12-01T17:35:09Z
Message:         Failed to complete processing domain resource sample-domain1 due to: Back-off pulling image "domain-home-in-image:12.2.1.4", the processing will be retried if required
Metadata:
  Creation Timestamp:  2020-12-01T17:35:09Z
  Resource Version:    1545729
  Self Link:           /api/v1/namespaces/sample-domain1-ns/events/sample-domain1DomainProcessingFailed1606844109483
  UID:                 ff1eccee-e4db-4274-bf90-4fb6749ea4ef
Reason:                DomainProcessingFailed
Reporting Component:   weblogic.operator
Reporting Instance:    weblogic-operator-7c5577bb75-kgqfj
Source:
Type:    Warning
Events:  <none>

```

Example of a `DomainProcessingCompleted` event:

```none

Name:             sample-domain1.DomainProcessingCompleted.1606844496874
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Event Time:       <nil>
First Timestamp:  <nil>
Involved Object:
  API Version:   weblogic.oracle/v8
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
Kind:            Event
Last Timestamp:  2020-12-01T17:41:36Z
Message:         Successfully completed processing domain resource sample-domain1
Metadata:
  Creation Timestamp:  2020-12-01T17:41:36Z
  Resource Version:    1546682
  Self Link:           /api/v1/namespaces/sample-domain1-ns/events/sample-domain1DomainProcessingCompleted1606844496874
  UID:                 caec129f-7416-41b3-a2d6-cb4b12bd011f
Reason:                DomainProcessingCompleted
Reporting Component:   weblogic.operator
Reporting Instance:    weblogic-operator-7c5577bb75-kgqfj
Source:
Type:    Normal
Events:  <none>

```

Example of a `DomainProcessingAborted` event:

```none

Name:             sample-domain1.DomainProcessingAborted.1606855873248
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Event Time:       <nil>
First Timestamp:  <nil>
Involved Object:
  API Version:   weblogic.oracle/v8
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
Kind:            Event
Last Timestamp:  2020-12-01T20:51:13Z
Message:         Aborting the processing of domain resource sample-domain1 permanently due to: exceeded configured domainPresenceFailureRetryMaxCount: 5
Metadata:
  Creation Timestamp:  2020-12-01T20:51:13Z
  Resource Version:    1563981
  Self Link:           /api/v1/namespaces/sample-domain1-ns/events/sample-domain1.DomainProcessingAborted.1606855873248
  UID:                 87db90ca-f150-4045-bfb7-7eb9ca48ac1e
Reason:                DomainProcessingAborted
Reporting Component:   weblogic.operator
Reporting Instance:    weblogic-operator-7c5577bb75-vflcq
Source:
Type:    Warning
Events:  <none>

```
Example of a `DomainValidationError` event:

```none

Name:             sample-domain1.DomainValidationError.1608160013145
Namespace:        sample-domain1-ns
Labels:           weblogic.createdByOperator=true
                  weblogic.domainUID=sample-domain1
Annotations:      <none>
API Version:      v1
Event Time:       <nil>
First Timestamp:  <nil>
Involved Object:
  API Version:   weblogic.oracle/v8
  Kind:          Domain
  Name:          sample-domain1
  Namespace:     sample-domain1-ns
Kind:            Event
Last Timestamp:  2020-12-16T23:06:53Z
Message:         Validation error in domain resource domain1: Replica request of 5 exceeds the maximum dynamic server count of 2 configured for cluster cluster-1
Metadata:
  Creation Timestamp:  2020-12-16T23:06:53Z
  Resource Version:    11222690
  Self Link:           /api/v1/namespaces/sample-domain1-ns/events/sample-domain1.DomainValidationError.1608160013145
  UID:                 cd4e6a2f-8ddb-4902-90a7-d993bceb567d
Reason:                DomainValidationError
Reporting Component:   weblogic.operator
Reporting Instance:    weblogic-operator-67c75bc4bf-d4flw
Source:
Type:    Warning
Events:  <none>

```

Example of domain processing completed after failure and retries:

The scenario is that the operator initially failed to process the domain resource because the specified image was missing, and then completed the processing during a retry after the image was recreated.
Note that this is not a full list of events; some of the events that are generated by the Kubernetes server have been removed to make the list less cluttered.

```none

LAST SEEN   TYPE      REASON                      OBJECT                                  MESSAGE
5m30s       Normal    DomainProcessingStarting    domain/sample-domain1                   Creating or updating Kubernetes presence for WebLogic Domain with UID sample-domain1
5m30s       Normal    Scheduled                   pod/sample-domain1-introspector-jlxsj   Successfully assigned sample-domain1-ns/sample-domain1-introspector-jlxsj to doxiao-1
5m27s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login', the processing will be retried if required
5m14s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: Back-off pulling image "domain-home-in-image:12.2.1.4", the processing will be retried if required
5m2s        Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login', the processing will be retried if required
4m50s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: Back-off pulling image "domain-home-in-image:12.2.1.4", the processing will be retried if required
4m50s       Normal    Pulling                     pod/sample-domain1-introspector-jlxsj   Pulling image "domain-home-in-image:12.2.1.4"
4m49s       Warning   Failed                      pod/sample-domain1-introspector-jlxsj   Error: ErrImagePull
4m49s       Warning   Failed                      pod/sample-domain1-introspector-jlxsj   Failed to pull image "domain-home-in-image:12.2.1.4": rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login'
4m34s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login', the processing will be retried if required
4m20s       Normal    BackOff                     pod/sample-domain1-introspector-jlxsj   Back-off pulling image "domain-home-in-image:12.2.1.4"
4m20s       Warning   Failed                      pod/sample-domain1-introspector-jlxsj   Error: ImagePullBackOff
4m20s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: Back-off pulling image "domain-home-in-image:12.2.1.4", the processing will be retried if required
3m49s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login', the processing will be retried if required
3m36s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: Back-off pulling image "domain-home-in-image:12.2.1.4", the processing will be retried if required
2m30s       Warning   DeadlineExceeded            job/sample-domain1-introspector         Job was active longer than specified deadline
2m30s       Normal    SuccessfulDelete            job/sample-domain1-introspector         Deleted pod: sample-domain1-introspector-jlxsj
2m30s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: Back-off pulling image "domain-home-in-image:12.2.1.4", the processing will be retried if required
2m29s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: Job sample-domain1-introspector failed due to reason: DeadlineExceeded. ActiveDeadlineSeconds of the job is configured with 180 seconds. The job was started 180 seconds ago. Ensure all domain dependencies have been deployed (any secrets, config-maps, PVs, and PVCs that the domain resource references). Use kubectl describe for the job and its pod for more job failure information. The job may be retried by the operator up to 5 times with longer ActiveDeadlineSeconds value in each subsequent retry. Use tuning parameter domainPresenceFailureRetryMaxCount to configure max retries., the processing will be retried if required
2m20s       Normal    DomainProcessingRetrying    domain/sample-domain1                   Retrying the processing of domain resource sample-domain1 after one or more failed attempts
2m18s       Normal    Scheduled                   pod/sample-domain1-introspector-6227v   Successfully assigned sample-domain1-ns/sample-domain1-introspector-6227v to doxiao-1
2m18s       Normal    SuccessfulCreate            job/sample-domain1-introspector         Created pod: sample-domain1-introspector-6227v
2m18s       Normal    DomainProcessingStarting    domain/sample-domain1                   Creating or updating Kubernetes presence for WebLogic Domain with UID sample-domain1
2m15s       Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login', the processing will be retried if required
2m1s        Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: Back-off pulling image "domain-home-in-image:12.2.1.4", the processing will be retried if required
2m1s        Normal    Pulling                     pod/sample-domain1-introspector-6227v   Pulling image "domain-home-in-image:12.2.1.4"
2m          Warning   Failed                      pod/sample-domain1-introspector-6227v   Failed to pull image "domain-home-in-image:12.2.1.4": rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login'
2m          Warning   Failed                      pod/sample-domain1-introspector-6227v   Error: ErrImagePull
107s        Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login', the processing will be retried if required
107s        Warning   Failed                      pod/sample-domain1-introspector-6227v   Error: ImagePullBackOff
107s        Normal    BackOff                     pod/sample-domain1-introspector-6227v   Back-off pulling image "domain-home-in-image:12.2.1.4"
103s        Normal    DomainChanged               domain/sample-domain1                   Domain resource sample-domain1 was changed
102s        Warning   DomainProcessingFailed      domain/sample-domain1                   Failed to complete processing domain resource sample-domain1 due to: rpc error: code = Unknown desc = pull access denied for domain-home-in-image, repository does not exist or may require 'docker login', the processing will be retried if required
99s         Normal    Scheduled                   pod/sample-domain1-introspector-fqzjv   Successfully assigned sample-domain1-ns/sample-domain1-introspector-fqzjv to doxiao-1
99s         Normal    DomainProcessingStarting    domain/sample-domain1                   Creating or updating Kubernetes presence for WebLogic Domain with UID sample-domain1
99s         Normal    SuccessfulCreate            job/sample-domain1-introspector         Created pod: sample-domain1-introspector-fqzjv
98s         Normal    Created                     pod/sample-domain1-introspector-fqzjv   Created container sample-domain1-introspector
98s         Normal    Pulled                      pod/sample-domain1-introspector-fqzjv   Container image "domain-home-in-image:12.2.1.4" already present on machine
98s         Normal    Started                     pod/sample-domain1-introspector-fqzjv   Started container sample-domain1-introspector
78s         Normal    Scheduled                   pod/sample-domain1-admin-server         Successfully assigned sample-domain1-ns/sample-domain1-admin-server to doxiao-1
77s         Normal    Created                     pod/sample-domain1-admin-server         Created container weblogic-server
77s         Normal    Started                     pod/sample-domain1-admin-server         Started container weblogic-server
77s         Normal    Pulled                      pod/sample-domain1-admin-server         Container image "domain-home-in-image:12.2.1.4" already present on machine
45s         Normal    DomainProcessingCompleted   domain/sample-domain1                   Successfully completed processing domain resource sample-domain1
45s         Normal    Scheduled                   pod/sample-domain1-managed-server2      Successfully assigned sample-domain1-ns/sample-domain1-managed-server2 to doxiao-1
45s         Normal    Scheduled                   pod/sample-domain1-managed-server1      Successfully assigned sample-domain1-ns/sample-domain1-managed-server1 to doxiao-1
44s         Normal    Started                     pod/sample-domain1-managed-server2      Started container weblogic-server
44s         Normal    Started                     pod/sample-domain1-managed-server1      Started container weblogic-server
44s         Normal    Created                     pod/sample-domain1-managed-server2      Created container weblogic-server
44s         Normal    Pulled                      pod/sample-domain1-managed-server2      Container image "domain-home-in-image:12.2.1.4" already present on machine
44s         Normal    Pulled                      pod/sample-domain1-managed-server1      Container image "domain-home-in-image:12.2.1.4" already present on machine
44s         Normal    Created                     pod/sample-domain1-managed-server1      Created container weblogic-server

```
