
echo "Install Traefik Operator"
helm install --name traefik-operator --namespace traefik --values values.yaml stable/traefik

echo "Wait until Traefik operator running"
max=20
count=0
while test $count -lt $max; do
  kubectl -n traefik get pod
  if test "$(kubectl -n traefik get pod | grep traefik | awk '{ print $2 }')" = 1/1; then
    break;
  fi
  count=`expr $count + 1`
  sleep 5
done

