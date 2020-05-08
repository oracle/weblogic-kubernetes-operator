+++
title = "Debugging"
date = 2020-03-11T16:45:16-05:00
weight = 60
pre = "<b> </b>"
description = "Debugging a deployed Model in Image domain."
+++

{{% notice info %}}
This feature is supported only in 3.0.0-rc1.
{{% /notice %}}

Here are some suggestions for debugging problems with Model in Image after your domain resource is deployed.

#### Contents

 - [Check the domain resource status](#check-the-domain-resource-status)
 - [Check the introspector job](#check-the-introspector-job)
 - [Check the WebLogic Server pods](#check-the-weblogic-server-pods)
 - [Check an operator log](#check-an-operator-log)



#### Check the domain resource status

To check the domain resource status: `kubectl -n MY_NAMESPACE describe domain MY_DOMAINUID`.

#### Check the introspector job

If your introspector job failed, then examine the `kubectl describe` of the job and its pod, and also examine its log, if one exists.

For example, assuming your domain UID is `sample-domain1` and your domain namespace is `sample-domain1-ns`:

  ```
  $ # here we see a failed introspector job pod among the domain's pods:
  $ kubectl -n sample-domain1-ns get pods -l weblogic.domainUID=sample-domain1
  NAME                                         READY   STATUS    RESTARTS   AGE
  sample-domain1-admin-server                  1/1     Running   0          19h
  sample-domain1-introspect-domain-job-v2l7k   0/1     Error     0          75m
  sample-domain1-managed-server1               1/1     Running   0          19h
  sample-domain1-managed-server2               1/1     Running   0          19h

  $ # let's look at the job's describe
  $ kubectl -n sample-domain1-ns describe job/sample-domain1-introspect-domain-job

  ...

  $ # now let's look at the job's pod describe, in particular look at its 'events'
  $ kubectl -n sample-domain1-ns describe pod/sample-domain1-introspect-domain-job-v2l7k

  ...

  $ # finally let's look at job's pod's log
  $ kubectl -n sample-domain1-ns logs job/sample-domain1-introspect-domain-job

  ...

  $ # alternative log command (will have same output as previous)
  # kubectl -n sample-domain1-ns logs pod/sample-domain1-introspect-domain-job-v2l7k
  ```

  A common reason for the introspector job to fail is because of an error in a model file. Here's some sample log output from an introspector job that shows such a failure:

  ```
  ...

  SEVERE Messages:
        1. WLSDPLY-05007: Model file /u01/wdt/models/model1.yaml,/weblogic-operator/wdt-config-map/..2020_03_19_15_43_05.993607882/datasource.yaml contains an unrecognized section: TYPOresources. The recognized sections are domainInfo, topology, resources, appDeployments, kubernetes
  ```

#### Check the WebLogic Server pods

If your introspector job succeeded, then there will be no introspector job or pod, the operator will create a `MY_DOMAIN_UID-weblogic-domain-introspect-cm` ConfigMap for your domain, and the operator will then run the domain's WebLogic pods.

If `kubectl -n MY_NAMESPACE get pods` reveals that your WebLogic pods have errors, then use `kubectl -n MY_NAMESPACE describe pod POD_NAME` and `kubectl -n MY_NAMESPACE logs POD_NAME` to debug.

#### Check an operator log

Look for `SEVERE` and `ERROR` level messages in your operator logs. For example:

  ```
  $ # find your operator
  $ kubectl get deployment --all-namespaces=true -l weblogic.operatorName

  NAMESPACE                     NAME                DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
  sample-weblogic-operator-ns   weblogic-operator   1         1         1            1           20h

  $ # grep operator log for SEVERE and WARNING level messages
  $ kubectl logs deployment/weblogic-operator -n sample-weblogic-operator-ns  \
    | egrep -e "level...(SEVERE|WARNING)"

  {"timestamp":"03-18-2020T20:42:21.702+0000","thread":11,"fiber":"","domainUID":"","level":"WARNING","class":"oracle.kubernetes.operator.helpers.HealthCheckHelper","method":"createAndValidateKubernetesVersion","timeInMillis":1584564141702,"message":"Kubernetes minimum version check failed. Supported versions are 1.13.5+,1.14.8+,1.15.7+, but found version v1.12.3","exception":"","code":"","headers":{},"body":""}
  ```

  You can filter out operator log messages specific to your `domainUID` by piping the above logs command through `grep "domainUID...MY_DOMAINUID"`. For example, assuming your operator is running in namespace `sample-weblogic-operator-ns` and your domain UID is `sample-domain1`:

  ```
  $ kubectl logs deployment/weblogic-operator -n sample-weblogic-operator-ns  \
    | egrep -e "level...(SEVERE|WARNING)" \
    | grep "domainUID...sample-domain1"
  ```
