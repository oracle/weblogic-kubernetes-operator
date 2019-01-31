# step 1 - create a pv for the domain namespace
mkdir -p /scratch/k8s-dir/storage/domain-namespaces/sample-domain1-ns
cp persistent-volumes/hostpath/shared-pv.yaml sample-domain1-ns-pv.yaml
edit sample-domain1-ns-pv.yaml
kubectl apply -f sample-domain1-ns-pv.yaml

# step 2 - create a domain definition
cp -r domain-definitions/wdt/simple domain1-def
find domain1-def -type f
edit domain1-def/model/model.yaml

# step 3 - create a secret containing the WLS admin credentials
kubectl create secret generic -n sample-domain1-ns domain1-uid-weblogic-credentials \
  --from-literal=username=weblogic --from-literal=password=welcome1
kubectl label secret -n sample-domain1-ns domain1-uid-weblogic-credentials \
  weblogic.domainUID=domain1-uid weblogic.domainName=domain1

# step 4 - create the domain home
cp weblogic-deploy.zip domain1-def
cp domain-home-creators/wdt-on-shared-pv/create-pod.yaml domain1-create-pod.yaml
edit domain1-create-pod.yaml
kubectl apply -f domain1-create-pod.yaml
kubectl logs -n sample-domain1-ns domain1-uid-create-pod
jar -c domain1-def | kubectl exec -i -n sample-domain1-ns domain1-uid-create-pod -- /bin/bash -c 'cd /u01 && jar -x'
kubectl get po -n sample-domain1-ns domain1-uid-create-pod
  (until Completed)
cat /scratch/k8s-dir/storage/domain-namespaces/sample-domain1-ns/domains/domain1-uid/config/config.xml
kubectl delete -f domain1-create-pod.yaml
rm domain1-create-pod.yaml

# step 5 - create the domain resource and wait for the servers to start
cp domain-resources/domain-and-logs-on-shared-pv.yaml domain1.yaml
edit domain1.yaml
kubectl apply -f domain1.yaml
kubectl get po -n sample-domain1-ns
  (until admin server and managed server are running)
curl -v -H 'host: domain1.org' http://${HOSTNAME}:30701/weblogic/
http://localhost:30701/console

# step 6 - create the ingress and verify the load balancer is routing to the managed server
cp load-balancers/domain-traefik.yaml domain1-lb.yaml
edit domain1-lb.yaml
kubectl apply -f domain1-lb.yaml
curl -v -H 'host: domain1.org' http://${HOSTNAME}:30305/weblogic/
curl -v -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp/

# step 7 - teardown
kubectl delete -f domain1-lb.yaml
kubectl delete -f domain1.yaml
kubectl get po -n sample-domain1-ns && kubectl get svc -n sample-domain1-ns
  (until they all go away)
kubectl delete -f sample-domain1-ns-pv.yaml
rm domain1-lb.yaml
rm domain1.yaml
rm -r domain1-def
kubectl delete secret -n sample-domain1-ns domain1-uid-weblogic-credentials
rm sample-domain1-ns-pv.yaml
rm -r /scratch/k8s-dir/storage/domain-namespaces/sample-domain1-ns
