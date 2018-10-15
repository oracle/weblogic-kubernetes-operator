#set -x
port=30301
echo "test domain1"
curl --silent -H 'host: domain1.org' http://${HOSTNAME}:${port}/testwebapp/ | grep InetAddress.hostname
curl --silent -H 'host: domain1.org' http://${HOSTNAME}:${port}/testwebapp/ | grep InetAddress.hostname
curl --silent -H 'host: domain1.org' http://${HOSTNAME}:${port}/testwebapp/ | grep InetAddress.hostname
curl --silent -H 'host: domain1.org' http://${HOSTNAME}:${port}/testwebapp/ | grep InetAddress.hostname
echo
echo "test domain2"
curl --silent -H 'host: domain2.org' http://${HOSTNAME}:${port}/testwebapp/ | grep InetAddress.hostname
curl --silent -H 'host: domain2.org' http://${HOSTNAME}:${port}/testwebapp/ | grep InetAddress.hostname
curl --silent -H 'host: domain2.org' http://${HOSTNAME}:${port}/testwebapp/ | grep InetAddress.hostname
curl --silent -H 'host: domain2.org' http://${HOSTNAME}:${port}/testwebapp/ | grep InetAddress.hostname
echo
echo "test traefik dashboard"
curl -H 'host: traefik.example.com' http://${HOSTNAME}:${port}/
curl -H 'host: traefik.example.com' http://${HOSTNAME}:${port}

