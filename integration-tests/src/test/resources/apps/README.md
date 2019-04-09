## How to use the shell script in this dir to build the archive file in the admin pod and deploy it to a weblogic target from the admin pod:

## Web App
1) Create a directory structure for packaging WAR
        
        integration-tests/src/test/resources/apps/
          testAppName/
            foo.java
            WEB-INF/
              weblogic.xml  
              web.xml
        
2) In the standalone client java file, call:
     
        String scriptName = `buildDeployAppInPod.sh`;
        domain.buildDeployJavaAppInPod(testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword());
            a. Create dir in the admin pod to save Web App files 
            b. Copy the shell script file and all App files over to the admin pod
            c. Run the shell script to build WAR and deploy the App from the admin pod to the given weblogic target
            
3) testAppName.war file is created at `/u01/oracle/apps/testAppName` in the admin pod
  
## EJB App
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
            a. Create dir in the admon pod to save Web App files 
            b. Copy the shell script file and all App files over to the admin pod
            c. Run the shell script to build WAR and deploy the App from the admin pod to the given webligic target
            
      or to build a JAR file:
        
       domain.buildDeployJavaAppInPod(testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword(), "jar");
            
3) testAppName.jar file is created at `/u01/oracle/apps/testAppName` in the admin pod
     
       sh buildDeployAppInPod.sh node-hostname node-port username password dir-in-pod-to-save-app-files appname deploy-target
      
          a. Create directories to save the binaries  
          b. Compile java files
          c. Create WAR/EAR/JAR
          d. Deploy the App from the admin pod to the given weblogic target

## Web Service App
1) Create a directory structure for packaging Web Service WAR.
      Use as example files build.xml, web.xml, weblogic_temp.xml from `integration-tests/src/test/resources/apps/testwsapp` and modify [wsName]
      
       integration-tests/src/test/resources/apps/
        [appName]/
          [wsName].java
          [wsName]Servlet.java
          build.xml
          WEB-INF/
            weblogic_temp.xml
            web.xml

      for example: appName = testwsapp, wsName = TestWSApp

       integration-tests/src/test/resources/apps/
          testwsapp/
            TestWSApp.java
            TestWSAppServlet.java
            build.xml
            WEB-INF/
              weblogic_temp.xml
              web.xml

2) In the standalone client java file, call:

        String scriptName = `buildDeployWSAndWSClientAppInPod.sh`;
        TestUtils.buildDeployWebServiceAppInPod(domain, `[appName]`, scriptName, BaseTest.getUsername(), BaseTest.getPassword());
         
    With no extra variables it will use `TestWSApp` Web Service. To customize Web Service name, pass extra argument to the script with desired `[wsName]` value.
          
          TestUtils.buildDeployWebServiceAppInPod(domain, testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword(), `[wsName]`);

            a. Create dir in the admin pod to save WS and WS Client Apps files
            b. Copy the shell script file and all App files over to the admin pod
            c. Run the shell script to build WAR and deploy the App from the admin pod to the given weblogic target

3) `[wsName].war` and `[wsName]Servlet.war` files are created at `/u01/oracle/apps/[appName]` in the admin pod
     
       sh buildDeployWSAndWSClientAppInPod.sh node-hostname node-port username password dir-in-pod-to-save-app-files appname deploy-target clusterurl wsname
        
      for example: for appName=testwsapp, wsName=TestWSApp:
           
          buildDeployWSAndWSClientAppInPod.sh slc13kef.us.oracle.com 30701 weblogic welcome1 /u01/oracle/apps/testwsapp testwsapp cluster-1 10.111.78.193:8001 TestWSApp
        
          a. Create directories to save the binaries
          b. Compile java files ( TestWSApp.java, TestWSAppServlet.java)
          c. Create WARs (TestWSApp.war, TestWSAppServlet.war)
          d. Deploy the WS and Client Apps from the admin pod to the given weblogic target

