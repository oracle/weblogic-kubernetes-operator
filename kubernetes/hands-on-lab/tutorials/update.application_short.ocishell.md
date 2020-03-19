# Oracle WebLogic Server Kubernetes Operator Tutorial #

### Application lifecycle management ###

This tutorial creates a Docker image with a WebLogic domain inside the image deployment. This means that all the artefacts, including the deployed applications and domain-related files, are stored within the image. This results in a new WebLogic Docker image every time the application is modified. In this, widely adopted approach, the image is the packaging unit instead of the Web/Enterprise Application Archive (*war*, *ear*).

For the purpose of this lab, we created another image that contains a domain and an updated version of the application (green title on the main page). This image is available at `iad.ocir.io/weblogick8s/weblogic-operator-tutorial-store:2.0`.

#### Modify the domain.yaml ####

Edit  your domain resource definition (*domain.yaml*) and modify the image location. The modified line with image should appear as follows:
```yaml
  image: "iad.ocir.io/weblogick8s/weblogic-operator-tutorial-store:2.0"
```

Don't forget the leading spaces to keep the proper indentation.

Apply the domain resource changes:
```bash
kubectl apply -f ~/domain.yaml
```
You can immediately check the status of your servers/pods. (You should observe the restart of the WLS domain):
```bash
$ kubectl get po -n sample-domain1-ns
NAME                             READY     STATUS        RESTARTS   AGE
sample-domain1-admin-server      1/1       Terminating   0          22m
sample-domain1-managed-server1   1/1       Running       0          20m
sample-domain1-managed-server2   1/1       Running       0          21m
sample-domain1-managed-server3   1/1       Running       0          21m
```
The operator now performs a rolling server restart, one server at a time. The first one is the *Admin* server, then the *Managed* servers.

During the rolling restart, check your web application periodically. If the responding server is already restarted, then you should see the change (green fonts) you made to the application. If the server is not yet restarted, then it is still serving the older version of the application.

`http://EXTERNAL-IP/opdemo/?dsname=testDatasource`

![](../images/update.application/004.check.changes.png)
