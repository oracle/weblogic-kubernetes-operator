if [ "$#" != 3 ] ; then
  echo "usage: $0 domainName adminUser adminPwd"
  exit 1 
fi

SHARED_DIR=${PWD}/../pv/shared

./build.sh $@

echo "copy the genereted domain_home from image to a host folder"
docker run --rm -u root -v $SHARED_DIR:/shared \
  -v $PWD/scripts:/tmp $1-image   /tmp/cp-domain.sh $1

echo "remove the temporary image $1-image"
docker rmi $1-image
