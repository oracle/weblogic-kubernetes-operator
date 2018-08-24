# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

import sys;
#
# +++ Start of common code for reading domain secrets

# Read username secret
file = open('/weblogic-operator/secrets/username', 'r')
admin_username = file.read()
file.close()

# Read password secret
file = open('/weblogic-operator/secrets/password', 'r')
admin_password = file.read()
file.close()

# +++ End of common code for reading domain secrets
#

def getEnvVar(var):
  val=os.environ.get(var)
  if val==None:
    print "ERROR: Env var ",var, " not set."
    sys.exit(1)
  return val

domain_uid = getEnvVar('DOMAIN_UID')
server_name = getEnvVar('SERVER_NAME')
domain_name = getEnvVar('DOMAIN_NAME')
domain_path = getEnvVar('DOMAIN_HOME')

service_name = domain_uid + "-" + server_name

# Connect to nodemanager and stop server
try:
  nmConnect(admin_username, admin_password, service_name,  '5556', domain_name, domain_path, 'plain')
except:
  print('Failed to connect to the NodeManager')
  exit(exitcode=2)

# Kill the server
try:
  nmKill(server_name)
except:
  print('Connected to the NodeManager, but failed to stop the server')
  exit(exitcode=2)

# Exit WLST
nmDisconnect()
exit()

