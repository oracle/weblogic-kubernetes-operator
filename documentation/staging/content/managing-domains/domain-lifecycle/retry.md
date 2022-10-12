---
title: "Domain Failure Retry Processing"
date: 2022-10-10T08:14:51-05:00
draft: false
weight: 8
description: "This document describes domain failure retry processing in the Oracle WebLogic Server in Kubernetes environment."
---

{{< table_of_contents >}}

### Overview

This document describes domain failure retry processing in the WebLogic Kubernetes Operator.

The WebLogic Kubernetes Operator may encounter various failures during its processing of a Domain resource. Failures fall into different categories and are handled differently by the Operator.

### Domain Failure Reasons

Here is a list of reasons for Domain resource failures that may be encountered by the Operator while processing a Domain resource:

| Domain Failure Reason | Description                                                                                                                                                                                        |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DomainInvalid        | One of more configuration validation errors in the Domain resource, such as `domainUID` is too long, or Configuration overrides is used in a model-in-image domain.                                |
| Introspection        | One or more SEVERE log messages is found in the introspector's log file.                                                                                                                           |
| Kubernetes           | Unrecoverable response code received from a Kubernetes API call.                                                                                                                                   |
| ServerPod            | One or more WebLogic server pod failed or did not get into the ready state within a predefined max wait time, or the introspector job pod did not complete.                                        |
| ReplicasTooHigh      | The replicas field is set or changed to a value that exceeds the maximum number of servers in the WebLogic cluster configuration.                                                                  |
| Internal             | The operator encountered an internal Exception while processing the Domain resource.                                                                                                               |
| TopologyMismatch     | One or more servers or clusters configured in the domain resource do not exist in the WebLogic domain configuration, or monitoring exporter port is specified and it conflicts with a server port. |
| Aborted              | The introspector encountered a FATAL error, or the operator has exceeded the maximum retry time.                                                                                                   |

### Domain Failure Severities

Domain resource failures fall into the 3 different severities:

1. Fatal failures
   - Failures that any further retries will also fail unless the cause of the failure if fixed. \
     Examples:
     1) FATAL errors in the introspector log that contains the special marker string `FatalIntrospectorError`
     2) Validation errors in domain resource that requires changes to the domain spec.
     3) Failures in the Severe category have reached the expected maximum retry time.
2. Severe failures
   - Most of the failures during domain processing are temporary failures that may either resolve in a later 
     time without user intervention, or be fixed by the user's actions without making any change to the domain resource. \
     Examples:
     1) introspector job time out (DeadlineExceeded error)
     2) SEVERE errors in the introspector log that does not contain the special marker string `FatalIntrospectorError`
     3) temporary network issues
     4) unauthorized to create some resources
     5) Exception from the operator itself
     6) Validation errors that can be fixed without changing the domain spec, for example, missing secrets, or missing configmap.
3. Warnings
   - Mismatch in domain spec configuration and WebLogic domain topology that usually do not prevent the domain from becoming available. 
     For example, Replicas are configured too high
     
{{%expand "Click here to view an example of domain status showing a failure with severity and reason." %}}
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

### Retry Behavior

Domains that have failures with severity of `Fatal` or `Warning` will not be retried. The domain status should contain a message indicating what action is needed to fix the failure condition. 

Domains that have severe failure conditions will be retried as follows:
- The operator calculates the next retry time based on the timestamp of the previous failure, so the next retry always occurs at the time that is the last failure timestamp plus a predefined retry interval.
  - The timestamp of the previous failure can be found in the `lastFailureTime` field in the domain status. 
  - The retry interval is specified in the `failureRetryIntervalSeconds` field in Domain spec. It has a default 
    value of 120 seconds. A value of 0 seconds means retry immediately after failure.
- The operator stops retrying a domain resource when the time elapse since the initial failure exceeds a predefined maximum retry time.
    - The timestamp of the initial failure can be found in the `initialFailureTime` field the domain status.
    - The retry interval is specified in the `failureRetryLimitMinutes` field in Domain spec. It has a default value of 1440 minutes (24 hours).
      A value of `0` will disable retries, which can be useful for accessing log files for debugging purposes.

An example of domain status showing a failure with pending retries. This Domain resource is configured to have a 
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





