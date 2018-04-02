// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ConfigMapHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String START_SERVER_SHELL_SCRIPT = "#!/bin/bash\n" +
          "\n" +
          "domain_uid=$1\n" +
          "server_name=$2\n" +
          "domain_name=$3\n" +
          "as_name=$4\n" +
          "as_port=$5\n" +
          "as_hostname=$1-$4\n" +
          "\n" +
          "echo \"debug arguments are $1 $2 $3 $4 $5\"\n" +
          "\n" +
          "nmProp=\"/u01/nodemanager/nodemanager.properties\"\n" +
          "\n" +
          "# TODO: parameterize shared home and domain name\n" +
          "export DOMAIN_HOME=/shared/domain/$domain_name\n" +
          "\n" +
          "#\n" +
          "# Create a folder\n" +
          "# $1 - path of folder to create\n" +
          "function createFolder {\n" +
          "  mkdir -m 777 -p $1\n" +
          "  if [ ! -d $1 ]; then\n" +
          "    fail \"Unable to create folder $1\"\n" +
          "  fi\n" +
          "}\n" +
          "\n" +
          "# Function to create server specific scripts and properties (e.g startup.properties, etc)\n" +
          "# $1 - Domain UID\n" +
          "# $2 - Server Name\n" +
          "# $3 - Domain Name\n" +
          "# $4 - Admin Server Hostname (only passed for managed servers)\n" +
          "# $5 - Admin Server port (only passed for managed servers)\n" +
          "function createServerScriptsProperties() {\n" +
          "\n" +
          "  # Create nodemanager home for the server\n" +
          "  srvr_nmdir=/u01/nodemanager\n" +
          "  createFolder ${srvr_nmdir}\n" +
          "  cp /shared/domain/$3/nodemanager/nodemanager.domains ${srvr_nmdir}\n" +
          "  cp /shared/domain/$3/bin/startNodeManager.sh ${srvr_nmdir}\n" +
          "\n" +
          "  # Edit the start nodemanager script to use the home for the server\n" +
          "  sed -i -e \"s:/shared/domain/$3/nodemanager:/u01/nodemanager:g\" /startNodeManager.sh\n" +
          "\n" +
          "  # Create startup.properties file\n" +
          "  datadir=${DOMAIN_HOME}/servers/$2/data/nodemanager\n" +
          "  nmdir=${DOMAIN_HOME}/nodemgr_home\n" +
          "  stateFile=${datadir}/$2.state\n" +
          "  startProp=${datadir}/startup.properties\n" +
          "  if [ -f \"$startProp\" ]; then\n" +
          "    echo \"startup.properties already exists\"\n" +
          "    return 0\n" +
          "  fi\n" +
          "\n" +
          "  createFolder ${datadir}\n" +
          "  echo \"# Server startup properties\" > ${startProp}\n" +
          "  echo \"AutoRestart=true\" >> ${startProp}\n" +
          "  if [ -n \"$4\" ]; then\n" +
          "    echo \"AdminURL=http\\://$4\\:$5\" >> ${startProp}\n" +
          "  fi\n" +
          "  echo \"RestartMax=2\" >> ${startProp}\n" +
          "  echo \"RotateLogOnStartup=false\" >> ${startProp}\n" +
          "  echo \"RotationType=bySize\" >> ${startProp}\n" +
          "  echo \"RotationTimeStart=00\\:00\" >> ${startProp}\n" +
          "  echo \"RotatedFileCount=100\" >> ${startProp}\n" +
          "  echo \"RestartDelaySeconds=0\" >> ${startProp}\n" +
          "  echo \"FileSizeKB=5000\" >> ${startProp}\n" +
          "  echo \"FileTimeSpanFactor=3600000\" >> ${startProp}\n" +
          "  echo \"RestartInterval=3600\" >> ${startProp}\n" +
          "  echo \"NumberOfFilesLimited=true\" >> ${startProp}\n" +
          "  echo \"FileTimeSpan=24\" >> ${startProp}\n" +
          "  echo \"NMHostName=$1-$2\" >> ${startProp}\n" +
          "}\n" +
          "\n" +
          "# Check for stale state file and remove if found\"\n" +
          "if [ -f ${stateFile} ]; then\n" +
          "  echo \"Removing stale file ${stateFile}\"\n" +
          "  rm ${stateFile}\n" +
          "fi\n" +
          "\n" +
          "# Create nodemanager home directory that is local to the k8s node\n" +
          "mkdir -p /u01/nodemanager\n" +
          "cp ${DOMAIN_HOME}/nodemanager/* /u01/nodemanager/\n" +
          "\n" +
          "# Edit the nodemanager properties file to use the home for the server\n" +
          "sed -i -e \"s:DomainsFile=.*:DomainsFile=/u01/nodemanager/nodemanager.domains:g\" /u01/nodemanager/nodemanager.properties\n" +
          "sed -i -e \"s:NodeManagerHome=.*:NodeManagerHome=/u01/nodemanager:g\" /u01/nodemanager/nodemanager.properties\n" +
          "sed -i -e \"s:ListenAddress=.*:ListenAddress=$1-$2:g\" /u01/nodemanager/nodemanager.properties\n" +
          "sed -i -e \"s:LogFile=.*:LogFile=/shared/logs/nodemanager-$2.log:g\" /u01/nodemanager/nodemanager.properties\n" +
          "\n" +
          "export JAVA_PROPERTIES=\"-DLogFile=/shared/logs/nodemanager-$server_name.log -DNodeManagerHome=/u01/nodemanager\"\n" +
          "export NODEMGR_HOME=\"/u01/nodemanager\"\n" +
          "\n" +
          "\n" +
          "# Create startup.properties\n" +
          "echo \"Create startup.properties\"\n" +
          "if [ -n \"$4\" ]; then\n" +
          "  echo \"this is managed server\"\n" +
          "  createServerScriptsProperties $domain_uid $server_name $domain_name $as_hostname $as_port\n" +
          "else\n" +
          "  echo \"this is admin server\"\n" +
          "  createServerScriptsProperties $domain_uid $server_name $domain_name\n" +
          "fi\n" +
          "\n" +
          "echo \"Start the nodemanager\"\n" +
          ". ${NODEMGR_HOME}/startNodeManager.sh &\n" +
          "\n" +
          "echo \"Allow the nodemanager some time to start before attempting to connect\"\n" +
          "sleep 15\n" +
          "echo \"Finished waiting for the nodemanager to start\"\n" +
          "\n" +
          "echo \"Update JVM arguments\"\n" +
          "echo \"Arguments=${USER_MEM_ARGS} -XX\\:+UnlockExperimentalVMOptions -XX\\:+UseCGroupMemoryLimitForHeap ${JAVA_OPTIONS}\" >> ${startProp}\n" +
          "\n" +
          "admin_server_t3_url=\n" +
          "if [ -n \"$4\" ]; then\n" +
          "  admin_server_t3_url=t3://$domain_uid-$as_name:$as_port\n" +
          "fi\n" +
          "\n" +
          "echo \"Start the server\"\n" +
          "wlst.sh -skipWLSModuleScanning /weblogic-operator/scripts/start-server.py $domain_uid $server_name $domain_name $admin_server_t3_url\n" +
          "\n" +
          "echo \"Wait indefinitely so that the Kubernetes pod does not exit and try to restart\"\n" +
          "while true; do sleep 60; done\n";

  private static final String START_SERVER_PYTHON_SCRIPT = "import sys;\n" +
          "#\n" +
          "# +++ Start of common code for reading domain secrets\n" +
          "\n" +
          "# Read username secret\n" +
          "file = open('/weblogic-operator/secrets/username', 'r')\n" +
          "admin_username = file.read()\n" +
          "file.close()\n" +
          "\n" +
          "# Read password secret\n" +
          "file = open('/weblogic-operator/secrets/password', 'r')\n" +
          "admin_password = file.read()\n" +
          "file.close()\n" +
          "\n" +
          "# +++ End of common code for reading domain secrets\n" +
          "#\n" +
          "domain_uid = sys.argv[1]\n" +
          "server_name = sys.argv[2]\n" +
          "domain_name = sys.argv[3]\n" +
          "if (len(sys.argv) == 5):\n" +
          "  admin_server_url = sys.argv[4]\n" +
          "else:\n" +
          "  admin_server_url = None\n" +
          "\n" +
          "domain_path='/shared/domain/%s' % domain_name\n" +
          "\n" +
          "print 'admin username is %s' % admin_username\n" +
          "print 'domain path is %s' % domain_path\n" +
          "print 'server name is %s' % server_name\n" +
          "print 'admin server url is %s' % admin_server_url\n" +
          "\n" +
          "# Encrypt the admin username and password\n" +
          "adminUsernameEncrypted=encrypt(admin_username, domain_path)\n" +
          "adminPasswordEncrypted=encrypt(admin_password, domain_path)\n" +
          "\n" +
          "print 'Create boot.properties files for this server'\n" +
          "\n" +
          "# Define the folder path\n" +
          "secdir='%s/servers/%s/security' % (domain_path, server_name)\n" +
          "\n" +
          "# Create the security folder (if it does not already exist)\n" +
          "try:\n" +
          "  os.makedirs(secdir)\n" +
          "except OSError:\n" +
          "  if not os.path.isdir(secdir):\n" +
          "    raise\n" +
          "\n" +
          "print 'writing boot.properties to %s/servers/%s/security/boot.properties' % (domain_path, server_name)\n" +
          "\n" +
          "bpFile=open('%s/servers/%s/security/boot.properties' % (domain_path, server_name), 'w+')\n" +
          "bpFile.write(\"username=%s\\n\" % adminUsernameEncrypted)\n" +
          "bpFile.write(\"password=%s\\n\" % adminPasswordEncrypted)\n" +
          "bpFile.close()\n" +
          "\n" +
          "service_name = domain_uid + \"-\" + server_name\n" +
          "\n" +
          "# Update node manager listen address\n" +
          "if admin_server_url is not None:\n" +
          "  connect(admin_username, admin_password, admin_server_url)\n" +
          "  serverConfig()\n" +
          "  server=cmo.lookupServer(server_name)\n" +
          "  machineName=server.getMachine().getName()\n" +
          "  print 'Name of machine assigned to server %s is %s' % (server_name, machineName)\n" +
          "\n" +
          "  if machineName is not None:\n" +
          "    print 'Updating listen address of machine %s' % machineName\n" +
          "    try:\n" +
          "      edit()\n" +
          "      startEdit(120000, 120000, 'true')\n" +
          "      cd('/')\n" +
          "      machine=cmo.lookupMachine(machineName)\n" +
          "      print 'Machine is %s' % machine\n" +
          "      nm=machine.getNodeManager()\n" +
          "      nm.setListenAddress(service_name)\n" +
          "      nm.setNMType('Plain')\n" +
          "      save()\n" +
          "      activate()\n" +
          "      print 'Updated listen address of machine %s to %s' % (machineName, service_name)\n" +
          "    except:\n" +
          "      cancelEdit('y')\n" +
          "  disconnect()\n" +
          "\n" +
          "# Connect to nodemanager and start server\n" +
          "try:\n" +
          "  nmConnect(admin_username, admin_password, service_name,  '5556', domain_name, domain_path, 'plain')\n" +
          "  nmStart(server_name)\n" +
          "  nmDisconnect()\n" +
          "except WLSTException, e:\n" +
          "  nmDisconnect()\n" +
          "  print e\n" +
          "\n" +
          "# Exit WLST\n" +
          "exit()\n";

  private static final String STOP_SERVER_SHELL_SCRIPT = "#!/bin/bash\n" +
          "\n" +
          "echo \"Stop the server\"\n" +
          "\n" +
          "wlst.sh -skipWLSModuleScanning /weblogic-operator/scripts/stop-server.py $1 $2 $3\n" +
          "\n" +
          "# Return status of 2 means failed to stop a server through the NodeManager.\n" +
          "# Look to see if there is a server process that can be killed.\n" +
          "if [ $? -eq 2 ]; then\n" +
          "  pid=$(jps -v | grep '[D]weblogic.Name=$2' | awk '{print $1}')\n" +
          "  if [ ! -z $pid ]; then\n" +
          "    echo \"Killing the server process $pid\"\n" +
          "    kill -15 $pid\n" +
          "  fi\n" +
          "fi\n" +
          "\n";

  private static final String STOP_SERVER_PYTHON_SCRIPT = "#\n" +
          "# +++ Start of common code for reading domain secrets\n" +
          "\n" +
          "# Read username secret\n" +
          "file = open('/weblogic-operator/secrets/username', 'r')\n" +
          "admin_username = file.read()\n" +
          "file.close()\n" +
          "\n" +
          "# Read password secret\n" +
          "file = open('/weblogic-operator/secrets/password', 'r')\n" +
          "admin_password = file.read()\n" +
          "file.close()\n" +
          "\n" +
          "# +++ End of common code for reading domain secrets\n" +
          "#\n" +
          "domain_uid = sys.argv[1]\n" +
          "server_name = sys.argv[2]\n" +
          "domain_name = sys.argv[3]\n" +
          "\n" +
          "service_name = domain_uid + \"-\" + server_name\n" +
          "domain_path='/shared/domain/%s' % domain_name\n" +
          "\n" +
          "# Connect to nodemanager and stop server\n" +
          "try:\n" +
          "  nmConnect(admin_username, admin_password, service_name,  '5556', domain_name, domain_path, 'plain')\n" +
          "except:\n" +
          "  print('Failed to connect to the NodeManager')\n" +
          "  exit(exitcode=2)\n" +
          "\n" +
          "# Kill the server\n" +
          "try:\n" +
          "  nmKill(server_name)\n" +
          "except:\n" +
          "  print('Connected to the NodeManager, but failed to stop the server')\n" +
          "  exit(exitcode=2)\n" +
          "\n" +
          "# Exit WLST\n" +
          "nmDisconnect()\n" +
          "exit()\n";

  private ConfigMapHelper() {}
  
  /**
   * Factory for {@link Step} that creates config map containing scripts
   * @param namespace Namespace
   * @param next Next processing step
   * @return Step for creating config map containing scripts
   */
  public static Step createScriptConfigMapStep(String namespace, Step next) {
    return new ScriptConfigMapStep(namespace, next);
  }

  private static class ScriptConfigMapStep extends Step {
    private final String namespace;
    
    public ScriptConfigMapStep(String namespace, Step next) {
      super(next);
      this.namespace = namespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      String name = KubernetesConstants.DOMAIN_CONFIG_MAP_NAME;
      V1ConfigMap cm = new V1ConfigMap();
      
      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setName(name);
      metadata.setNamespace(namespace);
      
      AnnotationHelper.annotateWithFormat(metadata);
      
      Map<String, String> labels = new HashMap<>();
      labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
      metadata.setLabels(labels);

      cm.setMetadata(metadata);

      Map<String, String> data = new HashMap<>();
      
      data.put("livenessProbe.sh", 
          "#!/bin/bash\n" + 
          "# Kubernetes periodically calls this liveness probe script to determine whether\n" + 
          "# the pod should be restarted. The script checks a WebLogic Server state file which\n" + 
          "# is updated by the node manager.\n" + 
          "DN=${DOMAIN_NAME:-$1}\n" +
          "SN=${SERVER_NAME:-$2}\n" +
          "STATEFILE=/shared/domain/${DN}/servers/${SN}/data/nodemanager/${SN}.state\n" + 
          "if [ `jps -l | grep -c \" weblogic.NodeManager\"` -eq 0 ]; then\n" + 
          "  echo \"Error: WebLogic NodeManager process not found.\"\n" +
          "  exit 1\n" + 
          "fi\n" + 
          "if [ -f ${STATEFILE} ] && [ `grep -c \"" + WebLogicConstants.FAILED_NOT_RESTARTABLE_STATE + "\" ${STATEFILE}` -eq 1 ]; then\n" + 
          "  echo \"Error: WebLogic Server state is " + WebLogicConstants.FAILED_NOT_RESTARTABLE_STATE + ".\"\n" +
          "  exit 1\n" + 
          "fi\n" + 
          "exit 0");
      
      data.put("readinessProbe.sh", 
          "#!/bin/bash\n" + 
          "\n" + 
          "# Kubernetes periodically calls this readiness probe script to determine whether\n" + 
          "# the pod should be included in load balancing. The script checks a WebLogic Server state\n" + 
          "# file which is updated by the node manager.\n" + 
          "\n" + 
          "DN=${DOMAIN_NAME:-$1}\n" +
          "SN=${SERVER_NAME:-$2}\n" +
          "STATEFILE=/shared/domain/${DN}/servers/${SN}/data/nodemanager/${SN}.state\n" + 
          "\n" + 
          "if [ `jps -l | grep -c \" weblogic.NodeManager\"` -eq 0 ]; then\n" + 
          "  echo \"Error: WebLogic NodeManager process not found.\"\n" +
          "  exit 1\n" + 
          "fi\n" + 
          "\n" + 
          "if [ ! -f ${STATEFILE} ]; then\n" + 
          "  echo \"Error: WebLogic Server state file not found.\"\n" +
          "  exit 2\n" + 
          "fi\n" + 
          "\n" + 
          "state=$(cat ${STATEFILE} | cut -f 1 -d ':')\n" +
          "if [ \"$state\" != \"" + WebLogicConstants.RUNNING_STATE + "\" ]; then\n" +
          "  echo \"" + WebLogicConstants.READINESS_PROBE_NOT_READY_STATE + "${state}\"\n" +
          "  exit 3\n" + 
          "fi\n" + 
          "exit 0");

      data.put("startServer.sh", START_SERVER_SHELL_SCRIPT);

      data.put("start-server.py", START_SERVER_PYTHON_SCRIPT);

      data.put("stopServer.sh", STOP_SERVER_SHELL_SCRIPT);

      data.put("stop-server.py", STOP_SERVER_PYTHON_SCRIPT);

      data.put("readState.sh", 
          "#!/bin/bash\n" + 
          "\n" + 
          "# Reads the current state of a server. The script checks a WebLogic Server state\n" + 
          "# file which is updated by the node manager.\n" + 
          "\n" + 
          "DN=${DOMAIN_NAME:-$1}\n" +
          "SN=${SERVER_NAME:-$2}\n" +
          "STATEFILE=/shared/domain/${DN}/servers/${SN}/data/nodemanager/${SN}.state\n" + 
          "\n" + 
          "if [ `jps -l | grep -c \" weblogic.NodeManager\"` -eq 0 ]; then\n" + 
          "  echo \"Error: WebLogic NodeManager process not found.\"\n" +
          "  exit 1\n" + 
          "fi\n" + 
          "\n" + 
          "if [ ! -f ${STATEFILE} ]; then\n" + 
          "  echo \"Error: WebLogic Server state file not found.\"\n" +
          "  exit 2\n" + 
          "fi\n" + 
          "\n" + 
          "cat ${STATEFILE} | cut -f 1 -d ':'\n" +
          "exit 0");

      cm.setData(data);
      
      CallBuilderFactory factory = ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
      Step read = factory.create().readConfigMapAsync(name, namespace, new ResponseStep<V1ConfigMap>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND) {
            return onSuccess(packet, null, statusCode, responseHeaders);
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }

        @Override
        public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (result == null) {
            Step create = factory.create().createConfigMapAsync(namespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                return super.onFailure(ScriptConfigMapStep.this, packet, e, statusCode, responseHeaders);
              }
              
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                
                LOGGER.info(MessageKeys.CM_CREATED, namespace);
                packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
                return doNext(packet);
              }
            });
            return doNext(create, packet);
          } else if (AnnotationHelper.checkFormatAnnotation(result.getMetadata()) && result.getData().entrySet().containsAll(data.entrySet())) {
            // existing config map has correct data
            LOGGER.fine(MessageKeys.CM_EXISTS, namespace);
            packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
            return doNext(packet);
          } else {
            // we need to update the config map
            Map<String, String> updated = result.getData();
            updated.putAll(data);
            cm.setData(updated);
            Step replace = factory.create().replaceConfigMapAsync(name, namespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                return super.onFailure(ScriptConfigMapStep.this, packet, e, statusCode, responseHeaders);
              }
              
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                LOGGER.info(MessageKeys.CM_REPLACED, namespace);
                packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
                return doNext(packet);
              }
            });
            return doNext(replace, packet);
          }
        }
      });
      
      return doNext(read, packet);
    }
  }

}
