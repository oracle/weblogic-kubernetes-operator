docker rmi -f $(docker images | grep "apache" | awk '{print $3}')
docker rmi -f $(docker images | grep "voyager" | awk '{print $3}')
docker rmi -f $(docker images | grep "prom" | awk '{print $3}')
docker rmi -f $(docker images | grep "grafana" | awk '{print $3}')
docker rmi -f $(docker images | grep "webhook" | awk '{print $3}')
docker rmi -f $(docker images | grep "voyager" | awk '{print $3}')
docker rmi -f $(docker images | grep "operator" | awk '{print $3}')
docker rmi -f $(docker images | grep "domain" | awk '{print $3}')
docker rmi -f $(docker images | grep "weblogic" | awk '{print $3}')

docker rmi -f oracle/weblogic:12.2.1.3-developer
docker rmi -f wlsldi-v2.docker.oraclecorp.com/weblogic:19.1.0.0
docker rmi -f wlsldi-v2.docker.oraclecorp.com/weblogic-for-rc2:12.2.1.3.0

#docker rmi -f $(docker images | grep "wlsldi-v2.docker" | awk '{print $1}')
#docker rmi -f $(docker images | grep "weblogic" | awk '{print $1}')

docker rmi -f $(docker images -q -f dangling=true)

docker images | grep "weblogic"
docker images | grep "none"

docker rmi -f $(docker images | grep "serverjre" | awk '{print $3}')
