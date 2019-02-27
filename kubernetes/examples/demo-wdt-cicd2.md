# scenario:
#
#  want each version to create a new rolling restartable image that can change
#  the app binaries and the weblogic configuration (e.g. deploy new apps), but not
#  the base weblogic image
#
#  1) create a base image for this domain that has a 'seed' domain home that just has the
#     domain secret and the admin server by starting from the base weblogic image then using
#     WDT createDomain to create the domain home
#
# 2) each time a version of the app needs to be made, create a new image by:
#    a) start from the base image for this domain
#    b) copy in the domain model and apps for that version
#    c) use WDT updateDomain to update the domain home

# pre-requisites:
# follow the quick start to:
# - create an operator in the sample-weblogic-operator-ns namespace
# - install traefik
# - create the sample-domains1-ns namespace
# - register that namespace with the operator and traefik

# step 1 - create a secret containing the WLS admin credentials, create the base domain definition & image, create the ingress for the domain
kubectl create secret generic -n sample-domain1-ns domain1-uid-weblogic-credentials \
  --from-literal=username=weblogic --from-literal=password=welcome1
kubectl label secret -n sample-domain1-ns domain1-uid-weblogic-credentials \
  weblogic.domainUID=domain1-uid weblogic.domainName=domain1
rm -r domain1-def
cp -r cicd/domain-definitions/base domain1-def
cp cicd/domain-home-creators/base/Dockerfile2 domain1-def/Dockerfile
cp weblogic-deploy.zip domain1-def
ENCODED_ADMIN_USERNAME=`kubectl get secret -n sample-domain1-ns domain1-uid-weblogic-credentials -o jsonpath='{.data.username}'`
ENCODED_ADMIN_PASSWORD=`kubectl get secret -n sample-domain1-ns domain1-uid-weblogic-credentials -o jsonpath='{.data.password}'`
docker build --build-arg ENCODED_ADMIN_USERNAME=${ENCODED_ADMIN_USERNAME} --build-arg ENCODED_ADMIN_PASSWORD=${ENCODED_ADMIN_PASSWORD} --force-rm=true -t domain1:base domain1-def
cp load-balancers/domain-traefik.yaml domain1-lb.yaml
kubectl apply -f domain1-lb.yaml

# step 2 - create the v1 domain definition & image, create the domain resource and wait for the servers to start
# note: v1 has testwebapp1-v1 (initial app)
rm -r domain1-def
cp -r cicd/domain-definitions/v1 domain1-def
cp cicd/domain-home-creators/derived/Dockerfile2 domain1-def/Dockerfile
cp weblogic-deploy.zip domain1-def
docker build --force-rm=true -t domain1:v1 domain1-def
helm install cicd/domain1 --name domain1 --namespace sample-domain1-ns --set Version=v1
kubectl get po -n sample-domain1-ns && curl -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp1/
  (until admin server and managed server are running)

# step 3 - create the v2 domain definition & image, create the domain resource and wait for the servers to roll
# note: v2 has testwebapp1-v2 (new version of of the first app)
rm -r domain1-def
cp -r cicd/domain-definitions/v2 domain1-def
cp cicd/domain-home-creators/derived/Dockerfile2 domain1-def/Dockerfile
cp weblogic-deploy.zip domain1-def
docker build --force-rm=true -t domain1:v2 domain1-def
helm upgrade domain1 cicd/domain1 --reuse-values --set Version=v2
kubectl get po -n sample-domain1-ns && curl -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp1/
  (until admin server and managed server are restarted)

# step 4 - create the v3 domain definition & image, create the domain resource and wait for the servers to roll
# note: v3 has testwebapp1-v2 & testwabapp2-v1 (same version of the first app, adds the first version of the second app)
rm -r domain1-def
cp -r cicd/domain-definitions/v3 domain1-def
cp cicd/domain-home-creators/derived/Dockerfile2 domain1-def/Dockerfile
cp weblogic-deploy.zip domain1-def
docker build --force-rm=true -t domain1:v3 domain1-def
helm upgrade domain1 cicd/domain1 --reuse-values --set Version=v3
kubectl get po -n sample-domain1-ns && curl -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp1/ && curl -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp2/
  (until admin server and managed server are running)

# step 5 - create the v4 domain definition & image, create the domain resource and wait for the servers to roll
# note: v4 only testwebapp2-v2 (removes the first app, new version of the second app)
rm -r domain1-def
cp -r cicd/domain-definitions/v4 domain1-def
cp cicd/domain-home-creators/derived/Dockerfile2 domain1-def/Dockerfile
cp weblogic-deploy.zip domain1-def
docker build --force-rm=true -t domain1:v4 domain1-def
helm upgrade domain1 cicd/domain1 --reuse-values --set Version=v4
kubectl get po -n sample-domain1-ns && curl -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp1/ && curl -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp2/
  (until admin server and managed server are running)

# step 6 - teardown
kubectl delete -f domain1-lb.yaml
helm delete --purge domain1
kubectl get po -n sample-domain1-ns && kubectl get svc -n sample-domain1-ns
  (until they all go away)
docker rmi domain1:v4
docker rmi domain1:v3
docker rmi domain1:v2
docker rmi domain1:v1
docker rmi domain1:base
kubectl delete secret -n sample-domain1-ns domain1-uid-weblogic-credentials
rm domain1-lb.yaml
rm -r domain1-def
