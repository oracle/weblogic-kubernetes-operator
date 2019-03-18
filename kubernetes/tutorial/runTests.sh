#
# domain1: DomainHomeInImage
# domain2: DomainHomeInImage_ServerLogsInPV
# domain3: DomainHomeOnPV
#
# TODO
##
## voyager https?
#

source waitUntil.sh

export PV_ROOT=/scratch/lihhe/pv
export WLS_OPT_ROOT=../../
export WLS_BASE_IMAGE=store/oracle/weblogic:12.2.1.3

resultFile=results
passcnt=0
failcnt=0

domainUrlTreafik=http://$HOSTNAME:30305/weblogic/
domainUrlTreafikHttps=https://$HOSTNAME:30443/weblogic/
domainUrlVoyager=http://$HOSTNAME:30305/weblogic/

# this need to be run once before any test
function beforeAll() {
  rm $resultFile
  ./operator.sh pullImages

}

# this need to be run once after all tests
function afterAll() {
  #./operator.sh delImages
  printResult
}

# This is to be run before each test
function setup() {
  clean
  echo "setup begin"
  ./domain.sh checkPV
  bash -e ./domain.sh createPV
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

  # clean pv folder
  ./domainHomeBuilder/cleanpv/run.sh
}

#Usage: checkResult result testName
function checkResult() {
   if [ $1 = 0 ]; then
    echo "PASS: $2" >> $resultFile
    ((passcnt=passcnt+1))
  else
    echo "FAIL: $2" >> $resultFile
    ((failcnt=failcnt+1))
    failcases="$failcases $2"
  fi
}

function printResult() {
  echo >> $resultFile
  echo "###################################################"  >> $resultFile
  echo "Test restuls: "  >> $resultFile
  echo "Passed Tests: $passcnt"  >> $resultFile
  echo "Failed Tests: $failcnt"  >> $resultFile
  if [ $failcnt != 0 ]; then 
    echo "Failed Cases: $failcases"  >> $resultFile
  fi
  echo "###################################################"  >> $resultFile
  echo >> $resultFile
  echo >> $resultFile
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

function verifyTraefik() {
  echo "verifyTraefik begin"

  # verify http
  waitUntilHttpReady "domain1 via Traefik" domain1.org $domainUrlTreafik
  checkResult $? verify_Domain1_Traefik_Http
  waitUntilHttpReady "domain2 via Traefik" domain2.org $domainUrlTreafik
  checkResult $? verify_Domain2_Traefik_Http
  waitUntilHttpReady "domain3 via Traefik" domain3.org $domainUrlTreafik
  checkResult $? verify_Domain3_Traefik_Http

  # verify https
  waitUntilHttpsReady "domain1 via Traefik" domain1.org $domainUrlTreafikHttps
  checkResult $? verify_Domain1_Traefik_Https
  waitUntilHttpsReady "domain2 via Traefik" domain2.org $domainUrlTreafikHttps
  checkResult $? verify_Domain2_Traefik_Https
  waitUntilHttpsReady "domain3 via Traefik" domain3.org $domainUrlTreafikHttps
  checkResult $? verify_Domain3_Traefik_Https

  # verify traefik dashboard
  waitUntilHttpReady "traefik dashboard" traefik.example.com  http://$HOSTNAME:30305/dashboard/
  checkResult $? verify_Traefik_Dashboard

  echo "verifyTraefik end"
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


function verifyVoyager() {
  echo "verifyVoyager begin"

  waitUntilHttpReady "domain1 via Voyager" domain1.org $domainUrlVoyager
  checkResult $? verify_Domain1_Voyager_Http
  waitUntilHttpReady "domain2 via Voyager" domain2.org $domainUrlVoyager
  checkResult $? verify_Domain2_Voyager_Http
  waitUntilHttpReady "domain3 via Voyager" domain3.org $domainUrlVoyager
  checkResult $? verify_Domain3_Voyager_Http

  # verify voyager stats 
  # TODO: not hostname
  waitUntilHttpReady "voyager stats" domain1.org http://$HOSTNAME:30317

  echo "verifyVoyager end"
}

function verifyLB() {
  createTraefik && verifyTraefik
  createVoyager && verifyVoyager  
}

# usage: verfiyLBForDomain domainName
function verfiyLBForDomain() {
  createTraefik

  waitUntilHttpReady "$1 via Traefik" $1.org $domainUrlTreafik
  checkResult $? verify_$1_Traefik_Http
  waitUntilHttpsReady "$1 via Traefik" $1.org $domainUrlTreafikHttps
  checkResult $? verify_$1_Traefik_Https

  createVoyager
  waitUntilHttpReady "domain1 via Voyager" domain1.org $domainUrlVoyager
  checkResult $? verify_$1_Voyager_Http
}

function testDomain1WLST() {
  createOperator

  export DOMAIN_BUILD_TYPE=wlst
  createDomain1 create_Domain1_WLST
  verfiyLBForDomain domain1
}

function testDomain2WLST() {
  createOperator
  export DOMAIN_BUILD_TYPE=wlst
  createDomain2 create_Domain2_WLST
  verfiyLBForDomain domain2
}

function testDomain3WLST() {
  createOperator

  export DOMAIN_BUILD_TYPE=wlst
  createDomain3 create_Domain3_WLST
  verfiyLBForDomain domain3
}

function testDomain1WDT() {
  createOperator

  export DOMAIN_BUILD_TYPE=wdt
  createDomain1 create_Domain1_WDT
  verfiyLBForDomain domain1
}

function testDomain2WDT() {
  createOperator
  export DOMAIN_BUILD_TYPE=wdt
  createDomain2 create_Domain2_WDT
  verfiyLBForDomain domain2
}

function testDomain3WDT() {
  createOperator

  export DOMAIN_BUILD_TYPE=wdt
  createDomain3 create_Domain3_WDT
  verfiyLBForDomain domain3
}


function testWLST() {
  createOperator

  export DOMAIN_BUILD_TYPE=wlst
  createDomain1 create_Domain1_WLST
  createDomain2 create_Domain2_WLST
  createDomain3 create_Domain3_WLST

  verifyLB
}

function testWDT() {
  createOperator

  export DOMAIN_BUILD_TYPE=wdt
  createDomain1 create_Domain1_WDT
  createDomain2 create_Domain2_WDT
  createDomain3 create_Domain3_WDT

  verifyLB
}

# usage: runOne testName
function runOne() {
  SECONDS=0
  setup
  $1
  echo "$0 took $(($SECONDS / 60)) minutes and $(($SECONDS % 60)) seconds to finish $1."   
}

# This suite contains all the tests. It's suitable for daily-run.
function runSuiteOne() {
  runOne testWLST
  runOne testWDT
}

## This suite can run tests of seperate domains with WLST.
function runSuiteTwo() {
  runOne testDomain1WLST
  runOne testDomain2WLST
  runOne testDomain3WLST
}

## This suite can run tests of seperate domains with WDT.
function runSuiteThree() {
  runOne testDomain1WDT
  runOne testDomain2WDT
  runOne testDomain3WDT
}

function runAll() {
  beforeAll
  runSuiteThree
  runSuiteOne
  runSuiteTwo
  afterAll
}

runAll




