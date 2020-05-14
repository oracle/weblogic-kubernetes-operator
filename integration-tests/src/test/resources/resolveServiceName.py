import socket
import sys

name = sys.argv[1]
print 'Resolving Server Service Name for ' + name
ipAddress = socket.gethostbyname(name)
print('IP address of host name ' + name + ' is: ' + ipAddress)
