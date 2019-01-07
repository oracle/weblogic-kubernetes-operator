if [ "$#" != 3 ] ; then
  echo "usage: $0 domainName adminUser adminPwd"
  exit 1 
fi

MYDIR="$(dirname "$(readlink -f "$0")")"

docker run --rm  \
  -v $MYDIR/scripts:/scripts \
  -v $PV_ROOT/shared:/u01/oracle/user-projects \
  -e "DOMAIN_HOME=/u01/oracle/user-projects/domains/$1" \
  -e "DOMAIN_NAME=$1" \
  -e "ADMIN_USER=$2" \
  -e "ADMIN_PWD=$3" \
  store/oracle/weblogic:19.1.0.0  /scripts/create-domain.sh
