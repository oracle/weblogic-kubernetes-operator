How to use the shell script in this dir to build the archive file in the admin pod and deploy it to a weblogic target from the admin pod:

  Web App
     1) Create a directory structure for packaging WAR
        
        integration-tests/src/test/resources/apps/
          testAppName/
            foo.java
            WEB-INF/
              weblogic.xml  
              web.xml
        
     2) In the standalone client java file, call:
     
        String scriptName = "buildDeployAppInPod.sh";
        domain.buildDeployJavaAppInPod(testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword());
            Create dir in the admon pod to save Web App files 
            Copy the shell script file and all App files over to the admin pod
            Run the shell script to build WAR and deploy the App from the admin pod to the given webligic target
            
     3) testAppName.war file is created at "/u01/oracle/apps/testAppName" in the admin pod
  
  EJB App
     1) Create a directory structure for packaging JAR
        
        integration-tests/src/test/resources/apps/
          testAppName/
            foo.java
            fooHome.java
            fooBean.java
            META-INF/
              ejb-jar.xml
              weblogic-ejb-jar.xml
              weblogic-cmp-jar.xml
              
    2) In the standalone client java file, call:
     
        String scriptName = "buildDeployAppInPod.sh";
        domain.buildDeployJavaAppInPod(testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword());
            Create dir in the admon pod to save Web App files 
            Copy the shell script file and all App files over to the admin pod
            Run the shell script to build WAR and deploy the App from the admin pod to the given webligic target
            
        or to build a JAR file:
        
        domain.buildDeployJavaAppInPod(testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword(), "jar");
            
     3) testAppName.jar file is created at "/u01/oracle/apps/testAppName" in the admin pod
     
  sh buildDeployAppInPod.sh node-hostname node-port username password dir-in-pod-to-save-app-files appname deploy-target
      Create directories to save the binaries  
      Complie java files
      Create WAR/EAR/JAR
      Deploy the App from the admin pod to the given webligic target
