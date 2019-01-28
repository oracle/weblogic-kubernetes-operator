# step 1 - create a domain definition

# unzip the WDT tool and the existing domain into a directory
cp -r domain-definitions/wdt/discover discover2
cd discover2
jar -xvf old-domain.zip
jar -xvf ../weblogic-deploy.zip
find weblogic-deploy -name "*.sh" | xargs chmod a+x

# run the WDT discover tool to create a WDT model and apps zip file from the existing domain
weblogic-deploy/bin/discoverDomain.sh -oracle_home $MW_HOME -domain_type WLS -domain_home old-domain -model_file model.yaml -archive_file apps.zip

# look at the output
jar -tvf apps.zip
edit model.yaml

# convert the output into a domain def

# put the model into the right location and customize it
mkdir -p domain1-def/model
diff model.yaml model.yaml.edited
edit model.yaml.edited
cp model.yaml.edited  domain1-def/model/model.yaml
#edit domain1-def/model/model.yaml:
#  REMOVE: node manager stuff
#  ADD:    admin username and password, domain name, admin server name, production mode enabled

# put the apps into the right location:
cd domain1-def
jar -xvf ../apps.zip
cd ..

find domain1-def -type f

# step 2 - create the domain home
cp ../domain-home-creators/wdt-in-image/Dockerfile domain1-def
edit domain1-def/Dockerfile
cp ../weblogic-deploy.zip domain1-def
docker build --force-rm=true -t domain1 domain1-def

# step 3 - create a secret containing the WLS admin credentials
kubectl create secret generic -n sample-domain1-ns domain1-uid-weblogic-credentials \
  --from-literal=username=weblogic --from-literal=password=welcome1
kubectl label secret -n sample-domain1-ns domain1-uid-weblogic-credentials \
  weblogic.domainUID=mydomain-uid weblogic.domainName=mydomain

# step 4 - create the domain resource and wait for the servers to start
cp ../domain-resources/domain-and-logs-in-image.yaml domain1.yaml
edit domain1.yaml
kubectl apply -f domain1.yaml
kubectl get po -n sample-domain1-ns
  until the servers starts
curl -v -H 'host: domain1.org' http://${HOSTNAME}:30701/weblogic/
http://localhost:30701/console

# step 6 - create the ingress and verify the load balancer is routing to the managed server
cp ../load-balancers/domain-traefik.yaml domain1-lb.yaml
edit domain1-lb.yaml
kubectl apply -f domain1-lb.yaml
curl -v -H 'host: domain1.org' http://${HOSTNAME}:30305/weblogic/
curl -v -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp/

# step 7 - teardown
kubectl delete -f domain1-lb.yaml
kubectl delete -f domain1.yaml
kubectl get po -n sample-domain1-ns && kubectl get svc -n sample-domain1-ns
  (until they all go away)
kubectl delete secret -n sample-domain1-ns domain1-uid-weblogic-credentials
docker rmi domain1
cd ..
rm -r discover2
