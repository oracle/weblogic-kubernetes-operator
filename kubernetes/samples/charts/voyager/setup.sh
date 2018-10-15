
#!/bin/bash
#  Copyright 2017, 2018, Oracle Corporation and/or affiliates.  All rights reserved.

echo "Install Utility onessl. The pre-consumption is that you have added ~/bin to your PATH env."
which onessl
if test $? != 0; then
  curl -fsSL -o onessl https://github.com/kubepack/onessl/releases/download/0.3.0/onessl-linux-amd64 \
    && chmod +x onessl \
    && mv onessl ~/bin
fi

echo "Add Appscode Chart Repository"
if test "$(helm search appscode/voyager | grep voyager |  wc -l)" = 0; then
  helm repo add appscode https://charts.appscode.com/stable/
  helm repo update
  helm search appscode/voyager
fi

# Install Voyager to K8S 1.9.x - 1.10.x
if test "$(kubectl get ns | grep voyager |  wc -l)" = 0; then
  kubectl create ns voyager
  helm install appscode/voyager --name voyager-operator --version 7.4.0 \
    --namespace voyager \
    --set cloudProvider=baremetal \
    --set apiserver.ca="$(onessl get kube-ca)" \
    --set apiserver.enableValidatingWebhook=true
fi

echo "wait until Voyager operator running"
max=20
count=0
while test $count -lt $max; do
  kubectl -n voyager get pod
  if test "$(kubectl -n voyager get pod | grep voyager | awk '{ print $2 }')" = 1/1; then
    break;
  fi
  count=`expr $count + 1`
  sleep 5
done

echo "Install Voyager Ingress"
if test "$(kubectl get ingresses.voyager.appscode.com | grep path-routing | wc -l)" = 0; then
  kubectl create -f samples/path-routing.yaml
fi
if test "$(kubectl get ingresses.voyager.appscode.com | grep host-routing | wc -l)" = 0; then
  kubectl create -f samples/host-routing.yaml
fi

# access host-routing states page via URL http://${HOSTNAME}:30315
echo "acess host-routing LB"
curl --silent -H 'host: domain1.org' http://${HOSTNAME}:30305/testwebapp/ | grep InetAddress.hostname
curl --silent -H 'host: domain2.org' http://${HOSTNAME}:30305/testwebapp/ | grep InetAddress.hostname

# access host-routing states page via URL http://${HOSTNAME}:30317
echo "access path-routing LB"
curl --silent http://${HOSTNAME}:30307/domain1/ | grep InetAddress.hostname
curl --silent http://${HOSTNAME}:30307/domain2/ | grep InetAddress.hostname

