if [ "$#" != 3 ] ; then
  echo "usage: $0 domainName adminUser adminPwd"
  exit 1 
fi

imageName=$1-image
domainRoot=/shared
domainHome=$domainRoot/domains/$1

echo "build image $imageName with domainHome $domainHome"
docker build \
  --build-arg ARG_DOMAIN_NAME=$1  --build-arg DOMAIN_ROOT=$domainRoot --build-arg ARG_DOMAIN_HOME=$domainHome \
  --build-arg ADMIN_USER=$2 --build-arg ADMIN_PWD=$3  \
  $PWD --force-rm -t $imageName

echo "copy the genereted domain_home from image to a host folder"
pvDir=${PWD}/../pv/shared  # host path of the pv folder
pvMountROOT=/pv   # mounted path of the pv folder in the container

docker run --rm -u root -v $pvDir:$pvMountROOT \
  -e "DOMAIN_HOME=$domainHome" -e "DOMAIN_NAME=$1" -e "PV_ROOT=$pvMountROOT" \
  $imageName  /tmp/cp-domain.sh

#echo "remove the temporary image $imageName"
#docker rmi $imageName

