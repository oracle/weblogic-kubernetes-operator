package oracle.kubernetes.operator;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.K8sTestUtils;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

public class DeleteWeblogicDomainResources extends BaseTest {
  private static final String domain1ForDelValueYamlFile = "domain_del_1.yaml";
  private static final String domain2ForDelValueYamlFile = "domain_del_2.yaml";

  private K8sTestUtils k8sTestUtils = new K8sTestUtils();

  private Operator operatorForDel;

  @Before
  public void setup() throws Exception {
    ITOperator.staticPrepare();

    if (operatorForDel == null) {
      logger.info("About to create operator");
      operatorForDel = TestUtils.createOperator("operator_del.yaml");
    }
  }

  @Test
  public void deleteOneDomain() throws Exception {
    logTestBegin("Deleting one domain.");
    final Domain domain = TestUtils.createDomain(domain1ForDelValueYamlFile);
    verifyBeforeDeletion(domain);

    logger.info("About to delete domain: " + domain.getDomainUid());
    TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());

    verifyAfterDeletion(domain);
  }

  @Test
  public void deleteTwoDomains() throws Exception {
    logTestBegin("Deleting two domains.");
    final Domain domain1 = TestUtils.createDomain(domain1ForDelValueYamlFile);
    final Domain domain2 = TestUtils.createDomain(domain2ForDelValueYamlFile);

    verifyBeforeDeletion(domain1);
    verifyBeforeDeletion(domain2);

    final String domainUidsToBeDeleted = domain1.getDomainUid() + "," + domain2.getDomainUid();
    logger.info("About to delete domains: " + domainUidsToBeDeleted);
    TestUtils.deleteWeblogicDomainResources(domainUidsToBeDeleted);

    verifyAfterDeletion(domain1);
    verifyAfterDeletion(domain2);
  }

  private void verifyBeforeDeletion(Domain domain) throws Exception {
    final String domainNs = String.class.cast(domain.getDomainMap().get("namespace"));
    final String domainUid = domain.getDomainUid();
    final String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    final String credentialsName =
        String.class.cast(domain.getDomainMap().get("weblogicCredentialsSecretName"));

    logger.info("Before deletion of domain: " + domainUid);

    k8sTestUtils.verifyDomainCrd();
    k8sTestUtils.verifyDomain(domainNs, domainUid, true);
    k8sTestUtils.verifyPods(domainNs, domain1LabelSelector, 4);
    k8sTestUtils.verifyJobs(domain1LabelSelector, 1);
    k8sTestUtils.verifyNoDeployments(domain1LabelSelector);
    k8sTestUtils.verifyNoReplicaSets(domain1LabelSelector);
    k8sTestUtils.verifyServices(domain1LabelSelector, 5);
    k8sTestUtils.verifyPvcs(domain1LabelSelector, 1);
    k8sTestUtils.verifyIngresses(domainNs, domainUid, domain1LabelSelector, 1);
    k8sTestUtils.verifyConfigMaps(domain1LabelSelector, 1);
    k8sTestUtils.verifyNoServiceAccounts(domain1LabelSelector);
    k8sTestUtils.verifyNoRoles(domain1LabelSelector);
    k8sTestUtils.verifyNoRoleBindings(domain1LabelSelector);
    k8sTestUtils.verifySecrets(credentialsName, 1);
    k8sTestUtils.verifyPvs(domain1LabelSelector, 1);
    k8sTestUtils.verifyNoClusterRoles(domain1LabelSelector);
    k8sTestUtils.verifyNoClusterRoleBindings(domain1LabelSelector);
  }

  private void verifyAfterDeletion(Domain domain) throws Exception {
    final String domainNs = String.class.cast(domain.getDomainMap().get("namespace"));
    final String domainUid = domain.getDomainUid();
    final String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    final String credentialsName =
        String.class.cast(domain.getDomainMap().get("weblogicCredentialsSecretName"));

    logger.info("After deletion of domain: " + domainUid);
    k8sTestUtils.verifyDomainCrd();
    k8sTestUtils.verifyDomain(domainNs, domainUid, false);
    k8sTestUtils.verifyPods(domainNs, domain1LabelSelector, 0);
    k8sTestUtils.verifyJobs(domain1LabelSelector, 0);
    k8sTestUtils.verifyNoDeployments(domain1LabelSelector);
    k8sTestUtils.verifyNoReplicaSets(domain1LabelSelector);
    k8sTestUtils.verifyServices(domain1LabelSelector, 0);
    k8sTestUtils.verifyPvcs(domain1LabelSelector, 0);
    k8sTestUtils.verifyIngresses(domainNs, domainUid, domain1LabelSelector, 0);
    k8sTestUtils.verifyConfigMaps(domain1LabelSelector, 0);
    k8sTestUtils.verifyNoServiceAccounts(domain1LabelSelector);
    k8sTestUtils.verifyNoRoles(domain1LabelSelector);
    k8sTestUtils.verifyNoRoleBindings(domain1LabelSelector);
    k8sTestUtils.verifySecrets(credentialsName, 0);
    k8sTestUtils.verifyPvs(domain1LabelSelector, 0);
    k8sTestUtils.verifyNoClusterRoles(domain1LabelSelector);
    k8sTestUtils.verifyNoClusterRoleBindings(domain1LabelSelector);
  }

  @AfterClass
  public static void uninit() throws Exception {
    ITOperator.staticUnPrepare();
  }
}
