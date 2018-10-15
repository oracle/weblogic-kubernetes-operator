
kubectl delete -f samples/host-routing.yaml
kubectl delete -f samples/path-routing.yaml
helm delete --purge traefik-operator
