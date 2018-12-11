> **WARNING** This documentation is for version 1.0 of the operator.  To view documenation for the current release, [please click here](/site).

# Running the Oracle Database in Kubernetes

**PLEASE NOTE**:  This page is a work in progress.  We will update this with either better details about how to put the data files onto a persistent volume, or a pointer to the official Oracle Database Kubernetes pages, or both.

```

apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: database
  namespace: domain2
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
  namespace: domain2
spec:
  selector:
    app: database
    version: 12.1.0.2
  ports:
  - protocol: TCP
    port: 1521
    targetPort: 1521
```
