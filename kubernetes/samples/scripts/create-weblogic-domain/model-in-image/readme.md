# Model in image sample

This sample demonstrates how to specify a domain model to use in an image for the operator. This allows the WebLogic domain to be created from the model in the image automatically.  The domain model format is described in the [WebLogic Deloy Tool](https://github.com/oracle/weblogic-deploy-tooling).

## Steps in creating a domain model in image

1. Obtain a base WebLogic image either from [Docker Hub](https://github.com/oracle/docker-images/tree/master/OracleWebLogic) or create one using [WebLogic Image Tool](https://github.com/oracle/weblogic-image-tool)

2. In the image, create the following structures and place the `WebLogic Deploy Tool``` artifacts

| directory | contents |
|-----------|----------|
| /u01/model_home/models| domain model yaml files |
| /u01/model_home/variables | model variable files |
| /u01/model_home/archives | deployment archive |


4. Optionally create a config map

Optionally, you can create a config map containing additional models and variable properties files. They will be used as input during domain creation. For example,

In a directory ```/home/acmeuser/wdtoverride```, place additional models and variables files

```kubectl create configmap wdt-config-map -n sample-domain1-ns --from-file /home/acmeuser/wdtoverride```


5. Optionally create an encryption secret

```WebLogic Deploy Tool``` allows you to encrypt sensitive information in the model.  If you model is using this feature, you need to create a secret to store the encryption passphrase.  The passphrase will be used for domain creation.  The secret can named anything but it must have a key ```wdtpassword```

```kubectl -n sample-domain1-ns create secret generic simple-domain1-wdt-secret --from-literal=wdtpassword=welcome1```


6. Update the domain resource yaml file

If you have addtional models or encryption secret, you can add the following keys to the domain resource yaml file.

```
wdtConfigMap : wdt-config-map
wdtConfigMapSecret : simple-domain1-wdt-secret
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

1. Create a temporary directory with 10g space
2. Go to edelivery.oracle.com
    - search for Oracle JRE
    - click on JRE 1.8.0_221 to add it to the shopping cart
    - search for Oracle WebLogic Server again
    - click on Oracle WebLogic Server 12.2.1.3.0 (Oracle WebLogic Server Enterprise Edition)
    - click on Checkout
    - click continue and accept license agreement 
    - click on V982783-01.zip and V886243-01.zip to download the zip files 
    (Oracle Fusion Middleware 12c (12.2.1.3.0) WebLogic Server and Coherence, 800.1 MB)
    (Oracle SERVER JRE 1.8.0.221 media upload for Linux x86-64, 52.5 MB)
3. Copy V982783-01.zip and V886243-01.zip to the temporary directory
4. Run ./build.sh <full path to the temporary directory in step 1> <oracle support id capable to download patches> <password for the support id>

5. Wait for it to finish
6. At the end, you will see the message "Getting pod status - ctrl-c when all is running and ready to exit"
7. Once all the pods are up, you can ctrl-c to exit the build script.
8. Run ```kubectl apply -f nginx.yaml```
9. Run ```kubectl cluster-info``` and make a note of the cluster ip address
10. Run curl -kL http://<cluster ip>/sample_war/index.jsp, you should see something like:
```Hello World, you have reached server managed-server1```














