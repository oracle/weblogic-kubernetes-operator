1.  The image subdirectory contains script and readme for creating an image suitable for model in image testing, you should create a model in image first.

2.  The updatecm.sh  will create a configmap named wdt-config-map from the current subdirectory cm using namespace sample-domain1-ns as needed.
    This is optional.

3.  Change to sample domain resource yaml to fit your en0ironment and then apply it to setup the weblogic operator domain
    
   Uncomment domain resource wdt config map & secrets if using wdt-config-map/secrets
    #wdtConfigMap : wdt-config-map
    #wdtConfigMapSecret : simple-domain1-wdt-secret
   Otherwise, comment it outl

   GENERAL ISSUE: wdtConfigMapSecret perhaps should be hidden or at least from sample:
                  - we want to encourage use of k8s secrets instead
                  - we want to discourage the potential for passwords to end up in plain-text (in the original unencrypted WDT)

   GENERAL ISSUE: maybe wdtConfigMap should not be mounted as 'optional'
                  instead, in operator, just don't define the stanza if the stanza is not in the domain resource

   mkdir $WORKDIR/domain
   cd $WORKDIR/domain
   cp $MIISAMPLEDIR/test/domain.yaml  $WORKDIR/domain

   Make sure operator is deployed
    helm ls

   Check namespaces operator is monitoring includes the domain resource's namespace (obtain name via 'helm ls')
    helm get values RELEASENAME

   Make sure no domains are already running using the 'domain1' domain_uid
    kubectl -n sample-domain1-ns get domains --show-labels=true
    (if so, delete it using the ./kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh script)

   Deploy credentials secret needed by domain resource:

   $OPDIR/kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh \
     -u weblogic -p welcome1 -n sample-domain1-ns -d domain1
     (this will create a secret named 'domain1-weblogic-credentials' with a weblogic.domainUID label of 'domain1'
   
   Deploy domain
    kubectl create -f domain.yaml 
      
