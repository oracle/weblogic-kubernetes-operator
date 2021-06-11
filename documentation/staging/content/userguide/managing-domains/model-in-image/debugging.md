+++
title = "Debugging"
date = 2020-03-11T16:45:16-05:00
weight = 60
pre = "<b> </b>"
description = "Debugging a deployed Model in Image domain."
+++

Here are some suggestions for debugging problems with Model in Image after your Domain YAML file is deployed.

### Contents

 - [Check the Domain status](#check-the-domain-status)
 - [Check the Domain events](#check-the-domain-events)
 - [Check the introspector job](#check-the-introspector-job)
 - [Check the WebLogic Server pods](#check-the-weblogic-server-pods)
 - [Check the operator log](#check-the-operator-log)
 - [Check the FAQ](#check-the-faq)


### Check the Domain status

To check the Domain status: `kubectl -n MY_NAMESPACE describe domain MY_DOMAINUID`.

If you are performing an online update to a running domain's WebLogic configuration,
then see [Online update status and labels]({{<relref "/userguide/managing-domains/model-in-image/runtime-updates#online-update-status-and-labels">}}).

### Check the Domain events

To check events for the Domain: `kubectl -n MY_NAMESPACE get events --sort-by='.lastTimestamp'`.

For more information, see [Domain events]({{< relref "/userguide/managing-domains/domain-events.md" >}}).

### Check the introspector job

If your introspector job failed, then examine the `kubectl describe` of the job and its pod, and also examine its log, if one exists.

{{% notice tip %}}
To prevent the introspector job from retrying while you are debugging a failure, set the operator's Helm `domainPresenceFailureRetryMaxCount` parameter to `0`. For more information, see  [Manage operators -> Use the operator -> Use Helm]({{<relref "/userguide/managing-operators/using-the-operator/using-helm">}}).
{{% /notice %}}

For example, assuming your domain UID is `sample-domain1` and your domain namespace is `sample-domain1-ns`:

  ```
  $ # here we see a failed introspector job pod among the domain's pods:
  ```
   ```shell
  $ kubectl -n sample-domain1-ns get pods -l weblogic.domainUID=sample-domain1
  ```
  ```
  NAME                                         READY   STATUS    RESTARTS   AGE
  sample-domain1-admin-server                  1/1     Running   0          19h
  sample-domain1-introspector-v2l7k            0/1     Error     0          75m
  sample-domain1-managed-server1               1/1     Running   0          19h
  sample-domain1-managed-server2               1/1     Running   0          19h

  $ # let's look at the job's describe
  ```
  ```shell
  $ kubectl -n sample-domain1-ns describe job/sample-domain1-introspector

  ```
  ```
  $ # now let's look at the job's pod describe, in particular look at its 'events'
  ```
  ```shell
  $ kubectl -n sample-domain1-ns describe pod/sample-domain1-introspector-v2l7k
  ```
  ```
  $ # finally let's look at job's pod's log
  ```
  ```shell
  $ kubectl -n sample-domain1-ns logs job/sample-domain1-introspector
  ```
  ```
  $ # alternative log command (will have same output as previous)
  # kubectl -n sample-domain1-ns logs pod/sample-domain1-introspector-v2l7k

  A common reason for the introspector job to fail is because of an error in a model file. Here's some sample log output from an introspector job that shows such a failure:
  ...

  SEVERE Messages:
        1. WLSDPLY-05007: Model file /u01/wdt/models/model1.yaml,/weblogic-operator/wdt-config-map/..2020_03_19_15_43_05.993607882/datasource.yaml contains an unrecognized section: TYPOresources. The recognized sections are domainInfo, topology, resources, appDeployments, kubernetes
  ```

{{% notice tip %}}
The introspector log is mirrored to the Domain resource `spec.logHome` directory
when `spec.logHome` is configured and `spec.logHomeEnabled` is true.
{{% /notice %}}

{{% notice tip %}}
If a model file error references a model file in your `spec.configuration.model.configMap`, then you can correct the error by redeploying the ConfigMap with a corrected model file and then initiating a domain restart or roll. Similarly, if a model file error references a model file in your model image, then you can correct the error by deploying a corrected image, modifying your Domain YAML file to reference the new image, and then initiating a domain restart or roll.
{{% /notice %}}

### Check the WebLogic Server pods

If your introspector job succeeded, then there will be no introspector job or pod, the operator will create a `MY_DOMAIN_UID-weblogic-domain-introspect-cm` ConfigMap for your domain, and the operator will then run the domain's WebLogic Server pods.

If `kubectl -n MY_NAMESPACE get pods` reveals that your WebLogic Server pods have errors, then use `kubectl -n MY_NAMESPACE describe pod POD_NAME`, `kubectl -n MY_NAMESPACE logs POD_NAME`, and/or `kubectl -n MY_NAMESPACE get events --sort-by='.lastTimestamp'` to debug.

If you are performing an online update to a running domain's WebLogic configuration,
then see [Online update status and labels]({{<relref "/userguide/managing-domains/model-in-image/runtime-updates#online-update-status-and-labels">}}).

### Check the operator log

Look for `SEVERE` and `ERROR` level messages in your operator logs. For example:

  ```
  $ # find your operator
  ```
  ```shell
  $ kubectl get deployment --all-namespaces=true -l weblogic.operatorName
  ```
  ```
  NAMESPACE                     NAME                DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
  sample-weblogic-operator-ns   weblogic-operator   1         1         1            1           20h

  $ # grep operator log for SEVERE and WARNING level messages
  ```
  ```shell
  $ kubectl logs deployment/weblogic-operator -n sample-weblogic-operator-ns  \
    | egrep -e "level...(SEVERE|WARNING)"
  ```
  ```json
  {"timestamp":"03-18-2020T20:42:21.702+0000","thread":11,"fiber":"","domainUID":"","level":"WARNING","class":"oracle.kubernetes.operator.helpers.HealthCheckHelper","method":"createAndValidateKubernetesVersion","timeInMillis":1584564141702,"message":"Kubernetes minimum version check failed. Supported versions are 1.13.5+,1.14.8+,1.15.7+, but found version v1.12.3","exception":"","code":"","headers":{},"body":""}
  ```

  You can filter out operator log messages specific to your `domainUID` by piping the above logs command through `grep "domainUID...MY_DOMAINUID"`. For example, assuming your operator is running in namespace `sample-weblogic-operator-ns` and your domain UID is `sample-domain1`:

  ```shell
  $ kubectl logs deployment/weblogic-operator -n sample-weblogic-operator-ns  \
    | egrep -e "level...(SEVERE|WARNING)" \
    | grep "domainUID...sample-domain1"
  ```

### Check the FAQ

Common issues that have corresponding FAQ entries include:
- When a Domain YAML file is deployed and no introspector or WebLogic Server pods start, plus the operator log contains no mention of the domain, then check to make sure that the Domain's namespace has been set up to be monitored by an operator. See the [Managing domain namespaces FAQ]({{<relref "/faq/namespace-management">}}).
- If a `describe` of an introspector job or WebLogic Server pod reveals image access errors, see the [Cannot pull image FAQ]({{<relref "/faq/cannot-pull-image">}}).
