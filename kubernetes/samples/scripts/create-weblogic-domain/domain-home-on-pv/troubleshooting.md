errors and how to resolve them

1. Message: "status on iteration 20 of 20
pod domain1-create-weblogic-sample-domain-job-4qwt2 status is Pending
The create domain job is not showing status completed after waiting 300 seconds."  
* The most likely cause is related to the value of persistentVolumeClaimName, defined in domain-home-on-pv/create-domain-inputs.yaml. Change the value to weblogic-sample-pvc.
* Verify that the operator is deployed. Use the command
```
kubectl  get all --all-namespaces  
```  
and look for lines similar to  
```
weblogic-operator1   pod/weblogic-operator-  
```  
If you do not find something similar in the output, the WebLogic Operator for Kubernetes may not have been installed completely. Review the operator installation instructions in
1. Message: "ERROR: Unable to create folder /shared/domains"
The most common cause is a poor choice of value for weblogicDomainStoragePath in the input file used when you executed  
```
create-pv-pvc.sh  
```
The you should delete the resources for your sample domain, correct the value in that file and rerun the commands to create the pv/pvc and the credential before you attampt to rerun  
```
create-domain.sh  
```
A correct values for weblogicDomainStoragePath will meet the following requirements  
* owned by the useer that started the operator
* exists
* is a directory
1. Message: "ERROR: The create domain job will not overwrite an existing domain. The domain folder /shared/domains/domain1 already exists"  
You will see this message if the directory domains/domain1 exists in the directory named as the value of weblogicDomainStoragePath in create-pv-pvc-inputs.yaml. For example, if the value of  weblogicDomainStoragePath is `/tmp/wls-op-4-k8s`, you would need to remove (or move) `/tmp/wls-op-4-k8s/domains/domain1`
