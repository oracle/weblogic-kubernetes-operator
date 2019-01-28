# step 1 - create a domain definition
cp -r domain-definitions/wlst/simple mydomain-def
find mydomain-def -type f
edit mydomain-def/model/model.py

# step 2 - create the domain home
cp domain-home-creators/wlst-in-image/Dockerfile mydomain-def
edit mydomain-def/Dockerfile
docker build --force-rm=true -t mydomain mydomain-def

# step 3 - create a secret containing the WLS admin credentials
kubectl create secret generic -n sample-domain1-ns mydomain-uid-weblogic-credentials \
  --from-literal=username=weblogic --from-literal=password=welcome1
kubectl label secret -n sample-domain1-ns mydomain-uid-weblogic-credentials \
  weblogic.domainUID=domain1-uid weblogic.domainName=mydomain

# step 4 - create the domain resource and wait for the servers to start
cp domain-resources/domain-and-logs-in-image.yaml mydomain.yaml
edit mydomain.yaml
kubectl apply -f mydomain.yaml
kubectl get po -n sample-domain1-ns
  (until admin server and managed server are running)
curl -v http://${HOSTNAME}:30701/weblogic/
http://localhost:30701/console

# step 5 - create the ingress and verify the load balancer is routing to the managed server
cp load-balancers/domain-traefik.yaml mydomain-lb.yaml
edit mydomain-lb.yaml
kubectl apply -f mydomain-lb.yaml
curl -v -H 'host: mydomain.org' http://${HOSTNAME}:30305/weblogic/
curl -v -H 'host: mydomain.org' http://${HOSTNAME}:30305/testwebapp/

# step 6 - teardown
kubectl delete -f mydomain-lb.yaml
kubectl delete -f mydomain.yaml
kubectl get po -n sample-domain1-ns && kubectl get svc -n sample-domain1-ns (until they all go away)
kubectl delete secret -n sample-domain1-ns mydomain-uid-weblogic-credentials
docker rmi mydomain
rm mydomain-lb.yaml
rm mydomain.yaml
rm -r mydomain-def
