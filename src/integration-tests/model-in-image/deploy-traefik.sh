# TBD doc/copyright

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$TESTDIR/../../.." > /dev/null 2>&1 ; pwd -P )"

set -eu
set -o pipefail

WORKDIR=${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}

TRAEFIK_NAME=${TRAEFIK_NAME:-traefik-operator}
TRAEFIK_NAMESPACE=${TRAEFIK_NAMESPACE:-${TRAEFIK_NAME}-ns}

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}

set +e

helm uninstall $DOMAIN_UID-ingress -n $DOMAIN_NAMESPACE
helm uninstall $TRAEFIK_NAME -n $TRAEFIK_NAMESPACE
kubectl create namespace $TRAEFIK_NAMESPACE
kubectl create namespace $DOMAIN_NAMESPACE

set -eu

cd ${SRCDIR}

# you only need to add the repo once, but we do it every time for simplicity
helm repo add stable https://kubernetes-charts.storage.googleapis.com/

helm install ${TRAEFIK_NAME} stable/traefik \
  --namespace $TRAEFIK_NAMESPACE \
  --values kubernetes/samples/charts/traefik/values.yaml \
  --set "kubernetes.namespaces={$TRAEFIK_NAMESPACE,$DOMAIN_NAMESPACE}" \
  --wait

# TBD replace with a yaml
helm install $DOMAIN_UID-ingress kubernetes/samples/charts/ingress-per-domain \
  --namespace $DOMAIN_NAMESPACE \
  --set wlsDomain.domainUID=$DOMAIN_UID \
  --set traefik.hostname=$DOMAIN_UID.org

cat << EOF > ${WORKDIR}/console-ingress.yaml

apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: ${DOMAIN_UID}-console-ingress
  namespace: ${DOMAIN_NAMESPACE}
  annotations:
    kubernetes.io/ingress.class: traefik
spec:
  rules:
  - host:
    http:
      paths:
      - path: /console
        backend:
          serviceName: ${DOMAIN_UID}-admin-server
          servicePort: 7001

EOF

kubectl delete -f ${WORKDIR}/console-ingress.yaml --ignore-not-found

kubectl apply -f ${WORKDIR}/console-ingress.yaml

echo "@@ Info: The WebLogic console should now be available at 'http://$(hostname).$(dnsdomainname):30305/console' (weblogic/welcome1)."
