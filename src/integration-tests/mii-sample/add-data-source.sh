set -eu

# Test script for adding a datasource.
#
# This is an updated version of the same script that's found in the hugo user
# guide documentation for MII runtime updates. It may eventually become part of
# of the sample.
#
# NOTE: Confirm it still matches the doc vesion of the script before checking
#       into the sample.
#

# assume MIISAMPLEDIR references the 'kubernetes/samples/scripts/create-weblogic-domain/model-in-image'
# directory within the operator source

RCUDB_NAMESPACE=${RCUDB_NAMESPACE:-default}
DOMAIN_UID=${DOMAIN_UID:-sample-domain1}
DOMAIN_NAMESPACE=${DOMAIN_NAMESPACE:-sample-domain1-ns}
WDTCONFIGMAPDIR=${WDTCONFIGMAPDIR:-${WORKDIR:-/tmp/$USER/model-in-image-sample-work-dir}/wdtconfigmap}

# the DB URL and password are set as defined in the following secret

$MIISAMPLEDIR/create_secret.sh \
  -n ${DOMAIN_NAMESPACE} \
  -s ${DOMAIN_UID}-new-db-access-secret \
  -l password=Oradoc_db1 \
  -l url=jdbc:oracle:thin:@oracle-db.${RCUDB_NAMESPACE}.svc.cluster.local:1521/devpdb.k8s

echo "@@ Info: Secret '${DOMAIN_UID}-new-db-access-secret' created in namespace '${DOMAIN_NAMESPACE}'."

# create a WDT configmap with the datasource WDT yaml snippet
mkdir -p ${WDTCONFIGMAPDIR}

cat << EOF > ${WDTCONFIGMAPDIR}/datasource.yaml
resources:
  JDBCSystemResource:
    mynewdatasource:
      Target: 'cluster-1'
      JdbcResource:
        JDBCDataSourceParams:
          JNDIName: [
            jdbc/generic2,
            jdbc/special2
          ]
          GlobalTransactionsProtocol: TwoPhaseCommit
        JDBCDriverParams:
          DriverName: oracle.jdbc.xa.client.OracleXADataSource
          URL: '@@SECRET:@@ENV:DOMAIN_UID@@-new-db-access-secret:url@@'
          PasswordEncrypted: '@@SECRET:@@ENV:DOMAIN_UID@@-new-db-access-secret:password@@'
          Properties:
            user:
              Value: 'sys as sysdba'
            oracle.net.CONNECT_TIMEOUT:
              Value: 5000
            oracle.jdbc.ReadTimeout:
              Value: 30000
        JDBCConnectionPoolParams:
            InitialCapacity: 0
            MaxCapacity: 1                   # This is a comment
            TestTableName: SQL ISVALID       # This is a comment
            TestConnectionsOnReserve: true   # This is a comment
EOF

# Create a config map containing the model snippet

$MIISAMPLEDIR/create_configmap.sh \
  -n ${DOMAIN_NAMESPACE} \
  -c ${DOMAIN_UID}-wdt-config-map \
  -f ${WDTCONFIGMAPDIR}

echo "@@ Info: Datasource added to configmap '${DOMAIN_UID}-wdt-config-map' in namespace '${DOMAIN_NAMESPACE}'."

# Add the secret to the domain resource's configuration.secrets if it's not already there
# TBD this 'add secret to domain' code is messy and little bit buggy.  Can it be simplified?

tfile1=/tmp/$(basename $0).script.a.$PPID
tfile2=/tmp/$(basename $0).script.b.$PPID
kubectl -n ${DOMAIN_NAMESPACE} get domain ${DOMAIN_UID} -o=jsonpath='{.spec.configuration.secrets}' > $tfile1
(
set +e
grep -c "${DOMAIN_UID}-new-db-access-secret" /tmp/$(basename $0).script.a.$PPID > $tfile2
set -e
)
has_secret="`cat $tfile2`"
rm -f $tfile1 $tfile2

if [ "$has_secret" = "0" ]; then
  # TBD The following assumes the secrets path and at least one secret  already exists in the domain resource - otherwise it'll fail
  kubectl patch -n $DOMAIN_NAMESPACE domain $DOMAIN_UID \
    --type='json' \
    -p="[{\"op\":\"add\",\"path\":\"/spec/configuration/secrets/0\",\"value\":\"$DOMAIN_UID-new-db-access-secret\"}]"
  echo "@@ Info: Domain '$DOMAIN_UID' patched to add reference to secret '$DOMAIN_UID-new-db-access-secret'."
else
  echo "@@ Info: Domain '$DOMAIN_UID' already has secret '$DOMAIN_UID-new-db-access-secret'."
fi

echo "@@ Info: The datasource add has been staged. Change 'restartVersion' to load it into your running domain."
