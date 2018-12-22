# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

def getEnvVar(var):
  val=os.environ.get(var)
  if val==None:
    print "ERROR: Env var ",var, " not set."
    sys.exit(1)
  return val

# This python script is used to configure a Node Manager

domain_name                  = getEnvVar("DOMAIN_NAME")
domain_path                  = getEnvVar("DOMAIN_HOME_DIR")
script_dir                   = getEnvVar("CREATE_DOMAIN_SCRIPT_DIR")

print 'Setting up WebLogic NodeManager'
print('domain_path        : [%s]' % domain_path);

# Read the domain secrets from the common python file
execfile('%s/read-domain-secret.py' % script_dir)

# Update Domain
readDomain(domain_path)

# Configure the node manager
# ==========================
cd('/NMProperties')
set('ListenAddress','0.0.0.0')
set('ListenPort',5556)
set('CrashRecoveryEnabled', 'true')
set('NativeVersionEnabled', 'true')
set('StartScriptEnabled', 'false')
set('SecureListener', 'false')
set('LogLevel', 'FINEST')
set('DomainsDirRemoteSharingEnabled', 'true')

# Set the Node Manager user name and password (domain name will change after writeDomain)
cd('/SecurityConfiguration/%s' % domain_name)
set('NodeManagerUsername', admin_username)
set('NodeManagerPasswordEncrypted', admin_password)

updateDomain()
print 'NodeManger has been set up'
print 'Done'

# Exit WLST
# =========
exit()
