---
title: "Run a database"
date: 2019-02-23T16:43:10-05:00
weight: 3

---

#### Run the Oracle database in Kubernetes

If you wish to run the Oracle database inside your Kubernetes cluster, in order to place
your state store, leasing tables, and such, in that database, then you can use this
sample to install the database.

You must configure your database to store its DB files
on persistent storage.  Refer to your cloud vendor's documentation for details of
available storage providers and how to create a persistent volume and attach it to a pod.

First create a namespace for the database:

```
kubectl create namespace database-namespace
```

Next, create a file called `database.yml` with the following content.  Make sure you update the
password field with your chosen administrator password for the database.

```
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: database
  namespace: database-namespace
  labels:
    app: database
    version: 12.1.0.2
spec:
  replicas: 1
  selector:
    matchLabels:
      app: database
      version: 12.1.0.2
  template:
    metadata:
      name: database
      labels:
        app: database
        version: 12.1.0.2
    spec:
      volumes:
      - name: dshm
        emptyDir:
          medium: Memory
      # add your volume mount for your persistent storage here
      containers:
      - name: database
        command:
        - /home/oracle/setup/dockerInit.sh
        image: container-registry.oracle.com/database/enterprise:12.1.0.2
        imagePullPolicy: IfNotPresent
        resources:
          requests:
            memory: 10Gi
        ports:
        - containerPort: 1521
          hostPort: 1521
        volumeMounts:
          - mountPath: /dev/shm
            name: dshm
          # add your persistent storage for DB files here
        env:
          - name: DB_SID
            value: OraDoc
          - name: DB_PDB
            value: OraPdb
          - name: DB_PASSWD
            value: *password*
          - name: DB_DOMAIN
            value: my.domain.com
          - name: DB_BUNDLE
            value: basic
          - name: DB_MEMORY
            value: 8g
      imagePullSecrets:
      - name: regsecret
---
apiVersion: v1
kind: Service
metadata:
  name: database
  namespace: database-namespace
spec:
  selector:
    app: database
    version: 12.1.0.2
  ports:
  - protocol: TCP
    port: 1521
    targetPort: 1521
```

If you have not previously done so, you will need to go to the [Oracle Container Registry](https://container-registry.oracle.com)
and accept the license for the [Oracle database image](https://container-registry.oracle.com/pls/apex/f?p=113:4:11538835301670).

Create a Docker registry secret so that Kubernetes can pull the database image:

```
kubectl create secret docker-registry regsecret \
        --docker-server=container-registry.oracle.com \
        --docker-username=your.email@some.com \
        --docker-password=your-password \
        --docker-email=your.email@some.com \
        -n database-namespace

```

Now use the following command to install the database:

```
kubectl apply -f database.yml
```

This will start up the database and expose it in the cluster at the following address:

```
database.database-namespace.svc.cluster.local:1521
```
