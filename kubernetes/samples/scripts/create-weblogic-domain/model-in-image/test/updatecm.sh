kubectl delete configmap wdt-config-map -n sample-domain1-ns
kubectl create configmap wdt-config-map -n sample-domain1-ns --from-file=cm

