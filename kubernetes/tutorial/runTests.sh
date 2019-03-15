#
# domain1: DomainHomeInImage
# domain2: DomainHomeInImage_ServerLogsInPV
# domain3: DomainHomeOnPV
#
# TODO
## traefik and voyager together: voyager-domain1-traefik-xxx
####  config voyager only monitor voyager-ing?  
## PV clean?
## voyager https?
#

export PV_ROOT=/scratch/lihhe/pv
export WLS_OPT_ROOT=../../
export WLS_BASE_IMAGE=store/oracle/weblogic:12.2.1.3

passcnt=0
failcnt=0

function setup() {
  echo "setup begin"
  ./domain.sh checkPV
  bash -e ./domain.sh createPV
  ./operator.sh pullImages
  echo "setup end"
}

function clean() {
./voyager.sh delIng
./voyager.sh delCon

./traefik.sh delIng
./traefik.sh delCon

# clean WebLogic domains and operator
./domain.sh delAll
./domain.sh waitUntilAllStopped
./operator.sh delete
./operator.sh delImages
}

#Usage: checkResult result testName
function checkResult() {
   if [ $1 = 0 ]; then
    echo "PASS: $2" >> results
    ((passcnt=passcnt+1))
  else
    echo "FAIL: $2" >> results
    ((failcnt=failcnt+1))
    failcases="$failcases $2"
  fi
}

function printResult() {
  echo "Test restuls: "  >> results
  echo "Passed Tests: $passcnt"  >> results
  echo "Failed Tests: $failcnt"  >> results
  echo "Failed Cases: $failcases"  >> results
}

function createOperator() {
  echo "createOperator begin"
  ./operator.sh create
  result=$?
  checkResult $result create_Operator
  if [ $result != 0 ]; then
    exit 1
  fi
  echo "createOperator end"
}

#Usage: createDomain1 testName
function createDomain1() {
  echo "$1 begin"
  ./domain.sh createDomain1
  ./domain.sh waitUntilReady default domain1
  checkResult $? $1
  echo "$1 end"
}

#Usage: createDomain2 testName
function createDomain2() {
  echo "$1 begin"
  ./domain.sh createDomain2
  ./domain.sh waitUntilReady test1 domain2
  checkResult $? $1
  echo "$1 end"
}

#Usage: createDomain3 testName
function createDomain3() {
  echo "$1 begin"
  ./domain.sh createDomain3
  ./domain.sh waitUntilReady test1 domain3
  checkResult $? $1
  echo "$1 end"
}

function createTraefik() {
  echo "createTraefik begin"
  ./traefik.sh createCon && ./traefik.sh createIng
  result=$?
  checkResult $result create_Traefik
  echo "createTraefik end"
  return $result
}

function verifyDomainsWithTraefik() {
  echo "verifyDomainsWithTraefik begin"

  # verify http
  http_code=$(curl -s -o /dev/null -w "%{http_code}"  -H 'host: domain1.org' http://$HOSTNAME:30305/weblogic/)
  verifyHTTPCode $http_code verify_Domain1_Traefik_Http

  http_code=$(curl -s -o /dev/null -w "%{http_code}"  -H 'host: domain2.org' http://$HOSTNAME:30305/weblogic/)
  verifyHTTPCode $http_code verify_Domain2_Traefik_Http

  http_code=$(curl -s -o /dev/null -w "%{http_code}"  -H 'host: domain3.org' http://$HOSTNAME:30305/weblogic/)
  verifyHTTPCode $http_code verify_Domain3_Traefik_Http

  # verify traefik dashboard
  http_code=$(curl -s -o /dev/null -w "%{http_code}"  -H 'host: traefik.example.com' http://$HOSTNAME:30305/dashboard/)
  verifyHTTPCode $http_code verify_Traefik_Dashboard

  # verify https
  http_code=$(curl -k -s -o /dev/null -w "%{http_code}"  -H 'host: domain1.org' https://$HOSTNAME:30443/weblogic/)
  verifyHTTPCode $http_code verify_Domain1_Traefik_Https

  http_code=$(curl -k -s -o /dev/null -w "%{http_code}"  -H 'host: domain2.org' https://$HOSTNAME:30443/weblogic/)
  verifyHTTPCode $http_code verify_Domain2_Traefik_Https

  http_code=$(curl -k -s -o /dev/null -w "%{http_code}"  -H 'host: domain3.org' https://$HOSTNAME:30443/weblogic/)
  verifyHTTPCode $http_code verify_Domain3_Traefik_Https

  echo "verifyDomainsWithTraefik end"
}

function createVoyager() {
  echo "createVoyager begin"
  ./voyager.sh createCon && ./voyager.sh createIng
  result=$?
  checkResult $result create_Voyager
  echo "createVoyager end"
  return $result
}

#Usage: verifyHTTP  httpCode testName
function verifyHTTPCode() {
  if [ $1 = 200 ]; then 
    checkResult 0 $2
  else
    checkResult 1 $2
  fi
}


function verifyDomainsWithVoyager() {
  echo "verifyDomainsWithVoyager begin"
  # verify http
  http_code=$(curl -s -o /dev/null -w "%{http_code}"  -H 'host: domain1.org' http://$HOSTNAME:30307/weblogic/)
  verifyHTTPCode $http_code verify_Domain1_Voyager_Http

  http_code=$(curl -s -o /dev/null -w "%{http_code}"  -H 'host: domain2.org' http://$HOSTNAME:30307/weblogic/)
  verifyHTTPCode $http_code verify_Domain2_Voyager_Http

  http_code=$(curl -s -o /dev/null -w "%{http_code}"  -H 'host: domain3.org' http://$HOSTNAME:30307/weblogic/)
  verifyHTTPCode $http_code verify_Domain3_Voyager_Http

  # verify voyager stats
  http_code=$(curl -s -o /dev/null -w "%{http_code}" http://$HOSTNAME:30317)
  verifyHTTPCode $http_code verify_Voyager_Stats

  echo "verifyDomainsWithVoyager end"
}

function testWLST() {
  setup
  createOperator

  export DOMAIN_BUILD_TYPE=wlst
  createDomain1 create_Domain1_WLST
  createDomain2 create_Domain2_WLST
  createDomain3 create_Domain3_WLST

  createTraefik && verifyDomainsWithTraefik
  ./traefik.sh delIng && ./traefik.sh delCon
  createVoyager && verifyDomainsWithVoyager

  printResult

}

function testWDT() {
  setup
  createOperator

  export DOMAIN_BUILD_TYPE=wdt
  createDomain1 create_Domain1_WDT
  createDomain2 create_Domain2_WDT
  createDomain3 create_Domain3_WDT

  createTraefik && verifyDomainsWithTraefik
  createVoyager && verifyDomainsWithVoyager

  printResult

}

rm results
clean
testWLST





