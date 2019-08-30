# Model in image sample

This sample demonstrates how to specify a domain model to use in an image for the operator. This allows the WebLogic domain to be created from the model in the image automatically.  The domain model format is described in the [WebLogic Deloy Tool](https://github.com/oracle/weblogic-deploy-tooling).

## High level steps in creating a domain model in image

1. Obtain a base WebLogic image either from [Docker Hub](https://github.com/oracle/docker-images/tree/master/OracleWebLogic) or create one using [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool)

2. In the image, create the following structures and place the `WebLogic Deploy Tool``` artifacts

| directory | contents |
|-----------|----------|
| /u01/wdt/models| domain model yaml files |
|                | model variable files |
|                | deployment archive |


4. Optionally create a config map

Optionally, you can create a config map containing additional models and variable properties files. They will be used as input during domain creation. For example,

In a directory ```/home/acmeuser/wdtoverride```, place additional models and variables files

```kubectl create configmap wdt-config-map -n sample-domain1-ns --from-file /home/acmeuser/wdtoverride```


5. Optionally create an encryption secret

```WebLogic Deploy Tool``` allows you to encrypt sensitive information in the model.  If you model is using this feature, you need to create a secret to store the encryption passphrase.  The passphrase will be used for domain creation.  The secret can named anything but it must have a key ```wdtpassword```

```kubectl -n sample-domain1-ns create secret generic sample-domain1-wdt-secret --from-literal=wdtpassword=welcome1```


6. Update the domain resource yaml file

If you have addtional models or encryption secret, you can add the following keys to the domain resource yaml file.

```
wdtConfigMap : wdt-config-map
wdtConfigMapSecret : sample-domain1-wdt-secret
```

Specify one of the domain type if it is not WLS

```
  serverPod:
    env:
    - name: WDT_DOMAIN_TYPE
      value: "WLS|JRF|RestrictedJRF"
```

## Naming convention of model files

During domain creation, we follow this alogrithm.  The model files are combined to form a list first from the image ```/u01/model_home/models``` and then followed by those in the config map. You can name the file using the convention ```filename.##.yaml```, where ```##``` is a numeric number.  

For example, in the domain ```/u01/model_home/models``` 

```
base-model.10.yaml
jdbc.20.yaml
```

In the config map,

```
jdbc-dev-urlprops.10.yaml
```

The combined model files list passing to the ```WebLogic Deploy Tool``` as

```base_model.10.yaml,jdbc.20.yaml,jdbc-dev-urlprops.10.yaml```

Similarly, the properties will use the same sorting algorithm, but they are appended together to form a single variable properties file.  The resultant properties file will be used during domain creation.


## Using this example

Prerequsite:

1. Create namespace for the sample 
```kubectl create namespace sample-domain1-ns```
2. Enable WebLogic Operator to watch for the namespace, from the root of the weblogic-kubernetes-operator directory

```helm upgrade \
  --reuse-values \
  --set "domainNamespaces={sample-domain1-ns}" \
  --wait \
  sample-weblogic-operator \
  kubernetes/charts/weblogic-operator
```

1. Create a temporary directory with 10g space
2. Go to edelivery.oracle.com
    - search for Oracle JRE
    - click on JRE 1.8.0_221 to add it to the shopping cart
    - click on V982783-01.zip to download the zip files 
    - search for Oracle WebLogic Server again
    - click on Oracle WebLogic Server 12.2.1.3.0 (Oracle WebLogic Server Enterprise Edition)
    - click on Checkout
    - click continue and accept license agreement 
    - click on V886243-01.zip to download the zip files 
    - click on Oracle Fusion Middleware 12c Infrastructure 12.2.1.3.0)
    - click on Checkout
    - click continue and accept license agreement 
    - click on V886246-01.zip to download the zip files 

    (Oracle Fusion Middleware 12c (12.2.1.3.0) WebLogic Server and Coherence, 800.1 MB)
    (Oracle Fusion Middleware 12c (12.2.1.3.0) Infrastructure, 1.5 GB)
    (Oracle SERVER JRE 1.8.0.221 media upload for Linux x86-64, 52.5 MB)
3. Copy V982783-01.zip and V886243-01.zip or V886246-01.zip to the temporary directory
4. Run ./build.sh <full path to the temporary directory in step 1> <oracle support id capable to download patches> <password for the support id> <image type: WLS|FMW>

5. Wait for it to finish


6. Run ./k8sdomain.sh
7. At the end, you will see the message "Getting pod status - ctrl-c when all is running and ready to exit"
8. Once all the pods are up, you can ctrl-c to exit the build script.


Optionally, you can install nginx to test the sample application

9. Install the nginx ingress controller in your environment For example:
```
helm install --name acmecontroller stable/nginx-ingress \
--namespace sample-domain1-ns \
--set controller.name=acme \
--set defaultBackend.enabled=true \
--set defaultBackend.name=acmedefaultbackend \
--set rbac.create=true
```
10. Install the ingress rule ```kubectl apply -f nginx.yaml```
11. kubectl --namespace sample-domain1-ns get services -o wide -w acmecontroller-nginx-ingress-acme
12. Note the ```EXTERNAL-IP```
13. Run curl -kL http://```EXTERNAL-IP```/sample_war/index.jsp, you should see something like:
```Hello World, you have reached server managed-server1```


## Running the example for JRF domain

JRF domain requires an infrastructure database.  This example shows how to setup a sample database,
modify the WebLogic Deploy Tool Model to provide database connection information, and steps to create the infrastructure schema.


1. kubectl apply -f db-slim.yaml
2. Copy ```image/model1.yaml.jrf``` into ```image/model1.yaml```
3. Copy ```domain.yaml.jrf``` into ```domain.yaml```
4. Repeat the above steps 1-5, make sure use the image type ```FMW```  in step 4
5. kubectl run rcu -i --tty  --image model-in-image:x0 --restart=Never -- sh
6. Create the rcu schema

```
/u01/oracle/oracle_common/bin/rcu \
  -silent \
  -createRepository \
  -databaseType ORACLE \
  -connectString  oracle-db.sample-domain1-ns.svc.cluster.local:1521/pdb1.k8s \
  -dbUser sys \
  -dbRole sysdba \
  -useSamePasswordForAllSchemaUsers true \
  -selectDependentsForComponents true \
  -schemaPrefix FMW1 \
  -component MDS \
  -component IAU \
  -component IAU_APPEND \
  -component IAU_VIEWER \
  -component OPSS  \
  -component WLS  \
  -component STB <<EOF
Oradoc_db1
welcome1
EOF
```
7. ctrl-d to exit the terminal
8. kubectl delete pod rcu
9. Repeat step 6-8










