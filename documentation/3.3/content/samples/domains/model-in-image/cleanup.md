---
title: "Cleanup"
date: 2019-02-23T17:32:31-05:00
weight: 7
---

To remove the resources you have created in these samples:

1. Delete the resources associated with the domain.
   ```shell
   $ /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh -d sample-domain1
   ```
   ```shell
   $ /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh -d sample-domain2
   ```

   This deletes the domain and any related resources that are labeled with the domain UID `sample-domain1` and `sample-domain2`.

   It leaves the namespace intact, the operator running, the load balancer running (if installed), and the database running (if installed).

   > **Note**: When you delete a domain, the operator will detect your domain deletion and shut down its pods. Wait for these pods to exit before deleting the operator that monitors the `sample-domain1-ns` namespace. You can monitor this process using the command `kubectl get pods -n sample-domain1-ns --watch` (`ctrl-c` to exit).

2. If you set up the Traefik ingress controller:

   ```shell
   $ helm uninstall traefik-operator -n traefik
   ```
   ```shell
   $ kubectl delete namespace traefik
   ```

3. If you set up a database for `JRF` or the Update 4 use case:
   ```shell
   $ /tmp/weblogic-kubernetes-operator/kubernetes/samples/scripts/create-oracle-db-service/stop-db-service.sh
   ```

4. Delete the operator and its namespace:
   ```shell
   $ helm uninstall sample-weblogic-operator -n sample-weblogic-operator-ns
   ```
   ```shell
   $ kubectl delete namespace sample-weblogic-operator-ns
   ```

6. Delete the domain's namespace:
   ```shell
   $ kubectl delete namespace sample-domain1-ns
   ```

7. Delete the images you may have created in this sample:
   ```shell
   $ docker image rm model-in-image:WLS-v1
   ```
   ```shell
   $ docker image rm model-in-image:WLS-v2
   ```
   ```shell
   $ docker image rm model-in-image:JRF-v1
   ```
   ```shell
   $ docker image rm model-in-image:JRF-v2
   ```
