
if [ "$#" != 3 ] ; then
  echo "usage: $0 domainName adminUser adminPwd"
  exit 1 
fi

imageName=$1-image
echo "build image $imageName"
docker build --build-arg ARG_DOMAIN_NAME=$1  --build-arg ADMIN_USER=$2 \
 --build-arg ADMIN_PWD=$3 $PWD --force-rm -t $imageName
