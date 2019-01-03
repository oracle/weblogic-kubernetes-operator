export WLS_BASE_IMAGE=store/oracle/weblogic:19.1.0.0
export PRJ_ROOT=../../

function pullImages() {
  echo "pull docker images"
  docker pull oracle/weblogic-kubernetes-operator:2.0-rc1
  docker tag oracle/weblogic-kubernetes-operator:2.0-rc1 weblogic-kubernetes-operator:2.0
  docker pull traefik:latest
  # TODO: until we has a public site for the image
  docker pull wlsldi-v2.docker.oraclecorp.com/weblogic:19.1.0.0
  docker tag wlsldi-v2.docker.oraclecorp.com/weblogic:19.1.0.0 $WLS_BASE_IMAGE
}

function createOpt() {
  echo "create namespace test1 to run wls domains"
  kubectl create namespace test1

  echo "install Treafik operator to namespace traefik"
  helm install stable/traefik \
    --name traefik-operator \
    --namespace traefik \
    --values $PRJ_ROOT/kubernetes/samples/charts/traefik/values.yaml  \
    --wait

  echo "install WebLogic operator to namespace weblogic-operator1"
  kubectl create namespace weblogic-operator1
  kubectl create serviceaccount -n weblogic-operator1 sample-weblogic-operator-sa

  helm install $PRJ_ROOT/kubernetes/charts/weblogic-operator \
    --name sample-weblogic-operator \
    --namespace weblogic-operator1 \
    --set serviceAccount=sample-weblogic-operator-sa \
    --set "domainNamespaces={default,test1}" \
    --wait
}

function delOpt() {
  echo "delete operators"
  helm delete --purge sample-weblogic-operator
  kubectl delete namespace weblogic-operator1

  helm delete --purge traefik-operator
  kubectl delete namespace traefik
  kubectl delete namespace test1
}

function setupPV() {
  mkdir -p pv/logs
  mkdir -p pv/shared
  chmod -R 777 pv/*

  sed -i 's@%PATH%@'"$PWD"/pv/logs'@' logPV/pv.yaml
  sed -i 's@%PATH%@'"$PWD"/pv/shared'@' domainHomePV/pv.yaml 
}

function createDomain1() {
  echo "create domain1"
  cd domain_builder/
  # create image 'domain1-image' with domainHome in the image
  ./build.sh domain1 weblogic welcome1
  cd ..

  kubectl -n default create secret generic domain1-weblogic-credentials \
    --from-literal=username=weblogic \
    --from-literal=password=welcome1

  kubectl create -f domain1.yaml

  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain1-ingress \
    --set wlsDomain.namespace=default \
    --set wlsDomain.domainUID=domain1 \
    --set traefik.hostname=domain1.org
}

function createDomain2() {
  echo "create domain2"
  cd domain_builder/
  # create image 'domain2-image' with domainHome in the image
  ./build.sh domain2 weblogic welcome2
  cd ..

  kubectl -n test1 create secret generic domain2-weblogic-credentials \
    --from-literal=username=weblogic \
    --from-literal=password=welcome2

  kubectl create -f logPV/pv.yaml
  kubectl create -f logPV/pvc.yaml
  kubectl create -f domain2.yaml

  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain2-ingress \
    --set wlsDomain.namespace=test1 \
    --set wlsDomain.domainUID=domain2 \
    --set traefik.hostname=domain2.org
}

function createDomain3() {
  echo "create domain3"
  # generate the domain3 configuration to a host folder
  cd domain_builder/
  ./generate.sh domain3 weblogic welcome3
  cd ..

  kubectl -n test1 create secret generic domain3-weblogic-credentials \
    --from-literal=username=weblogic \
    --from-literal=password=welcome3

  kubectl create -f domainHomePV/pv.yaml
  kubectl create -f domainHomePV/pvc.yaml
  kubectl create -f domain3.yaml

  helm install $PRJ_ROOT/kubernetes/samples/charts/ingress-per-domain \
    --name domain3-ingress \
    --set wlsDomain.namespace=test1 \
    --set wlsDomain.domainUID=domain3 \
    --set traefik.hostname=domain3.org
}

function createDomains() {
  createDomain1
  createDomain2
  createDomain3

}

function delDomain1() {
  helm delete --purge domain1-ingress
  kubectl delete -f domain1.yaml
  kubectl delete secret domain1-weblogic-credentials
}

function delDomain2() {
  helm delete --purge domain2-ingress
  kubectl delete -f domain2.yaml
  kubectl delete -f logPV/pvc.yaml
  kubectl delete -f logPV/pv.yaml
  kubectl -n test1 delete secret domain2-weblogic-credentials
}

function delDomain3() {
  helm delete --purge domain3-ingress
  kubectl delete -f domain3.yaml
  kubectl delete -f domainHomePV/pvc.yaml
  kubectl delete -f domainHomePV/pv.yaml
  kubectl -n test1 delete secret domain3-weblogic-credentials
}

function delDomains() {
  delDomain1
  delDomain2
  delDomain3
}

function usage() {
  echo "usage: $0 <cmd>"
  echo "  image cmd: pullImages"
  echo "  This is to pull required images."
  echo
  echo "  PV cmd: setupPV"
  echo "  This is to create PV folders and set right host path in the pv yamls."
  echo
  echo "  operator cmd: createOpt | delOpt"
  echo "  These are to create or delete wls operator and Traefik operator."
  echo
  echo "  domains cmd: createDomains | delDomains"
  echo "  These are to create or delete all the demo domains."
  echo
  echo "  one domain cmd: createDomain1 | createDomain2 | createDomain3 | delDomain1 | delDomain2 | delDomain3"
  echo "  These are to create or delete one indivisual domain."
  echo
  exit 1
}

function main() {
  if [ "$#" != 1 ] ; then
    usage
  fi
  $1
}

main $@
