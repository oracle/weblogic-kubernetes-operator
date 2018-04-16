// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
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

  private ConfigMapHelper() {}
  
  /**
   * Factory for {@link Step} that creates config map containing scripts for server instances
   * @param operatorNamespace the operator's namespace
   * @param domainNamespace the domain's namespace
   * @param next Next processing step
   * @return Step for creating config map containing scripts
   */
  public static Step createScriptConfigMapStep(String operatorNamespace, String domainNamespace, Step next) {
    return new ScriptConfigMapStep(operatorNamespace, domainNamespace, next);
  }

  // Make this public so that it can be unit tested
  public static class ScriptConfigMapStep extends Step {
    private final String operatorNamespace;
    private final String domainNamespace;
    
    public ScriptConfigMapStep(String operatorNamespace, String domainNamespace, Step next) {
      super(next);
      this.operatorNamespace = operatorNamespace;
      this.domainNamespace = domainNamespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      V1ConfigMap cm = computeDomainConfigMap();
      CallBuilderFactory factory = ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
      Step read = factory.create().readConfigMapAsync(cm.getMetadata().getName(), domainNamespace, new ResponseStep<V1ConfigMap>(next) {
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
            Step create = factory.create().createConfigMapAsync(domainNamespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                return super.onFailure(ScriptConfigMapStep.this, packet, e, statusCode, responseHeaders);
              }
              
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                
                LOGGER.info(MessageKeys.CM_CREATED, KubernetesConstants.DOMAIN_CONFIG_MAP_NAME, domainNamespace);
                packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
                return doNext(packet);
              }
            });
            return doNext(create, packet);
          } else if (AnnotationHelper.checkFormatAnnotation(result.getMetadata()) && result.getData().entrySet().containsAll(cm.getData().entrySet())) {
            // existing config map has correct data
            LOGGER.fine(MessageKeys.CM_EXISTS, KubernetesConstants.DOMAIN_CONFIG_MAP_NAME, domainNamespace);
            packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
            return doNext(packet);
          } else {
            // we need to update the config map
            Map<String, String> updated = result.getData();
            updated.putAll(cm.getData());
            cm.setData(updated);
            Step replace = factory.create().replaceConfigMapAsync(cm.getMetadata().getName(), domainNamespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                return super.onFailure(ScriptConfigMapStep.this, packet, e, statusCode, responseHeaders);
              }
              
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                LOGGER.info(MessageKeys.CM_REPLACED, KubernetesConstants.DOMAIN_CONFIG_MAP_NAME, domainNamespace);
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

    // Make this protected so that it can be unit tested
    protected V1ConfigMap computeDomainConfigMap() {
      String name = KubernetesConstants.DOMAIN_CONFIG_MAP_NAME;
      V1ConfigMap cm = new V1ConfigMap();
      cm.setApiVersion("v1");
      cm.setKind("ConfigMap");

      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setName(name);
      metadata.setNamespace(domainNamespace);
      
      AnnotationHelper.annotateWithFormat(metadata);
      
      Map<String, String> labels = new HashMap<>();
      labels.put(LabelConstants.OPERATORNAME_LABEL, operatorNamespace);
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

      return cm;
    }
  }

  /**
   * Factory for {@link Step} that creates config map containing scripts for domain home creation
   * @param operatorNamespace the operator's namespace
   * @param domainNamespace the domain's namespace
   * @param next Next processing step
   * @return Step for creating config map containing scripts
   */
  public static Step createDomainHomeConfigMapStep(String operatorNamespace, String domainNamespace, Step next) {
    return new DomainHomeConfigMapStep(operatorNamespace, domainNamespace, next);
  }

  // Make this public so that it can be unit tested
  public static class DomainHomeConfigMapStep extends Step {
    private final String operatorNamespace;
    private final String domainNamespace;
    
    public DomainHomeConfigMapStep(String operatorNamespace, String domainNamespace, Step next) {
      super(next);
      this.operatorNamespace = operatorNamespace;
      this.domainNamespace = domainNamespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      V1ConfigMap cm;
      try {
        cm = computeDomainHomeConfigMap();
      } catch (IOException e1) {
        return doTerminate(e1, packet);
      }

      CallBuilderFactory factory = ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
      Step read = factory.create().readConfigMapAsync(cm.getMetadata().getName(), domainNamespace, new ResponseStep<V1ConfigMap>(next) {
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
            Step create = factory.create().createConfigMapAsync(domainNamespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                return super.onFailure(DomainHomeConfigMapStep.this, packet, e, statusCode, responseHeaders);
              }
              
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                
                LOGGER.info(MessageKeys.CM_CREATED, KubernetesConstants.DOMAIN_HOME_CONFIG_MAP_NAME, domainNamespace);
                packet.put(ProcessingConstants.DOMAIN_HOME_CONFIG_MAP, result);
                return doNext(packet);
              }
            });
            return doNext(create, packet);
          } else if (AnnotationHelper.checkFormatAnnotation(result.getMetadata()) && result.getData().entrySet().containsAll(cm.getData().entrySet())) {
            // existing config map has correct data
            LOGGER.fine(MessageKeys.CM_EXISTS, KubernetesConstants.DOMAIN_HOME_CONFIG_MAP_NAME, domainNamespace);
            packet.put(ProcessingConstants.DOMAIN_HOME_CONFIG_MAP, result);
            return doNext(packet);
          } else {
            // we need to update the config map
            Map<String, String> updated = result.getData();
            updated.putAll(cm.getData());
            cm.setData(updated);
            Step replace = factory.create().replaceConfigMapAsync(cm.getMetadata().getName(), domainNamespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                return super.onFailure(DomainHomeConfigMapStep.this, packet, e, statusCode, responseHeaders);
              }
              
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                LOGGER.info(MessageKeys.CM_REPLACED, KubernetesConstants.DOMAIN_CONFIG_MAP_NAME, domainNamespace);
                packet.put(ProcessingConstants.DOMAIN_HOME_CONFIG_MAP, result);
                return doNext(packet);
              }
            });
            return doNext(replace, packet);
          }
        }
      });
      
      return doNext(read, packet);
    }

    // Make this protected so that it can be unit tested
    protected V1ConfigMap computeDomainHomeConfigMap() throws IOException {
      String name = KubernetesConstants.DOMAIN_HOME_CONFIG_MAP_NAME;
      V1ConfigMap cm = new V1ConfigMap();
      cm.setApiVersion("v1");
      cm.setKind("ConfigMap");

      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setName(name);
      metadata.setNamespace(domainNamespace);
      
      AnnotationHelper.annotateWithFormat(metadata);
      
      Map<String, String> labels = new HashMap<>();
      labels.put(LabelConstants.OPERATORNAME_LABEL, operatorNamespace);
      labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
      metadata.setLabels(labels);

      cm.setMetadata(metadata);

      Map<String, String> data = new HashMap<>();
      
      // HERE
      data.put("weblogic-deploy.zip.base64", toBase64("/operator/weblogic-deploy.zip"));

      data.put("utility.sh", 
          "#!/bin/bash\n" + 
          "#\n" + 
          "\n" + 
          "#\n" + 
          "# Report an error and fail the job\n" + 
          "# $1 - text of error\n" + 
          "function fail {\n" + 
          "  echo ERROR: $1\n" + 
          "  exit 1\n" + 
          "}\n" + 
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
          "#\n" + 
          "# Check a file exists\n" + 
          "# $1 - path of file to check\n" + 
          "function checkFileExists {\n" + 
          "  if [ ! -f $1 ]; then\n" + 
          "    fail \"The file $1 does not exist\"\n" + 
          "  fi\n" + 
          "}\n");
      
      data.put("create-domain-job.sh", 
          "#!/bin/bash\n" + 
          "#\n" + 
          "\n" + 
          "# Include common utility functions\n" + 
          "source /u01/weblogic/utility.sh\n" + 
          "\n" + 
          "# Verify the script to create the domain exists\n" + 
          "script='/u01/weblogic/create-domain-script.sh'\n" + 
          "if [ -f $script ]; then\n" + 
          "  echo The domain will be created using the script $script\n" + 
          "else\n" + 
          "  fail \"Could not locate the domain creation script ${script}\"\n" + 
          "fi\n" + 
          "\n" + 
          "# Validate the domain secrets exist before proceeding.\n" + 
          "if [ ! -f /weblogic-operator/secrets/username ]; then\n" + 
          "  fail \"The domain secret /weblogic-operator/secrets/username was not found\"\n" + 
          "fi\n" + 
          "if [ ! -f /weblogic-operator/secrets/password ]; then\n" + 
          "  fail \"The domain secret /weblogic-operator/secrets/password was not found\"\n" + 
          "fi\n" + 
          "\n" + 
          "# Do not proceed if the domain already exists\n" + 
          "domainFolder=${SHARED_PATH}/domain/%DOMAIN_NAME%\n" + 
          "if [ -d ${domainFolder} ]; then\n" + 
          "  fail \"The create domain job will not overwrite an existing domain. The domain folder ${domainFolder} already exists\"\n" + 
          "fi\n" + 
          "\n" + 
          "# Create the base folders\n" + 
          "createFolder ${SHARED_PATH}/domain\n" + 
          "createFolder ${SHARED_PATH}/applications\n" + 
          "createFolder ${SHARED_PATH}/logs\n" + 
          "createFolder ${SHARED_PATH}/stores\n" + 
          "\n" + 
          "# Execute the script to create the domain\n" + 
          "source $script\n");
      
      data.put("read-domain-secret.py", 
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
          "#\n");
      
      data.put("create-domain-script.sh", 
          "#!/bin/bash\n" + 
          "#\n" + 
          "\n" + 
          "# Include common utility functions\n" + 
          "source /u01/weblogic/utility.sh\n" + 
          "\n" + 
          "export DOMAIN_HOME=${SHARED_PATH}/domain/%DOMAIN_NAME%\n" + 
          "\n" + 
          "# Function to create node manager home for a server\n" + 
          "# $1 - Domain UID\n" + 
          "# $2 - Server Name\n" + 
          "# $3 - Admin Server Hostname (only passed for managed servers)\n" + 
          "function createNodeMgrHome() {\n" + 
          "\n" + 
          "  # Create startup.properties file\n" + 
          "  datadir=${DOMAIN_HOME}/servers/$2/data/nodemanager\n" + 
          "  startProp=${datadir}/startup.properties\n" + 
          "  createFolder ${datadir}\n" + 
          "  echo \"# Server startup properties\" > ${startProp}\n" + 
          "  echo \"AutoRestart=true\" >> ${startProp}\n" + 
          "  if [ -n \"$3\" ]; then\n" + 
          "    echo \"AdminURL=http\\://$3\\:%ADMIN_PORT%\" >> ${startProp}\n" + 
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
          "\n" + 
          "  # Create nodemanager home for the server\n" + 
          "  nmdir=${DOMAIN_HOME}/servers/$2/nodemgr_home\n" + 
          "  createFolder ${nmdir}\n" + 
          "  prop=${nmdir}/nodemanager.properties\n" + 
          "  cp ${DOMAIN_HOME}/nodemanager/nodemanager.properties ${nmdir}\n" + 
          "  cp ${DOMAIN_HOME}/nodemanager/nodemanager.domains ${nmdir}\n" + 
          "  cp ${DOMAIN_HOME}/bin/startNodeManager.sh ${nmdir}\n" + 
          "\n" + 
          "  # Edit the start nodemanager script to use the home for the server\n" + 
          "  sed -i -e \"s:/nodemanager:/servers/$2/nodemgr_home:g\" ${nmdir}/startNodeManager.sh\n" + 
          "\n" + 
          "  # Edit the nodemanager properties file to use the home for the server\n" + 
          "  sed -i -e \"s:DomainsFile=.*:DomainsFile=${nmdir}/nodemanager.domains:g\" ${prop}\n" + 
          "  sed -i -e \"s:NodeManagerHome=.*:NodeManagerHome=${nmdir}:g\" ${prop}\n" + 
          "  sed -i -e \"s:ListenAddress=.*:ListenAddress=$1-$2:g\" ${prop}\n" + 
          "  sed -i -e \"s:LogFile=.*:LogFile=/shared/logs/nodemanager-$2.log:g\" ${prop}\n" + 
          "\n" + 
          "}\n" + 
          "\n" + 
          "# Function to create script for starting a server\n" + 
          "# $1 - Domain UID\n" + 
          "# $2 - Server Name\n" + 
          "# $3 - Flag (only passed for admin server)\n" + 
          "function createStartScript() {\n" + 
          "\n" + 
          "  nmdir=${DOMAIN_HOME}/servers/$2/nodemgr_home\n" + 
          "  stateFile=${DOMAIN_HOME}/servers/$2/data/nodemanager/$2.state\n" + 
          "  scriptFile=${nmdir}/startServer.sh\n" + 
          "  pyFile=${nmdir}/start-server.py\n" + 
          "  argsFile=${nmdir}/set-ms-args.py\n" + 
          "\n" + 
          "  # Create a script that starts the node manager, then uses wlst to connect\n" + 
          "  # to the nodemanager and start the server.\n" + 
          "  # The script and 'EOF' on the following lines must not be indented!\n" + 
          "  cat << EOF > ${scriptFile}\n" + 
          "#!/bin/bash\n" + 
          "\n" + 
          "# Base64 decode binary support files\n" + 
          "mkdir -p /tmp/weblogic-operator/bin\n" + 
          "shopt -s nullglob\n" + 
          "for f in /weblogic-operator/scripts/*.base64; do\n" + 
          "  cat \"\\$f\" | base64 --decode > /tmp/weblogic-operator/bin/\\$(basename \"\\$f\" .base64)\n" + 
          "done\n" + 
          "\n" + 
          "# Check for stale state file and remove if found\"\n" + 
          "if [ -f ${stateFile} ]; then\n" + 
          "  echo \"Removing stale file ${stateFile}\"\n" + 
          "  rm ${stateFile}\n" + 
          "fi\n" + 
          "\n" + 
          "echo \"Start the nodemanager\"\n" + 
          ". ${nmdir}/startNodeManager.sh &\n" + 
          "\n" + 
          "echo \"Allow the nodemanager some time to start before attempting to connect\"\n" + 
          "sleep 15\n" + 
          "echo \"Finished waiting for the nodemanager to start\"\n" + 
          "\n" + 
          "echo \"Update JVM arguments\"\n" + 
          "if [ $# -eq 3 ]\n" + 
          "then\n" + 
          "  echo \"Update JVM arguments for admin server\"\n" + 
          "  echo \"Arguments=\\${USER_MEM_ARGS} -XX\\:+UnlockExperimentalVMOptions -XX\\:+UseCGroupMemoryLimitForHeap \\${JAVA_OPTIONS}\" >> ${startProp}\n" + 
          "else\n" + 
          "  echo \"Update JVM arguments for managed server\"\n" + 
          "  wlst.sh ${argsFile} $1 $2 ${startProp}\n" + 
          "fi\n" + 
          "\n" + 
          "echo \"Start the server\"\n" + 
          "wlst.sh -skipWLSModuleScanning ${pyFile}\n" + 
          "\n" + 
          "echo \"Wait indefinitely so that the Kubernetes pod does not exit and try to restart\"\n" + 
          "while true; do sleep 60; done\n" + 
          "EOF\n" + 
          "\n" + 
          "  checkFileExists ${scriptFile}\n" + 
          "  chmod +x ${scriptFile}\n" + 
          "\n" + 
          "  # Create a python script to execute the wlst commands.\n" + 
          "  # The script and 'EOF' on the following lines must not be indented!\n" + 
          "  cat /u01/weblogic/read-domain-secret.py > ${pyFile}\n" + 
          "  cat << EOF >> ${pyFile}\n" + 
          "\n" + 
          "# Connect to nodemanager and start server\n" + 
          "nmConnect(admin_username, admin_password, '$1-$2',  '5556', '%DOMAIN_NAME%', '${DOMAIN_HOME}', 'plain')\n" + 
          "nmStart('$2')\n" + 
          "\n" + 
          "# Exit WLST\n" + 
          "nmDisconnect()\n" + 
          "exit()\n" + 
          "EOF\n" + 
          "\n" + 
          "  checkFileExists ${pyFile}\n" + 
          "\n" + 
          "  # Create a python script to set JVM arguments for managed server.\n" + 
          "  # The script and 'EOF' on the following lines must not be indented!\n" + 
          "  cat << EOF > ${argsFile}\n" + 
          "\n" + 
          "import os\n" + 
          "import sys\n" + 
          "EOF\n" + 
          "\n" + 
          "  cat /u01/weblogic/read-domain-secret.py >> ${argsFile}\n" + 
          "  cat << EOF >> ${argsFile}\n" + 
          "\n" + 
          "mem_args=os.environ['USER_MEM_ARGS']\n" + 
          "java_opt=os.environ['JAVA_OPTIONS']\n" + 
          "admin_server=os.environ['ADMIN_NAME']\n" + 
          "admin_port=os.environ['ADMIN_PORT']\n" + 
          "\n" + 
          "domain_UID=sys.argv[1]\n" + 
          "server_name=sys.argv[2]\n" + 
          "startup_file=sys.argv[3]\n" + 
          "\n" + 
          "adminUrl='t3://' + domain_UID + '-' + admin_server + ':' + admin_port\n" + 
          "dirStr='Servers/managed-server1/ServerStart/' + server_name\n" + 
          "\n" + 
          "# Connect to admin server to get startup arguments of this server\n" + 
          "connect(admin_username, admin_password, adminUrl)\n" + 
          "cd(dirStr)\n" + 
          "args=get('Arguments')\n" + 
          "disconnect()\n" + 
          "\n" + 
          "f = open(startup_file, 'a')\n" + 
          "s=str(\"Arguments=\"+ mem_args + \" -XX\\:+UnlockExperimentalVMOptions -XX\\:+UseCGroupMemoryLimitForHeap \" + java_opt )\n" + 
          "if not (args is None):\n" + 
          "  s=str(s + \" \" + args + \"\\n\")\n" + 
          "else:\n" + 
          "  s=str(s +  \"\\n\")\n" + 
          "\n" + 
          "f.write(s)\n" + 
          "f.close()\n" + 
          "EOF\n" + 
          "\n" + 
          "  checkFileExists ${argsFile}\n" + 
          "\n" + 
          "}\n" + 
          "\n" + 
          "# Function to create script for stopping a server\n" + 
          "# $1 - Domain UID\n" + 
          "# $2 - Server Name\n" + 
          "function createStopScript() {\n" + 
          "\n" + 
          "  nmdir=${DOMAIN_HOME}/servers/$2/nodemgr_home\n" + 
          "  scriptFile=${nmdir}/stopServer.sh\n" + 
          "  pyFile=${nmdir}/stop-server.py\n" + 
          "\n" + 
          "  # Create a script that stops the server.\n" + 
          "  # The script and 'EOF' on the following lines must not be indented!\n" + 
          "  cat << EOF > ${scriptFile}\n" + 
          "#!/bin/bash\n" + 
          "\n" + 
          "echo \"Stop the server\"\n" + 
          "wlst.sh -skipWLSModuleScanning ${pyFile}\n" + 
          "\n" + 
          "# Return status of 2 means failed to stop a server through the NodeManager.\n" + 
          "# Look to see if there is a server process that can be killed.\n" + 
          "if [ \\$? -eq 2 ]; then\n" + 
          "  pid=\\$(jps -v | grep '[D]weblogic.Name=$2' | awk '{print \\$1}')\n" + 
          "  if [ ! -z \\$pid ]; then\n" + 
          "    echo \"Killing the server process \\$pid\"\n" + 
          "    kill -15 \\$pid\n" + 
          "  fi\n" + 
          "fi\n" + 
          "\n" + 
          "EOF\n" + 
          "\n" + 
          "  checkFileExists ${scriptFile}\n" + 
          "  chmod +x ${scriptFile}\n" + 
          "\n" + 
          "  # Create a python script to execute the wlst commands.\n" + 
          "  # The script and 'EOF' on the following lines must not be indented!\n" + 
          "  cat /u01/weblogic/read-domain-secret.py > ${pyFile}\n" + 
          "  cat << EOF >> ${pyFile}\n" + 
          "\n" + 
          "# Connect to nodemanager and stop server\n" + 
          "try:\n" + 
          "  nmConnect(admin_username, admin_password, '$1-$2',  '5556', '%DOMAIN_NAME%', '${DOMAIN_HOME}', 'plain')\n" + 
          "except:\n" + 
          "  print('Failed to connect to the NodeManager')\n" + 
          "  exit(exitcode=2)\n" + 
          "\n" + 
          "# Kill the server\n" + 
          "try:\n" + 
          "  nmKill('$2')\n" + 
          "except:\n" + 
          "  print('Connected to the NodeManager, but failed to stop the server')\n" + 
          "  exit(exitcode=2)\n" + 
          "\n" + 
          "# Exit WLST\n" + 
          "nmDisconnect()\n" + 
          "exit()\n" + 
          "EOF\n" + 
          "}\n" + 
          "\n" + 
          "checkFileExists ${pyFile}\n" + 
          "\n" + 
          "# Create the domain\n" + 
          "wlst.sh -skipWLSModuleScanning /u01/weblogic/create-domain.py\n" + 
          "\n" + 
          "# Setup admin server\n" + 
          "createNodeMgrHome %DOMAIN_UID% %ADMIN_SERVER_NAME%\n" + 
          "createStartScript %DOMAIN_UID% %ADMIN_SERVER_NAME% 'admin'\n" + 
          "createStopScript  %DOMAIN_UID% %ADMIN_SERVER_NAME%\n" + 
          "\n" + 
          "# Create the managed servers\n" + 
          "index=0\n" + 
          "while [ $index -lt %CONFIGURED_MANAGED_SERVER_COUNT% ]\n" + 
          "do\n" + 
          "  ((index++))\n" + 
          "  createNodeMgrHome %DOMAIN_UID% %MANAGED_SERVER_NAME_BASE%${index} %DOMAIN_UID%-%ADMIN_SERVER_NAME%\n" + 
          "  createStartScript %DOMAIN_UID% %MANAGED_SERVER_NAME_BASE%${index}\n" + 
          "  createStopScript  %DOMAIN_UID% %MANAGED_SERVER_NAME_BASE%${index}\n" + 
          "done\n" + 
          "\n" + 
          "echo \"Successfully Completed\"\n");
      
      data.put("create-domain.py", 
          "# This python script is used to create a WebLogic domain\n" + 
          "\n" + 
          "# Read the domain secrets from the common python file\n" + 
          "execfile(\"/u01/weblogic/read-domain-secret.py\")\n" + 
          "\n" + 
          "server_port        = %MANAGED_SERVER_PORT%\n" + 
          "domain_path        = os.environ.get(\"DOMAIN_HOME\")\n" + 
          "cluster_name       = \"%CLUSTER_NAME%\"\n" + 
          "number_of_ms       = %CONFIGURED_MANAGED_SERVER_COUNT%\n" + 
          "\n" + 
          "print('domain_path        : [%s]' % domain_path);\n" + 
          "print('domain_name        : [%DOMAIN_NAME%]');\n" + 
          "print('admin_username     : [%s]' % admin_username);\n" + 
          "print('admin_port         : [%ADMIN_PORT%]');\n" + 
          "print('cluster_name       : [%s]' % cluster_name);\n" + 
          "print('server_port        : [%s]' % server_port);\n" + 
          "\n" + 
          "# Open default domain template\n" + 
          "# ============================\n" + 
          "readTemplate(\"/u01/oracle/wlserver/common/templates/wls/wls.jar\")\n" + 
          "\n" + 
          "set('Name', '%DOMAIN_NAME%')\n" + 
          "setOption('DomainName', '%DOMAIN_NAME%')\n" + 
          "create('%DOMAIN_NAME%','Log')\n" + 
          "cd('/Log/%DOMAIN_NAME%');\n" + 
          "set('FileName', '/shared/logs/%DOMAIN_NAME%.log')\n" + 
          "\n" + 
          "# Configure the Administration Server\n" + 
          "# ===================================\n" + 
          "cd('/Servers/AdminServer')\n" + 
          "set('ListenAddress', '%DOMAIN_UID%-%ADMIN_SERVER_NAME%')\n" + 
          "set('ListenPort', %ADMIN_PORT%)\n" + 
          "set('Name', '%ADMIN_SERVER_NAME%')\n" + 
          "\n" + 
          "create('T3Channel', 'NetworkAccessPoint')\n" + 
          "cd('/Servers/%ADMIN_SERVER_NAME%/NetworkAccessPoints/T3Channel')\n" + 
          "set('PublicPort', %T3_CHANNEL_PORT%)\n" + 
          "set('PublicAddress', '%T3_PUBLIC_ADDRESS%')\n" + 
          "set('ListenAddress', '%DOMAIN_UID%-%ADMIN_SERVER_NAME%')\n" + 
          "set('ListenPort', %T3_CHANNEL_PORT%)\n" + 
          "\n" + 
          "cd('/Servers/%ADMIN_SERVER_NAME%')\n" + 
          "create('%ADMIN_SERVER_NAME%', 'Log')\n" + 
          "cd('/Servers/%ADMIN_SERVER_NAME%/Log/%ADMIN_SERVER_NAME%')\n" + 
          "set('FileName', '/shared/logs/%ADMIN_SERVER_NAME%.log')\n" + 
          "\n" + 
          "# Set the admin user's username and password\n" + 
          "# ==========================================\n" + 
          "cd('/Security/%DOMAIN_NAME%/User/weblogic')\n" + 
          "cmo.setName(admin_username)\n" + 
          "cmo.setPassword(admin_password)\n" + 
          "\n" + 
          "# Write the domain and close the domain template\n" + 
          "# ==============================================\n" + 
          "setOption('OverwriteDomain', 'true')\n" + 
          "\n" + 
          "# Configure the node manager\n" + 
          "# ==========================\n" + 
          "cd('/NMProperties')\n" + 
          "set('ListenAddress','0.0.0.0')\n" + 
          "set('ListenPort',5556)\n" + 
          "set('CrashRecoveryEnabled', 'true')\n" + 
          "set('NativeVersionEnabled', 'true')\n" + 
          "set('StartScriptEnabled', 'false')\n" + 
          "set('SecureListener', 'false')\n" + 
          "set('LogLevel', 'FINEST')\n" + 
          "set('DomainsDirRemoteSharingEnabled', 'true')\n" + 
          "\n" + 
          "# Set the Node Manager user name and password (domain name will change after writeDomain)\n" + 
          "cd('/SecurityConfiguration/base_domain')\n" + 
          "set('NodeManagerUsername', admin_username)\n" + 
          "set('NodeManagerPasswordEncrypted', admin_password)\n" + 
          "\n" + 
          "# Create a cluster\n" + 
          "cd('/')\n" + 
          "create(cluster_name, 'Cluster')\n" + 
          "\n" + 
          "# Create managed servers\n" + 
          "for index in range(0, number_of_ms):\n" + 
          "  cd('/')\n" + 
          "\n" + 
          "  msIndex = index+1\n" + 
          "  name = '%MANAGED_SERVER_NAME_BASE%%s' % msIndex\n" + 
          "\n" + 
          "  create(name, 'Server')\n" + 
          "  cd('/Servers/%s/' % name )\n" + 
          "  print('managed server name is %s' % name);\n" + 
          "  set('ListenAddress', '%DOMAIN_UID%-%s' % name)\n" + 
          "  set('ListenPort', server_port)\n" + 
          "  set('NumOfRetriesBeforeMSIMode', 0)\n" + 
          "  set('RetryIntervalBeforeMSIMode', 1)\n" + 
          "  set('Cluster', cluster_name)\n" + 
          "\n" + 
          "  create(name,'Log')\n" + 
          "  cd('/Servers/%s/Log/%s' % (name, name))\n" + 
          "  set('FileName', '/shared/logs/%s.log' % name)\n" + 
          "\n" + 
          "# Write Domain\n" + 
          "# ============\n" + 
          "writeDomain(domain_path)\n" + 
          "closeTemplate()\n" + 
          "print 'Domain Created'\n" + 
          "\n" + 
          "# Update Domain\n" + 
          "readDomain(domain_path)\n" + 
          "cd('/')\n" + 
          "cmo.setProductionModeEnabled(%PRODUCTION_MODE_ENABLED%)\n" + 
          "updateDomain()\n" + 
          "closeDomain()\n" + 
          "print 'Domain Updated'\n" + 
          "\n" + 
          "# Encrypt the admin username and password\n" + 
          "adminUsernameEncrypted=encrypt(admin_username, domain_path)\n" + 
          "adminPasswordEncrypted=encrypt(admin_password, domain_path)\n" + 
          "\n" + 
          "print 'Create boot.properties files for admin and managed servers'\n" + 
          "\n" + 
          "asbpFile=open('%s/servers/%ADMIN_SERVER_NAME%/security/boot.properties' % domain_path, 'w+')\n" + 
          "asbpFile.write(\"username=%s\\n\" % adminUsernameEncrypted)\n" + 
          "asbpFile.write(\"password=%s\\n\" % adminPasswordEncrypted)\n" + 
          "asbpFile.close()\n" + 
          "\n" + 
          "import os\n" + 
          "\n" + 
          "# Create boot.properties file for each managed server\n" + 
          "for index in range(0, number_of_ms):\n" + 
          "\n" + 
          "  # Define the folder path\n" + 
          "  secdir='%s/servers/%MANAGED_SERVER_NAME_BASE%%s/security' % (domain_path, index+1)\n" + 
          "\n" + 
          "  # Create the security folder (if it does not already exist)\n" + 
          "  try:\n" + 
          "    os.makedirs(secdir)\n" + 
          "  except OSError:\n" + 
          "    if not os.path.isdir(secdir):\n" + 
          "      raise\n" + 
          "\n" + 
          "  bpFile=open('%s/boot.properties' % secdir, 'w+')\n" + 
          "  bpFile.write(\"username=%s\\n\" % adminUsernameEncrypted)\n" + 
          "  bpFile.write(\"password=%s\\n\" % adminPasswordEncrypted)\n" + 
          "  bpFile.close()\n" + 
          "\n" + 
          "print 'Done'\n" + 
          "\n" + 
          "# Exit WLST\n" + 
          "# =========\n" + 
          "exit()\n");
      
      cm.setData(data);

      return cm;
    }
  }

  private static String toBase64(String fileName) throws IOException {
    File file = new File(fileName);
    byte[] encoded = Base64.getEncoder().encode(Files.readAllBytes(file.toPath()));
    return new String(encoded, StandardCharsets.UTF_8);
  }
}
