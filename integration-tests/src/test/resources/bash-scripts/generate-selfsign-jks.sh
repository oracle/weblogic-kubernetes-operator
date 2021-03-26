#!/bin/bash
# Copyright (c) 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.


# Usage:
#
#  $0 [install-dir]

# Define functions
function generate_jks_stores {

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
       -out identity.p12 -name "mykey" 

keytool -importkeystore -destkeystore IdentityKeyStore.jks \
        -deststorepass changeit -srckeystore identity.p12 \
        -srcstoretype PKCS12 -srcstorepass changeit 

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
  rm -rf TrustKeyStore.jks IdentityKeyStore.jks
  rm -rf *.p12
)
 generate_jks_stores ${workdir}

( cd $workdir;
  rm -rf *.pem *.der
  rm -rf *.p12
)

