usage(){
cat <<EOF
Usage: $0 [options] -a <subject alternative names> -n <namespace>
Options:
-a  SANS           Required, the SANs for the certificate
-n  NAMESPACE      Required, the namespace where the secret will be created.
-s  SECRET_NAME    Optional, the name of the kubernetes secret. Default is: weblogic-operator-external-rest-identity.
-h, --help         Display this help text.
EOF
exit 1
}

if [ ! -x "$(command -v keytool)" ]; then
  echo "Can't find keytool.  Please add it to the path."
  exit 1
fi

if [ ! -x "$(command -v openssl)" ]; then
  echo "Can't find openssl.  Please add it to the path."
  exit 1
fi

if [ ! -x "$(command -v base64)" ]; then
  echo "Can't find base64.  Please add it to the path."
  exit 1
fi

TEMP_DIR=`mktemp -d`
if [ $? -ne 0 ]; then
  echo "$0: Can't create temp directory."
  exit 1
fi

if [ -z $TEMP_DIR ]; then
  echo "Can't create temp directory."
  exit 1
fi

function cleanup {
  rm -r $TEMP_DIR
  if [[ $SUCCEEDED != "true" ]]; then
    exit 1
  fi
}

set -e
#set -x

trap "cleanup" EXIT

SECRET_NAME="weblogic-operator-external-rest-identity"

while [ $# -gt 0 ]
  do 
    key="$1"
    case $key in
      -a)
      shift # past argument
      if [ $# -eq 0 ] || [ ${1:0:1} == "-" ]; then echo "SANs is required and is missing"; usage; fi
      SANS=$1
      shift # past value
      ;;
      -n)
      shift # past argument
      if [ $# -eq 0 ] || [ ${1:0:1} == "-" ]; then echo "Namespace is required and is missing"; usage; fi
      NAMESPACE=$1
      shift # past value
      ;;
      -s)
      shift # past argument
      if [ $# -eq 0 ] || [ ${1:0:1} == "-" ]; then echo "Invalid secret name $1"; usage; fi
      SECRET_NAME=$1
      shift # past value
      ;;
      -h)
      shift # past argument
      ;;
      *)
      SANS=$1
      shift # past argument
      ;;  
    esac    
done

if [ -z "$SANS" ]
then
  1>&2
  echo "SANs is required and is missing"
  usage
fi

if [ -z "$NAMESPACE" ]
then
  1>&2
  echo "Namespace is required and is missing"
  usage
fi

DAYS_VALID="3650"
TEMP_PW="temp_password"
OP_PREFIX="weblogic-operator"
ROOT_ALIAS="${OP_PREFIX}-root-alias"
CA_ALIAS="${OP_PREFIX}-ca-alias"
OP_ALIAS="${OP_PREFIX}-alias"
ROOT_JKS="${TEMP_DIR}/${OP_PREFIX}-root.jks"
CA_JKS="${TEMP_DIR}/${OP_PREFIX}-ca.jks"
OP_JKS="${TEMP_DIR}/${OP_PREFIX}.jks"
OP_PKCS12="${TEMP_DIR}/${OP_PREFIX}.p12"
OP_CSR="${TEMP_DIR}/${OP_PREFIX}.csr"
OP_ROOT_PEM="${TEMP_DIR}/${OP_PREFIX}.root.pem"
OP_CA_PEM="${TEMP_DIR}/${OP_PREFIX}.ca.pem"
OP_CERT_PEM="${TEMP_DIR}/${OP_PREFIX}.cert.pem"
OP_KEY_PEM="${TEMP_DIR}/${OP_PREFIX}.key.pem"
KEYTOOL=/usr/java/jdk1.8.0_141/bin/keytool

# generate private keys (for operator-root and operator-ca)

keytool \
   -genkeypair \
   -alias ${ROOT_ALIAS} \
   -dname cn=operator-root \
   -validity 10000 \
   -keyalg RSA \
   -keysize 2048 \
   -keystore ${ROOT_JKS} \
   -keypass ${TEMP_PW} \
   -storepass ${TEMP_PW}
   
keytool \
   -genkeypair \
   -alias ${CA_ALIAS} \
   -dname cn=operator-ca \
   -validity 10000 \
   -keyalg RSA \
   -keysize 2048 \
   -keystore ${CA_JKS} \
   -keypass ${TEMP_PW} \
   -storepass ${TEMP_PW}

# generate operator-root certificate

keytool \
   -exportcert \
   -rfc \
   -keystore ${ROOT_JKS} \
   -alias ${ROOT_ALIAS} \
   -storepass ${TEMP_PW} \
   > ${OP_ROOT_PEM}

keytool \
   -keystore ${CA_JKS} \
   -storepass ${TEMP_PW} \
   -importcert \
   -noprompt \
   -alias ${ROOT_ALIAS} \
   -file ${OP_ROOT_PEM}
# generate a certificate for operator-ca signed by operator-root (operator-root -> operator-ca)

keytool \
   -keystore ${CA_JKS} \
   -storepass ${TEMP_PW} \
   -certreq \
   -alias ${CA_ALIAS} | \
   keytool \
      -keystore ${ROOT_JKS} \
      -storepass ${TEMP_PW} \
      -gencert \
      -alias ${ROOT_ALIAS} \
      -ext bc=0 \
      -ext san=${SANS} \
      -rfc \
   > ${OP_CA_PEM}

# import operator-ca cert chain into operator-ca.jks
   
keytool \
   -keystore ${CA_JKS} \
   -storepass ${TEMP_PW} \
   -importcert \
   -alias ${CA_ALIAS} \
   -file ${OP_CA_PEM}

# generate private keys (for operator)

keytool \
  -genkeypair \
  -keystore ${OP_JKS} \
  -alias ${OP_ALIAS} \
  -storepass ${TEMP_PW} \
  -keypass ${TEMP_PW} \
  -keysize 2048 \
  -keyalg RSA \
  -validity ${DAYS_VALID} \
  -dname "CN=weblogic-operator" \
  -ext KU=digitalSignature,nonRepudiation,keyEncipherment,dataEncipherment,keyAgreement \
  -ext SAN="${SANS}" \
2> /dev/null

# generate a certificate for operator signed by operator-ca (operator-root -> operator-ca -> weblogic-operator)

keytool \
   -keystore ${OP_JKS} \
   -storepass ${TEMP_PW} \
   -certreq \
   -alias ${OP_ALIAS} | \
   keytool \
      -keystore ${CA_JKS} \
      -storepass ${TEMP_PW} \
      -gencert -alias ${CA_ALIAS} \
      -ext ku:c=digitalSignature,keyCertSign,keyEncipherment \
      -ext san="${SANS}" \
      -ext eku=serverAuth,clientAuth -rfc \
   > ${OP_CERT_PEM}

# convert the keystore to a pkcs12 file
keytool \
  -importkeystore \
  -srckeystore ${OP_JKS} \
  -srcstorepass ${TEMP_PW} \
  -destkeystore ${OP_PKCS12} \
  -srcstorepass ${TEMP_PW} \
  -deststorepass ${TEMP_PW} \
  -deststoretype PKCS12 \
2> /dev/null

# extract the private key from the pkcs12 file to a pem file
openssl \
  pkcs12 \
  -in ${OP_PKCS12} \
  -passin pass:${TEMP_PW} \
  -nodes \
  -nocerts \
  -out ${OP_KEY_PEM} \
2> /dev/null

set +e
# Check if namespace exist
kubectl get namespace $NAMESPACE >/dev/null 2>/dev/null
if [ $? -eq 1 ]; then
  echo "Namespace $NAMESPACE does not exist"
  exit 1
fi
kubectl get secret $SECRET_NAME -n $NAMESPACE >/dev/null 2>/dev/null
if [ $? -eq 1 ]; then
  kubectl create secret tls "$SECRET_NAME" --cert=${OP_CERT_PEM} --key=${OP_KEY_PEM} -n $NAMESPACE >/dev/null
fi
echo "externalRestIdentitySecret: $SECRET_NAME"

SUCCEEDED=true
