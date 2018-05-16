docker pull wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest
docker tag wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest store/oracle/weblogic:12.2.1.3
  
docker pull wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest
docker tag wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest store/oracle/serverjre:8

#docker rmi -f $(docker images -q -f dangling=true)
docker images --quiet --filter=dangling=true | xargs --no-run-if-empty docker rmi  -f