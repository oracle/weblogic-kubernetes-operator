# Copyright (c) 2018, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# This script is run during introspection to detect if secure production mode is enabled in
# an existing domain config.xml.
#

import inspect
import os
import sys
import traceback
from xml.dom.minidom import parse
from java.lang import Boolean


tmp_callerframerecord = inspect.stack()[0]    # 0 represents this line # 1 represents line at caller
tmp_info = inspect.getframeinfo(tmp_callerframerecord[0])
tmp_scriptdir=os.path.dirname(tmp_info[0])
sys.path.append(tmp_scriptdir)

from utils import *
from weblogic.management.configuration import LegalHelper

def checkIfSecureModeEnabledForDomain(domain, domain_version):

  # Caller only invoke this if the current pod version is greater than or 14.1.2.0 and existing domain

  trace("Checking secure mode in existing domain")
  secureModeEnabled = False
  cd('/SecurityConfiguration/' + domain.getName())
  childs = ls(returnType='c', returnMap='true')
  if 'SecureMode' in childs:
    cd('SecureMode/NO_NAME_0')
    attributes = ls(returnType='a', returnMap='true')
    if attributes['SecureModeEnabled']:
      secureModeEnabled = attributes['SecureModeEnabled']
  else:
    # Cannot use domain.getDomainVersion() -> it returns the version of the wls currently using, not
    # the value in <domain-version>*</domain-version>
    #
    secureModeEnabled = domain.isProductionModeEnabled() and not LegalHelper.versionEarlierThan(domain_version, "14.1.2.0")

  if isinstance(secureModeEnabled, str) or isinstance(secureModeEnabled, unicode):
    result =  Boolean.valueOf(secureModeEnabled)
  else:
    result =  secureModeEnabled

  trace("Writing secure mode status as " + str(secureModeEnabled))
  fh = open('/tmp/mii_domain_upgrade.txt', 'w')
  if result:
    fh.write("True")
  else:
    fh.write("False")
  fh.close()
  if LegalHelper.versionEarlierThan(domain_version, "14.1.2.0"):
    fh = open('/tmp/mii_domain_before14120.txt', 'w')
    fh.write("True")
    fh.close()

trace("Checking existing domain at " + str(sys.argv[1]))
try:
  readDomain(sys.argv[1])
  dom_tree = parse(sys.argv[1] + "/config/config.xml")
  collection = dom_tree.documentElement
  nodes = collection.getElementsByTagName('domain-version')
  domain_version = nodes[0].firstChild.nodeValue
  domain = cmo
  checkIfSecureModeEnabledForDomain(domain, domain_version)
except:
  exc_type, exc_obj, exc_tb = sys.exc_info()
  ee_string = traceback.format_exception(exc_type, exc_obj, exc_tb)
  utils.trace('SEVERE', 'Error in mii-domain-upgrade:\n ' + str(ee_string))
  sys.exit(2)