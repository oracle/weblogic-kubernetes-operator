#  Download the webloic imagetool from github, follow the quickstart guide to setup

#  This will create a base image with patches for the operator and the april psu

source $WORKDIR/imagetool/imagetool-*.*.*/bin/setup.sh

imagetool create --tag model-in-image:12213p2 --user yourid --passwordEnv $1 --patches 29135930_12.2.1.3.190416,29016089 
#--httpProxyUrl proxy --httpsProxyUrl proxy

# This will create a domain in image using wdt

#imagetool create --tag model-in-image:12213p2h1 --user yourid --passwordEnv MYPWD --patches 29135930_12.2.1.3.190416,29016089 --wdtModel model1.yaml --wdtDomainType WLS --wdtDomainHome /u01/domains/domain1 --wdtArchive archive1.zip

