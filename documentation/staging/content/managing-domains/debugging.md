+++
title = "Domain debugging"
date = 2020-03-11T16:45:16-05:00
weight = 9
pre = "<b> </b>"
description = "Debugging a deployed domain."
+++

Here are some suggestions for debugging problems with a domain after your Domain YAML file is deployed.

### Contents

 - [Check the Domain status](#check-the-domain-status)
 - [Check the Domain events](#check-the-domain-events)
 - [Check the introspector job](#check-the-introspector-job)
 - [Check the WebLogic Server pods](#check-the-weblogic-server-pods)
 - [Check the docs](#check-the-docs)
 - [Check the operator](#check-the-operator)

### Check the Domain status

To check the Domain status: `kubectl -n MY_NAMESPACE describe domain MY_DOMAINUID`.

If you are performing an online update to a running domain's WebLogic configuration,
then see [Online update status and labels]({{<relref "/managing-domains/model-in-image/runtime-updates#online-update-status-and-labels">}}).

### Check the Domain events

To check events for the Domain: `kubectl -n MY_NAMESPACE get events --sort-by='.lastTimestamp'`.

For more information, see [Domain events]({{< relref "/managing-domains/accessing-the-domain/domain-events.md" >}}).

### Check the introspector job

If your introspector job failed, then examine the `kubectl describe` of the job and its pod, and also examine its log, if one exists.

{{% notice tip %}}
To prevent the introspector job from retrying while you are debugging a failure, set the operator's Helm `domainPresenceFailureRetryMaxCount` parameter to `0`. For more information, see the [Configuration reference]({{< relref "/managing-operators/using-helm#domainpresencefailureretrymaxcount-and-domainpresencefailureretryseconds" >}}).
{{% /notice %}}

For example, assuming your domain UID is `sample-domain1` and your domain namespace is `sample-domain1-ns`.

Here we see a failed introspector job pod among the domain's pods:

   ```shell
  $ kubectl -n sample-domain1-ns get pods -l weblogic.domainUID=sample-domain1
  ```
  ```
  NAME                                         READY   STATUS    RESTARTS   AGE
  sample-domain1-admin-server                  1/1     Running   0          19h
  sample-domain1-introspector-v2l7k            0/1     Error     0          75m
  sample-domain1-managed-server1               1/1     Running   0          19h
  sample-domain1-managed-server2               1/1     Running   0          19h

  ```
- First, look at the output from the job's `describe` command.

  ```shell
  $ kubectl -n sample-domain1-ns describe job/sample-domain1-introspector

  ```

- Now, look at the job's pod `describe` output; in particular look at its `events`.

  ```shell
  $ kubectl -n sample-domain1-ns describe pod/sample-domain1-introspector-v2l7k
  ```

- Last, look at the job's pod's log.

  ```shell
  $ kubectl -n sample-domain1-ns logs job/sample-domain1-introspector
  ```


- Here's an alternative log command that will have same output as shown in the previous command.
  `$ kubectl -n sample-domain1-ns logs pod/sample-domain1-introspector-v2l7k`

A common reason for the introspector job to fail in a Model in Image domain is because of an error in a model file. Here's some sample log output from an introspector job that shows such a failure:
 ```
  ...

  SEVERE Messages:
        1. WLSDPLY-05007: Model file /u01/wdt/models/model1.yaml,/weblogic-operator/wdt-config-map/..2020_03_19_15_43_05.993607882/datasource.yaml contains an unrecognized section: TYPOresources. The recognized sections are domainInfo, topology, resources, appDeployments, kubernetes
  ```

{{% notice tip %}}
The introspector log is mirrored to the Domain resource `spec.logHome` directory
when `spec.logHome` is configured and `spec.logHomeEnabled` is true.
{{% /notice %}}


If a model file error references a model file in your `spec.configuration.model.configMap`, then you can correct the error by redeploying the ConfigMap with a corrected model file and then initiating a domain restart or roll. Similarly, if a model file error references a model file in your model image, then you can correct the error by deploying a corrected image, modifying your Domain YAML file to reference the new image, and then initiating a domain restart or roll.


### Check the WebLogic Server pods

If your introspector job succeeded, then there will be no introspector job or pod, the operator will create a `MY_DOMAIN_UID-weblogic-domain-introspect-cm` ConfigMap for your domain, and the operator will then run the domain's WebLogic Server pods.

If `kubectl -n MY_NAMESPACE get pods` reveals that your WebLogic Server pods have errors, then use `kubectl -n MY_NAMESPACE describe pod POD_NAME`, `kubectl -n MY_NAMESPACE logs POD_NAME`, and/or `kubectl -n MY_NAMESPACE get events --sort-by='.lastTimestamp'` to debug.

If you are performing an online update to a running domain's WebLogic configuration,
then see [Online update status and labels]({{<relref "/managing-domains/model-in-image/runtime-updates#online-update-status-and-labels">}}).


### Check the docs

Common issues that have corresponding documentation include:
- When a Domain YAML file is deployed and no introspector or WebLogic Server pods start,
  plus the operator log contains no mention of the domain,
  then check to make sure that the Domain's namespace has been set up to be monitored by an operator.
  See the operator [Namespace management]({{<relref "/managing-operators/namespace-management.md">}})
  and operator [Common mistakes and solutions]({{<relref "/managing-operators/common-mistakes.md">}}) documentation.
- If a `describe` of an introspector job or WebLogic Server pod reveals image access errors,
  see the [Cannot pull image]({{<relref "/faq/cannot-pull-image">}}) FAQ.

### Check the operator

If the problem is specific to the operator itself,
or its namespace management,
then consult the operator [Troubleshooting]({{<relref "/managing-operators/troubleshooting.md">}}) documentation.
