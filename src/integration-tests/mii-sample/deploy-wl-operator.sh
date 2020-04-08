# TBD doc/copyright

TESTDIR="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
SRCDIR="$( cd "$TESTDIR/../../.." > /dev/null 2>&1 ; pwd -P )"

set -u

DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-${DOMAIN_UID}-ns}

OPER_NAME=${OPER_NAME:-sample-weblogic-operator}
OPER_NAMESPACE=${OPER_NAMESPACE:-${OPER_NAME}-ns}
OPER_SA=${OPER_SA:-${OPER_NAME}-sa}

OPER_IMAGE_TAG=${OPER_IMAGE_TAG:-test}
OPER_IMAGE_NAME=${OPER_IMAGE_NAME:-weblogic-kubernetes-operator}
OPER_IMAGE=${OPER_IMAGE_NAME}:${OPER_IMAGE_TAG}

set +e

kubectl create namespace $DOMAIN_NAMESPACE
kubectl create namespace $OPER_NAMESPACE
kubectl create serviceaccount -n $OPER_NAMESPACE $OPER_SA

helm uninstall $OPER_NAME -n $OPER_NAMESPACE

set -eu
cd ${SRCDIR}

helm install $OPER_NAME kubernetes/charts/weblogic-operator \
  --namespace $OPER_NAMESPACE \
  --set       image=$OPER_IMAGE \
  --set       serviceAccount=$OPER_SA \
  --set       "domainNamespaces={$DOMAIN_NAMESPACE}" \
  --set       "javaLoggingLevel=FINEST" \
  --wait

kubectl get deployments -n $OPER_NAMESPACE

echo "log command: kubectl logs -n $OPER_NAMESPACE -c weblogic-operator deployments/weblogic-operator"
