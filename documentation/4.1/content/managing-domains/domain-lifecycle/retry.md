---
title: "Domain failure retry processing"
date: 2022-10-10T08:14:51-05:00
draft: false
weight: 7
description: "This document describes domain failure retry processing in the Oracle WebLogic Server in Kubernetes environment."
---

This document describes domain failure retry processing in the Oracle WebLogic Server in Kubernetes environment.

{{< table_of_contents >}}

### Overview

The WebLogic Kubernetes Operator may encounter various failures during its processing of a Domain resource.
Failures are reported using Kubernetes events and [conditions]({{< relref "/managing-domains/accessing-the-domain/status-conditions.md" >}})
in the `status.conditions` field in the Domain resource.
See [Domain debugging]({{< relref "/managing-domains/debugging#check-the-domain-status" >}}).
Failures fall into different categories and are handled differently by the operator, where most failures lead to automatic retries.
Refer to [Retry behavior]({{< relref "#retry-behavior" >}}) on tuning failure retry limits and intervals.

### Domain failure severities

Domain resource failures fall into three severity levels:

- Warnings
    - Mismatch in domain spec configuration and WebLogic domain topology that usually does not prevent the domain from becoming available.
      For example, replicas are configured too high.
- Severe failures
   - Most of the failures during domain processing are temporary failures that may either resolve at a later
     time without intervention, or be fixed by user actions without making any change to the domain resource
     or the cluster resource. The operator [periodically retries]({{< relref "#retry-behavior" >}})
     when it encounters this type of failure. \
     Examples:
     - Introspector job time out (`DeadlineExceeded` error).
     - `SEVERE` errors in the introspector log that do not contain the special marker string `FatalIntrospectorError`.
     - Temporary network issues.
     - Unauthorized to create some resources.
     - An exception from the operator.
     - Validation errors that can be fixed without changing the domain spec, for example, missing secrets or a missing ConfigMap.
- Fatal failures
    - Failures that are not automatically retried. The cause of the failure must be fixed, and the retry
      must be manually initiated by updating the domain as described in [Retry behavior]({{< relref "#retry-behavior" >}}). \
      Examples:
        - Fatal errors in the introspector log that contain the special marker string `FatalIntrospectorError`.
        - Validation errors in the domain resource that require changes to the domain spec.
        - Failures at the `Severe` level that have reached the expected [maximum retry time]({{< relref "#retry-behavior" >}}).

For reasons for Domain failures, see [Domain failure reasons]({{< relref "#domain-failure-reasons" >}}).

{{%expand "Click here for an example of domain status showing a failure and its severity." %}}
```yaml
Status:
   ...
  Conditions:
    Last Transition Time:  2022-10-10T23:48:09.157398Z
    Message:               10 replicas specified for cluster 'cluster-1' which has a maximum cluster size of 5
  10 replicas specified for cluster 'cluster-2' which has a maximum cluster size of 2
    Reason:                ReplicasTooHigh
    Severity:              Warning
    Status:                True
    Type:                  Failed
   ...
```
{{% /expand %}}

### Retry behavior

Domains that have failures with a severity of `Fatal` or `Warning` will not be retried. The domain status should contain a message indicating what action is needed to fix the failure condition.

Domains failures with a severity of `Severe` will be retried as follows:
- The operator calculates the next retry time based on the timestamp of the previous failure, so the next retry always occurs at the time that is the last failure timestamp plus a predefined retry interval.
  - The timestamp of the previous failure can be found in the `lastFailureTime` field in the domain status.
  - The retry interval is specified in the `failureRetryIntervalSeconds` field in the Domain spec. It has a default
    value of 120 seconds. A value of zero seconds means retry immediately after failure.
- The operator stops retrying a domain resource when the time elapsed since the initial failure exceeds a predefined maximum retry time.
    - The timestamp of the initial failure can be found in the `initialFailureTime` field the domain status.
    - The retry interval is specified in the `failureRetryLimitMinutes` field in the Domain spec. It has a default value of 1440 minutes (24 hours).
      A value of zero minutes will disable retries, which can be useful for accessing log files for debugging purposes.

The following is an example of domain status showing a failure with pending retries. This Domain resource is configured to have a
`failureRetryLimitMinutes` of 10 minutes. Note that the next retry is 120 seconds after the `Last Failure Time`,
and the retry until time is 10 minutes after the `Initial Failure Time`.
```yaml
Status:
  ...
  Initial Failure Time:      2022-10-11T23:16:21.851801Z
  Last Failure Time:         2022-10-11T23:21:53.109997Z
  Message:                   Failure on pod 'domain1-introspector-hlvwt' in namespace 'default': Back-off pulling image "oracle/weblogic:12214". Will retry next at 2022-10-11T23:23:53.109997240Z and approximately every 120 seconds afterward until 2022-10-11T23:26:21.851801Z if the failure is not resolved.
```

In this example, all retries failed to start the domain before the predefined retry time limit, and the domain status shows a `Fatal` failure with `Aborted` reason.
```yaml
Status:
  Clusters:
  Conditions:
    Last Transition Time:    2022-10-11T23:26:34.107662Z
    Message:                 The operator failed after retrying for 10 minutes. This time limit may be specified in spec.failureRetryLimitMinutes. Please resolve the error and then update domain.spec.introspectVersion to force another retry.
    Reason:                  Aborted
    Severity:                Fatal
    Status:                  True
    ...
```

To manually initiate an immediate retry, or to restart retries that have reached their
`spec.failureRetryLimitMinutes`, update a domain field that will cause immediate action by the operator.
For example, change `spec.introspectVersion` or `spec.restartVersion` as appropriate.
See [Startup and shutdown]({{< relref "/managing-domains/domain-lifecycle/startup#fields-that-cause-servers-to-be-restarted" >}})
and [Initiating introspection]({{< relref "/managing-domains/domain-lifecycle/introspection#initiating-introspection" >}})

### Domain failure reasons

The following is a list of reasons for failures that may be encountered by the operator while processing a Domain resource.

| Domain Failure Reason | Description                                                                                                                                                                                                                                       |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DomainInvalid`       | One of more configuration validation errors in the Domain resource, such as the `domainUID` is too long, or configuration overrides are used in a Model In Image domain.                                                                          |
| `Introspection`       | One or more `SEVERE` log messages is found in the introspector's log file.                                                                                                                                                                        |
| `Kubernetes`          | Unrecoverable response code received from a Kubernetes API call.                                                                                                                                                                                  |
| `ServerPod`           | One or more WebLogic Server pods failed or did not get into the ready state within a predefined maximum wait time as configured in `spec.serverPod.maxReadyWaitTimeSeconds` in the Domain resource, or the introspector job pod did not complete. |
| `ReplicasTooHigh`     | The replicas field is set or changed to a value that exceeds the maximum number of servers in the WebLogic cluster configuration.                                                                                                                 |
| `Internal`            | The operator encountered an internal exception while processing the Domain resource.                                                                                                                                                              |
| `TopologyMismatch`    | One or more servers or clusters configured in the domain resource do not exist in the WebLogic domain configuration, or the monitoring exporter port is specified and it conflicts with a server port.                                                |
| `Aborted`             | The introspector encountered a fatal error or the operator has exceeded the maximum retry time.                                                                                                                                                  |
