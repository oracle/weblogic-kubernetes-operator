#!/bin/bash
# Copyright (c) 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.


# Usage:
#
#  $0 [install-dir]

# Define functions
generate_jks_stores() {

( cd $workdir;

host=`hostname`

openssl req -newkey rsa:2048 -days 1 \
       -passout pass:changeit -passin pass:changeit \
       -x509 -keyout cakey.pem -out cacert.pem \
       -subj "/C=US/ST=NJ/L=Basking Ridge/O=QA/CN=${host}"

#cakey.pem is the private key
#cacert.pem is the public certificate

openssl pkcs12 -export -in cacert.pem -inkey cakey.pem \
       -passout pass:changeit -passin pass:changeit \
       -out identity1.p12 -name "mykeysen"

openssl pkcs12 -export -in cacert.pem -inkey cakey.pem \
       -passout pass:changeit -passin pass:changeit \
       -out identity2.p12 -name "mykeyrec"

openssl pkcs12 -export -in cacert.pem -inkey cakey.pem \
       -passout pass:changeit -passin pass:changeit \
       -out useridentity.p12 -name "user_d1"

keytool -importkeystore -destkeystore PkiKeyStore.jks \
        -deststorepass changeit -srckeystore useridentity.p12 \
        -srcstoretype PKCS12 -srcstorepass changeit

keytool -importkeystore -destkeystore Identity1KeyStore.jks \
        -deststorepass changeit -srckeystore identity1.p12 \
        -srcstoretype PKCS12 -srcstorepass changeit

keytool -importkeystore -destkeystore Identity2KeyStore.jks \
        -deststorepass changeit -srckeystore identity2.p12 \
        -srcstoretype PKCS12 -srcstorepass changeit

keytool -exportcert -alias mykeyrec -keystore Identity2KeyStore.jks -storepass changeit -file certrec.pem
keytool -exportcert -alias user_d1 -keystore PkiKeyStore.jks -storepass changeit -file cert1.pem
keytool -import -file cacert.pem -keystore TrustKeyStore.jks \
        -storepass changeit -noprompt

)

}

# MAIN
workdir=${1:-`pwd`}

if [ ! -d ${workdir} ]; then
  mkdir -p $workdir
fi

( cd $workdir;
  rm -rf *.pem *.der
  rm -rf TrustKeyStore.jks IdentityKeyStore.jks PkiKeyStore.jks
  rm -rf *.p12
)
 generate_jks_stores ${workdir}

( cd $workdir;
  #rm -rf *.pem *.der
  #rm -rf *.p12
)

