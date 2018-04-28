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
domain_uid = sys.argv[1]
server_name = sys.argv[2]
domain_name = sys.argv[3]
if (len(sys.argv) == 5):
  admin_server_url = sys.argv[4]
else:
  admin_server_url = None

domain_path='/shared/domain/%s' % domain_name

print 'admin username is %s' % admin_username
print 'domain path is %s' % domain_path
print 'server name is %s' % server_name
print 'admin server url is %s' % admin_server_url

# Encrypt the admin username and password
adminUsernameEncrypted=encrypt(admin_username, domain_path)
adminPasswordEncrypted=encrypt(admin_password, domain_path)

print 'Create boot.properties files for this server'

# Define the folder path
secdir='%s/servers/%s/security' % (domain_path, server_name)

# Create the security folder (if it does not already exist)
try:
  os.makedirs(secdir)
except OSError:
  if not os.path.isdir(secdir):
    raise

print 'writing boot.properties to %s/servers/%s/security/boot.properties' % (domain_path, server_name)

bpFile=open('%s/servers/%s/security/boot.properties' % (domain_path, server_name), 'w+')
bpFile.write("username=%s\n" % adminUsernameEncrypted)
bpFile.write("password=%s\n" % adminPasswordEncrypted)
bpFile.close()

service_name = domain_uid + "-" + server_name

# Connect to nodemanager and start server
try:
  nmConnect(admin_username, admin_password, service_name,  '5556', domain_name, domain_path, 'plain')
  nmStart(server_name)
  nmDisconnect()
except WLSTException, e:
  nmDisconnect()
  print e

# Exit WLST
exit()

