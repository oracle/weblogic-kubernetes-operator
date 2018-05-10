package oracle.kubernetes.operator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.PersistentVolume;
import oracle.kubernetes.operator.utils.Secret;
import oracle.kubernetes.operator.utils.TestUtils;

/**
 * Base class which contains common methods to create/shutdown operator and domain. IT tests can
 * extend this class.
 *
 * @author Vanajakshi Mukkara
 */
public class BaseTest {
  protected static final String TESTWEBAPP = "testwebapp";

  public static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  protected static Operator operator;
  protected static Domain domain;
  protected static PersistentVolume pv;
  protected static Secret secret;

  protected static String username = "weblogic";
  protected static String password = "welcome1";

  protected static String resultRoot = "";
  protected static String pvRoot = "";
  protected static String resultDir = "";
  protected static String userProjectsDir = "";
  protected static String pvDir = "";

  protected static String opPropsFile = "";
  protected static String domainPropsFile = "";
  protected static String appPropsFile = "";
  protected static Properties opProps = new Properties();
  protected static Properties domainProps = new Properties();
  protected static Properties appProps = new Properties();

  protected static String operatorNS = "";
  protected static String domainUid = "";
  protected static String domainNS = "";

  public static void setup() throws Exception {
    boolean createOpDomain = true; //this flag will be removed later, its here for testing

    //check file exists
    File f = new File(BaseTest.class.getClassLoader().getResource(appPropsFile).getFile());
    if (!f.exists()) {
      throw new IllegalArgumentException(
          "FAILURE: Invalid operator appp properties file " + appPropsFile);
    }

    //load props
    FileInputStream inStream = new FileInputStream(f);
    appProps.load(inStream);
    inStream.close();
    String baseDir = appProps.getProperty("baseDir");
    if (baseDir == null) {
      throw new IllegalArgumentException("FAILURE: baseDir is not set");
    }

    //PV dir in domain props is ignored
    resultRoot = baseDir + "/" + System.getProperty("user.name") + "/wl_k8s_test_results";
    resultDir = resultRoot + "/acceptance_test_tmp";

    userProjectsDir = resultDir + "/user-projects";
    pvRoot = resultRoot;

    //create resultRoot, PVRoot, etc
    if (createOpDomain) {
      createDirectories();
    }

    //check file exists
    f = new File(BaseTest.class.getClassLoader().getResource(opPropsFile).getFile());
    if (!f.exists()) {
      throw new IllegalArgumentException(
          "FAILURE: Invalid operator input properties file " + opPropsFile);
    }

    //load props
    inStream = new FileInputStream(f);
    opProps.load(inStream);
    inStream.close();
    operatorNS = opProps.getProperty("namespace");

    //create op
    operator = new Operator(opProps, userProjectsDir);

    if (createOpDomain) {
      if (!operator.run()) {
        throw new RuntimeException("FAILURE: Create Operator Script failed..");
      }
    }

    logger.info("Check Operator status");
    operator.verifyPodCreated();
    operator.verifyOperatorReady();
    operator.verifyExternalRESTService();

    //check domain props file exists
    f = new File(ITSingleDomain.class.getClassLoader().getResource(domainPropsFile).getFile());
    if (!f.exists()) {
      throw new IllegalArgumentException(
          "FAILURE: Invalid domain input properties file" + domainPropsFile);
    }
    //load props
    inStream = new FileInputStream(f);
    domainProps.load(inStream);
    inStream.close();
    domainNS = domainProps.getProperty("namespace");
    domainUid = domainProps.getProperty("domainUID");

    if (createOpDomain) {
      pvDir = resultRoot + "/acceptance_test_pv/persistentVolume-" + domainUid;
      //k8s job mounts PVROOT /scratch/<usr>/wl_k8s_test_results to /scratch
      domainProps.setProperty("weblogicDomainStoragePath", pvDir);
      pv = new PersistentVolume("/scratch/acceptance_test_pv/persistentVolume-" + domainUid);
      secret =
          new Secret(
              domainProps.getProperty("namespace"),
              domainProps.getProperty("secretName", domainUid + "-weblogic-credentials"),
              username,
              password);

      logger.info("Creating domain, waiting for the script " + "to complete execution");
    }
    domain = new Domain(domainProps, userProjectsDir);
    if (createOpDomain) {
      if (!domain.run()) {
        throw new RuntimeException("FAILURE: Create domain Script failed..");
      }
    }

    domain.verifyDomainCreated();
  }

  public static void shutdownAndCleanup() {
    try {
      if (domain != null) domain.shutdown();
      if (operator != null) operator.shutdown();
    } finally {
      TestUtils.cleanupAll();
    }
  }

  private static void createDirectories() {

    Path path = Paths.get(resultRoot);
    //if directory exists?
    if (!Files.exists(path)) {
      try {
        Files.createDirectories(path);
      } catch (Exception e) {
        //fail to create directory
        e.printStackTrace();
        throw new RuntimeException("FAILURE: ResultRoot " + resultRoot + " can not be created" + e);
      }
    }
    String output = TestUtils.executeCommand("chmod -R 777 " + resultRoot);
    if (!output.trim().equals("")) {
      throw new RuntimeException("FAILURE: Couldn't change permissions for PVROOT " + output);
    }

    path = Paths.get(resultDir);
    //if directory exists?
    if (!Files.exists(path)) {
      try {
        Files.createDirectories(path);
      } catch (IOException e) {
        //fail to create directory
        e.printStackTrace();
        throw new RuntimeException("FAILURE: ResultDir " + resultDir + " can not be created" + e);
      }
    }

    path = Paths.get(userProjectsDir);
    //if directory exists?
    if (!Files.exists(path)) {
      try {
        Files.createDirectories(path);
      } catch (IOException e) {
        //fail to create directory
        e.printStackTrace();
        throw new RuntimeException(
            "FAILURE: UserProjectsDir " + userProjectsDir + " can not be created" + e);
      }
    }
  }
}
