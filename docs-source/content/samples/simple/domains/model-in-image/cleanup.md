---
title: "Cleanup"
date: 2019-02-23T17:32:31-05:00
weight: 4
---


To remove the resources you have created in the samples:

1. Delete the domain resources.
   ```
   $ /tmp/operator-source/kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh -d sample-domain1
   $ /tmp/operator-source/kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh -d sample-domain2
   ```

   This deletes the domain and any related resources that are labeled with the domain UID `sample-domain1` and `sample-domain2`.

   It leaves the namespace intact, the operator running, the load balancer running (if installed), and the database running (if installed).

   > **Note**: When you delete a domain, the operator will detect your domain deletion and shut down its pods. Wait for these pods to exit before deleting the operator that monitors the `sample-domain1-ns` namespace. You can monitor this process using the command `kubectl get pods -n sample-domain1-ns --watch` (`ctrl-c` to exit).

2. If you set up the Traefik ingress controller:

   ```
   $ helm delete --purge traefik-operator
   $ kubectl delete namespace traefik
   ```

3. If you set up a database for `JRF`:
   ```
   $ /tmp/operator-source/kubernetes/samples/scripts/create-oracle-db-service/stop-db-service.sh
   ```

4. Delete the operator and its namespace:
   ```
   $ helm delete --purge sample-weblogic-operator
   $ kubectl delete namespace sample-weblogic-operator-ns
   ```

6. Delete the domain's namespace:
   ```
   $ kubectl delete namespace sample-domain1-ns
   ```

7. Delete the images you may have created in this sample:
   ```
   $ docker image rm model-in-image:WLS-v1
   $ docker image rm model-in-image:WLS-v2
   $ docker image rm model-in-image:JRF-v1
   $ docker image rm model-in-image:JRF-v2
   ```


### References

For references to the relevant user documentation, see:
 - [Model in Image]({{< relref "/userguide/managing-domains/model-in-image/_index.md" >}}) user documentation
 - [Oracle WebLogic Server Deploy Tooling](https://github.com/oracle/weblogic-deploy-tooling)
 - [Oracle WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool)
