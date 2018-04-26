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
domain_uid = sys.argv[1]
server_name = sys.argv[2]
domain_name = sys.argv[3]

service_name = domain_uid + "-" + server_name
domain_path='/shared/domain/%s' % domain_name

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

