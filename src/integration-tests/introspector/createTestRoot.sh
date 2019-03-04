#!/bin/sh

# This script runs in a pod via krun.sh, it sets up the top
# level directory for this test that in turn is mounted as /shared by the 
# test's wl-pvc, wl-pv, and wl-pod yamlt (plus similar 'mysql' yamlt).

# The 'acceptance_test_pv' directory it creates matches the directory 
# expected by the integration test 'cleanup.sh' script.

# The 'pv-root' directory is mounted in the krun.sh command line.  It physically
# corresponds to the PV_ROOT env var path specified as an input to the test.

DOMAIN_UID=${1?}

mkdir -p /pv-root/acceptance_test_pv/domain-${DOMAIN_UID}-storage/domains || exit 1
mkdir -p /pv-root/acceptance_test_pv/domain-${DOMAIN_UID}-storage/mysql || exit 1
chmod 777 /pv-root/acceptance_test_pv || exit 1
chmod 777 /pv-root/acceptance_test_pv/domain-${DOMAIN_UID}-storage || exit 1
chmod 777 /pv-root/acceptance_test_pv/domain-${DOMAIN_UID}-storage/domains || exit 1
chmod 777 /pv-root/acceptance_test_pv/domain-${DOMAIN_UID}-storage/mysql || exit 1
