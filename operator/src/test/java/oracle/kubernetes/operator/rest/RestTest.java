// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Logger;
import javax.json.JsonObject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.rest.model.ClusterModel;
import oracle.kubernetes.operator.rest.model.CollectionModel;
import oracle.kubernetes.operator.rest.model.DomainModel;
import oracle.kubernetes.operator.rest.model.ErrorModel;
import oracle.kubernetes.operator.rest.model.ScaleClusterParamsModel;
import oracle.kubernetes.operator.rest.model.VersionModel;
import oracle.kubernetes.operator.work.Container;
import org.apache.commons.codec.binary.Base64;
import org.glassfish.jersey.jsonp.JsonProcessingFeature;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Tests the Weblogic Operator REST api */
public class RestTest {

  private WebTarget externalHttpsTarget;
  private WebTarget internalHttpsTarget;

  private static final String V1 = "v1";
  private static final String OPERATOR_HREF = "/operator";
  private static final String V1_HREF = OPERATOR_HREF + "/" + V1;
  private static final String LATEST_HREF = OPERATOR_HREF + "/latest";

  private static final String SWAGGER = "swagger";
  private static final String DOMAINS = "domains";
  private static final String CLUSTERS = "clusters";
  private static final String DOMAIN1 = "domain1";
  private static final String CLUSTER1 = "cluster1";

  private static final String SWAGGER_HREF = LATEST_HREF + "/" + SWAGGER;
  private static final String DOMAINS_HREF = LATEST_HREF + "/" + DOMAINS;
  private static final String DOMAIN1_HREF = DOMAINS_HREF + "/" + DOMAIN1;
  private static final String DOMAIN1_CLUSTERS_HREF = DOMAIN1_HREF + "/" + CLUSTERS;
  private static final String DOMAIN1_CLUSTER1_HREF = DOMAIN1_CLUSTERS_HREF + "/" + CLUSTER1;
  private static final String DOMAIN1_CLUSTER1_SCALE_HREF = DOMAIN1_CLUSTER1_HREF + "/scale";

  private static final String CA_CERT_DATA =
      "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUR3VENDQXFtZ0F3SUJBZ0lFVHVHU216QU5CZ2txaGtpRzl3MEJBUXNGQURDQmdURUxNQWtHQTFVRUJoTUMNClZWTXhFREFPQmdOVkJBZ1RCMDE1VTNSaGRHVXhEekFOQmdOVkJBY1RCazE1Vkc5M2JqRVhNQlVHQTFVRUNoTU8NClRYbFBjbWRoYm1sNllYUnBiMjR4R1RBWEJnTlZCQXNURUVaUFVpQlVSVk5VU1U1SElFOU9URmt4R3pBWkJnTlYNCkJBTVRFbGRsWW14dloybGpUM0JsY21GMGIzSkRRVEFlRncweE56RXlNRFV5TXpJNE1ERmFGdzB5TnpFeU1ETXkNCk16STRNREZhTUlHQk1Rc3dDUVlEVlFRR0V3SlZVekVRTUE0R0ExVUVDQk1IVFhsVGRHRjBaVEVQTUEwR0ExVUUNCkJ4TUdUWGxVYjNkdU1SY3dGUVlEVlFRS0V3NU5lVTl5WjJGdWFYcGhkR2x2YmpFWk1CY0dBMVVFQ3hNUVJrOVMNCklGUkZVMVJKVGtjZ1QwNU1XVEViTUJrR0ExVUVBeE1TVjJWaWJHOW5hV05QY0dWeVlYUnZja05CTUlJQklqQU4NCkJna3Foa2lHOXcwQkFRRUZBQU9DQVE4QU1JSUJDZ0tDQVFFQWp1Q1JtOE5Wck02bjQrQ1ptZFh3M3FqRjV3T00NCnZYZVJDZG9TZ1dEalRrUmtKV1RZOVlVaGVIaVB1TGozdXZRbFNwNUNZdngwTUYyM2pxbzcyaEJqM3U2cGZqbVMNCnJBeEpSdjZQV1E3Y3dTbGU3SU1URk5Qb3NvS0wrSEZmTWxmL2o2WUtqZzlQZXJPY09ocEI2WnJWS0NxeDdvOCsNCmRpb2FxdXlYV2drKzQxdkNKeGs5QVlqRGdBM1BnNC8xQ1BPVUU4eGN4Z29ldi9teW4yTFMvZkU5NzJsNVo4eUINCnFtcXI1V09EbUZLVWNqV0tSVGlnWjFSNVBoQjNVaHhBUXN4aHJKYVZFM3drT1ZjYWdza2QvWHM2eWY3cS9pVXMNClUxL1VCc3Q1SE5Dd2hnWUZ3bkV1RXZvaVNPeFl2UEx4cjRWTU1RM2lPR21QS0VBKzJoUUtxc214b3dJREFRQUINCm96OHdQVEFQQmdOVkhSTUVDREFHQVFIL0FnRUJNQXNHQTFVZER3UUVBd0lDQkRBZEJnTlZIUTRFRmdRVVlFcDANCmkxc2hZcDh5N1lQTEk5MXh6L2pXWVVBd0RRWUpLb1pJaHZjTkFRRUxCUUFEZ2dFQkFIZFNtUVZZT0pzdmJFR1QNCmxwdk1CcjhCL0M1cUdGQjF4N3BBZWRlOFA1TXk0MHg1QnNjTjg4ZkN3djZSVStUbDNjenQ4ZHBMc0RZaTIzR2QNCnEwSk1LT2docXdSa2w4bEZRNmY0ZUdsZGFLMGlOc3hxQkJZUVFBeHNscTV0RXRUZk4rYmdVbGUyMmhpNERjUGsNClh0UDNncGhHdzRjSXlpZ09DbWpiOVk5VnNQY0M2Rit2bmhNaWxkRVhmUEFJcWRQSnlWZFMrWWNXOXdkaXF2d28NClVsK0h2VDhyMnFSbTV0U2NReFRySEY1emdwZzZhUmRENk1qWGQwZFAydzUzazVQeUZPb0o4eE1Qd1JGeE1xazkNCmkzdm9ZcUFBNXBNZXBVR3ladllKenUrUEk2cmFJNlllc3NMcW02NEE0NlZYS0xIOEZvTnYwMEQ2Y0o5R1NwMUUNCkJmRm85L3M9Ci0tLS0tRU5EIENFUlRJRklDQVRFLS0tLS0K";
  public static final String OP_CERT_DATA =
      "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUR4ekNDQXErZ0F3SUJBZ0lFT2lMdG1EQU5CZ2txaGtpRzl3MEJBUXNGQURDQmdURUxNQWtHQTFVRUJoTUMNClZWTXhFREFPQmdOVkJBZ1RCMDE1VTNSaGRHVXhEekFOQmdOVkJBY1RCazE1Vkc5M2JqRVhNQlVHQTFVRUNoTU8NClRYbFBjbWRoYm1sNllYUnBiMjR4R1RBWEJnTlZCQXNURUVaUFVpQlVSVk5VU1U1SElFOU9URmt4R3pBWkJnTlYNCkJBTVRFbGRsWW14dloybGpUM0JsY21GMGIzSkRRVEFlRncweE56RXlNRFV5TXpJNE1EUmFGdzB5TnpFeU1ETXkNCk16STRNRFJhTUhneEN6QUpCZ05WQkFZVEFsVlRNUkF3RGdZRFZRUUlFd2ROZVZOMFlYUmxNUTh3RFFZRFZRUUgNCkV3Wk5lVlJ2ZDI0eEZ6QVZCZ05WQkFvVERrMTVUM0puWVc1cGVtRjBhVzl1TVJrd0Z3WURWUVFMRXhCR1QxSWcNClZFVlRWRWxPUnlCUFRreFpNUkl3RUFZRFZRUURFd2xzYjJOaGJHaHZjM1F3Z2dFaU1BMEdDU3FHU0liM0RRRUINCkFRVUFBNElCRHdBd2dnRUtBb0lCQVFDOCs1MVNYcnpPUm8xaHYwb1doZFhnNTBCczRJc0pUSUw4ZVdZV1R6SGINCkxVcVMxQldWZllVMHJGWXZYTDBGQnh6SGdtL1lVZ0dHVkQwZEVtdVBMSXc3cEd4TS84Sm5HbGpvampnZSs5QmUNCk9rMFBKSXc3MmpPazA0a3ZOK1V6QnJodk5kRnRUQ3VnaVZDWG1ncjZLYjlIM2JpSlkraWZIMmR1OTRIamcrQ2ENCkRYZU5qZXlSRmVZQmdmRTd1cERBNGx6aXNrRVFjczVTSHJNcVB5TFViZVRrYk1aSy82bVVYazdOTGhyQ3BRNmMNCjNnVGgrSDVLaElBd0lXR2hXTEhOTGkzWm5kbWhRRW53enZMOUlDYVpHazZ3QmlxNDJsdUUxYndPdmpMVnRKOEsNCmVJWGFONnlIdXFFamNMUFBEeEhtYkZwVWlaZmgrWTk0K29RQTRMS3lGdWhOQWdNQkFBR2pUekJOTUI4R0ExVWQNCkl3UVlNQmFBRkdCS2RJdGJJV0tmTXUyRHl5UGRjYy80MW1GQU1Bc0dBMVVkRHdRRUF3SUQrREFkQmdOVkhRNEUNCkZnUVVvcFFvY0ExaVpGN042ZDhMdmoyM1Ezc0FJTVl3RFFZSktvWklodmNOQVFFTEJRQURnZ0VCQUlHZFpkMVMNCkhZdFMxQnVRSnpLcSthTEVHUzQvQk01aXk2Q0oxaGpvUnpyc0Y0TEhtTmNqOE04M1RYY1JXTHJraEhtd3FFU0cNCjVpU3o0bVJnbmxxSlIxcndxZjhOUnAwU3dnVVlmbmdvdGI2dlZxVUhWZzcvdWtaRURYV2dUMThaS1BrZkp4SnoNCmRPdlpEeDhETzVhOWhQVFZKeWwzekd3ckhBaVY3Zjg1RWdIVkxsUTFqbC91eG9zSXJaMm5VZ3BFVTlzaC8xd04NCkIyYUtZVk5WQVFNZVZuVHhHU0h0WW5pOUJ1U1FDMFhZS3FCbVlHWWlwUDlnenJBd0hFTXVEeFRxcUdIRU84WVgNCjgrem1xVGJTVzQ2NkNYL2RsTFhNKzR3MFErNU1XODZBbkpzVGhEeE5mWkMrd3o5ZHNwbm9lclVsYWVyMVhiaWkNCjAwd2ZNaU81UU9uTlF3TT0KLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=";
  private static final String OP_KEY_DATA =
      "QmFnIEF0dHJpYnV0ZXMKICAgIGZyaWVuZGx5TmFtZTogd2VibG9naWMtb3BlcmF0b3IuYWxpYXMKICAgIGxvY2FsS2V5SUQ6IDU0IDY5IDZEIDY1IDIwIDMxIDM1IDMxIDMyIDM1IDMxIDM2IDM0IDM4IDMzIDMxIDM2IDM2IApLZXkgQXR0cmlidXRlczogPE5vIEF0dHJpYnV0ZXM+Ci0tLS0tQkVHSU4gUlNBIFBSSVZBVEUgS0VZLS0tLS0KTUlJRXBRSUJBQUtDQVFFQXZQdWRVbDY4emthTlliOUtGb1hWNE9kQWJPQ0xDVXlDL0hsbUZrOHgyeTFLa3RRVgpsWDJGTkt4V0wxeTlCUWNjeDRKdjJGSUJobFE5SFJKcmp5eU1PNlJzVFAvQ1p4cFk2STQ0SHZ2UVhqcE5EeVNNCk85b3pwTk9KTHpmbE13YTRielhSYlV3cm9JbFFsNW9LK2ltL1I5MjRpV1Bvbng5bmJ2ZUI0NFBnbWcxM2pZM3MKa1JYbUFZSHhPN3FRd09KYzRySkJFSExPVWg2ektqOGkxRzNrNUd6R1N2K3BsRjVPelM0YXdxVU9uTjRFNGZoKwpTb1NBTUNGaG9WaXh6UzR0MlozWm9VQko4TTd5L1NBbW1ScE9zQVlxdU5wYmhOVzhEcjR5MWJTZkNuaUYyamVzCmg3cWhJM0N6enc4UjVteGFWSW1YNGZtUGVQcUVBT0N5c2hib1RRSURBUUFCQW9JQkFRQ2xjYW15Mk5sMXhISTEKcHArWHhDY1BzNlBGTFhiSzl6NmRCVEtJU1dDZVBySlFoSGM0M2lCbGtwSUkrS2xKNDRZZ2EySzdBRi94VjRJQgpGNFV1WEpPUUMwdjh4Tk5PSzlTMkV2dXl0RVVnbU8ycFdoZWl0azRMK0Z6YkI1WVI2OG8vSWVCc1RRak1qQ29QCjduMjVzQjZUTGRwRi9UOURQdHp3V3FKTnFjQlJYQU9ybDExYWI2YWNVTTNjaHdXL00vemR3Rm93aDRPWDkvMzYKc2hXV2VXVERxZEh1ZVIzQXptNEthYWpyTWRMSlViV2Q1MkpUbUo0c1huWldmakgvNVZCR3dBVDFMank0MXEyRwo4NXV2d09yanl4eFF0Q010a1A1YUNzdGx2emlRZ3FKWXNDRjk5ZTBnQTEzdi90YzJxcWVZZVVpSW9xalhSZTJKCituSE1HamJCQW9HQkFOOEdFRnY2SFRUQ3RqM3N6MHl6elBwRDQwNytmbkptL01mY2Jla3FtSmlzUkNiTEJyMksKSDNHU0w3S0gyZWRsbU56NU1IQnZ0WUhjd21YMzgvaUVzS01lejBuWXU3ejA3aFVZVndsZ3JHZG9mS1lTWXVDbAp6SW9tMWhHMjZmbTJKdUc5V1NoS3dzY282SGFxMnI2U0VybXF3S29JNnI2bGc5ZFJuR0F6T2FvRkFvR0JBTmp0CkIrejYwMUI3eXc2ZTRkVUFSUjV2R0lPMmxZR2YwUTBORlVBNm1lNHUrUzkxZi92NmZESERSK3pjb0E5TGRDdjQKcmV5RkFveEJoVGdFeWlqWmNjVkFuRTIwa1VrYW1OMzV4UUdjMnpFTTJEeEU4YnpyR2FzNHd0a0tITFZwVkZCOQphaThLajBSMWU4Z0NWYS9EOWtJK1NKdVl2T3hrYTRaa2dLUFJEKytwQW9HQkFNNEtDaDhQS09CUGFwSTNMeVRMCmozaytNc2dOOGIrN1NabFRHZStwdktSL3NjNnREcU1ZOGdlNGFIeGxhWGlQc2ZPais0NUVoY2xkcS9NTWFjYVUKdjZOVG1XbTk0Mk9rWERmODdwdnBSaDJhdUYyczZ0QmtIRjBkbC9OeHF5TlpsdjVDTWhZNVMwMDNpOXZsNklUYQo2cFhCSEpGNS8zVDE4S0dCOGhnbCt3WUpBb0dBSVYvZWYweGI1SjhYTDZtc0x4dzZoaU1yajJBeEFsOTNSMTNDCjdqM1YzdnBsSmpNYXZNYmVBcjM3dStwNXljQ2ZJQUREcVljUlRFanNXU0VMaFZ0bkVLVVBKemxudk9xVnFGazgKUVRKRDJ2a1I3N3Zmd1dRZWIrUnN2ZjI3U3dIb2tmV3B0NUVWVjhBSGlrOHBwY0F0akNXUEFEbHduNklYbFBhegpnQUN6UmZrQ2dZRUFoOEZiZDNCeDRVWWR2NVo5RjFsdnFpNk9kdS9EOWtMYjMvb3p1dlhIUkQzaXE5NUFUcG5JClFVNEhRMnBpSzBkYTJnYnFML2UrQkhqYmdPK0NtbmxBT0F4VGx0RWNkelVFeHNIdWxYdFB5bWRod1RFenpRRUEKTUpGY2FRTVhGd2MxQTdvVXJkUFozRmNhdCt2RTZoUnRIRnJDMVFHeU90d3dGSGtIMXR5Q1cycz0KLS0tLS1FTkQgUlNBIFBSSVZBVEUgS0VZLS0tLS0K";

  private static final Logger logger =
      LoggingFactory.getLogger("Operator", "Operator").getUnderlyingLogger();
  private List<Handler> savedhandlers;

  @After
  public void restoreConsoleLogging() {
    TestUtils.restoreConsoleHandlers(logger, savedhandlers);
  }

  @Before
  public void setUp() throws Exception {
    Container container = new Container();

    savedhandlers = TestUtils.removeConsoleHandlers(logger);

    // Start the REST server
    RestServer.create(new TestRestConfigImpl());
    RestServer.getInstance().start(container);

    // create the client
    Client c =
        ClientBuilder.newBuilder()
            .trustStore(createTrustStore())
            .register(JsonProcessingFeature.class)
            .build();

    externalHttpsTarget = c.target(RestServer.getInstance().getExternalHttpsUri());
    internalHttpsTarget = c.target(RestServer.getInstance().getInternalHttpsUri());
  }

  @After
  public void tearDown() throws Exception {
    RestServer.getInstance().stop();
    RestServer.destroy();
  }

  @Test
  public void testOperator() {
    Response r = request(OPERATOR_HREF).get();
    verifyOK(r);
    CollectionModel<VersionModel> want = new CollectionModel<VersionModel>();
    {
      VersionModel item = createLatestVersion();
      item.addSelfLinks(V1_HREF);
      want.addItem(item);
    }
    want.addSelfLinks(OPERATOR_HREF);
    CollectionModel<VersionModel> have =
        r.readEntity(new GenericType<CollectionModel<VersionModel>>() {});
    assertEquals(have.toString(), want.toString());
  }

  @Test
  public void testInteralHttps() {
    // just verify that the port is working - testOperator will check the response body
    Response r = request(internalHttpsTarget, OPERATOR_HREF).get();
    verifyOK(r);
  }

  @Test
  public void testExistingVersion() {
    Response r = request(V1_HREF).get();
    verifyOK(r);
    VersionModel want = createLatestVersion();
    want.addSelfAndParentLinks(V1_HREF, OPERATOR_HREF);
    want.addLink(DOMAINS, V1_HREF + "/" + DOMAINS);
    want.addLink(SWAGGER, V1_HREF + "/" + SWAGGER);
    verifyEntity(r, want);
  }

  @Test
  public void testLatestVersion() {
    Response r = request(LATEST_HREF).get();
    verifyOK(r);
    VersionModel want = createLatestVersion();
    want.addSelfAndParentLinks(LATEST_HREF, OPERATOR_HREF);
    want.addLink(DOMAINS, DOMAINS_HREF);
    want.addLink(SWAGGER, SWAGGER_HREF);
    verifyEntity(r, want);
  }

  @Test
  public void testNonExistingVersion() {
    String href = OPERATOR_HREF + "/v2";
    Response r = request(href).get();
    verifyNotFound(r);
    ErrorModel want = new ErrorModel(Status.NOT_FOUND.getStatusCode(), href);
    verifyEntity(r, want);
  }

  @Test
  public void testSwagger() {
    Response r = request(SWAGGER_HREF).get();
    verifyOK(r);
    // Verify that the output really is json:
    JsonObject j = r.readEntity(JsonObject.class);
    // And verify one of the top level properties that the swagger should have:
    assertEquals(j.getString("swagger"), "2.0");
  }

  @Test
  public void testDomains() {
    Response r = request(DOMAINS_HREF).get();
    verifyOK(r);
    CollectionModel<DomainModel> want = new CollectionModel<DomainModel>();
    {
      DomainModel item = createDomainUID1();
      item.addSelfLinks(DOMAIN1_HREF);
      want.addItem(item);
    }
    want.addSelfAndParentLinks(DOMAINS_HREF, LATEST_HREF);
    CollectionModel<DomainModel> have =
        r.readEntity(new GenericType<CollectionModel<DomainModel>>() {});
    assertEquals(have.toString(), want.toString());
  }

  @Test
  public void testExistingDomain() {
    Response r = request(DOMAIN1_HREF).get();
    verifyOK(r);
    DomainModel want = createDomainUID1();
    want.addSelfAndParentLinks(DOMAIN1_HREF, DOMAINS_HREF);
    want.addLink("clusters", DOMAIN1_CLUSTERS_HREF);
    verifyEntity(r, want);
  }

  @Test
  public void testNonExistingDomain() {
    String href = DOMAINS_HREF + "/domain2";
    Response r = request(href).get();
    verifyNotFound(r);
    ErrorModel want = new ErrorModel(Status.NOT_FOUND.getStatusCode(), href);
    verifyEntity(r, want);
  }

  @Test
  public void testClusters() {
    Response r = request(DOMAIN1_CLUSTERS_HREF).get();
    verifyOK(r);
    CollectionModel<ClusterModel> want = new CollectionModel<ClusterModel>();
    {
      ClusterModel item = createDomain1Cluster1();
      item.addSelfLinks(DOMAIN1_CLUSTER1_HREF);
      want.addItem(item);
    }
    want.addSelfAndParentLinks(DOMAIN1_CLUSTERS_HREF, DOMAIN1_HREF);
    CollectionModel<ClusterModel> have =
        r.readEntity(new GenericType<CollectionModel<ClusterModel>>() {});
    assertEquals(have.toString(), want.toString());
  }

  @Test
  public void testExistingCluster() {
    Response r = request(DOMAIN1_CLUSTER1_HREF).get();
    verifyOK(r);
    ClusterModel want = createDomain1Cluster1();
    want.addSelfAndParentLinks(DOMAIN1_CLUSTER1_HREF, DOMAIN1_CLUSTERS_HREF);
    want.addActionLink("scale", DOMAIN1_CLUSTER1_SCALE_HREF);
    verifyEntity(r, want);
  }

  @Test
  public void testNonExistingCluster() {
    String href = DOMAIN1_CLUSTERS_HREF + "/cluster2";
    Response r = request(href).get();
    verifyNotFound(r);
    ErrorModel want = new ErrorModel(Status.NOT_FOUND.getStatusCode(), href);
    verifyEntity(r, want);
  }

  @Test
  public void testScaleCluster() {
    Entity<ScaleClusterParamsModel> entity =
        Entity.entity(createScaleClusterParams(), MediaType.APPLICATION_JSON);
    verifyStatusCode(
        request(DOMAIN1_CLUSTER1_SCALE_HREF).header("X-Requested-By", "TestClient").post(entity),
        Status.NO_CONTENT);
  }

  @Test
  public void testScaleClusterMissingRequestedByHeader() {
    Entity<ScaleClusterParamsModel> entity =
        Entity.entity(createScaleClusterParams(), MediaType.APPLICATION_JSON);
    verifyStatusCode(request(DOMAIN1_CLUSTER1_SCALE_HREF).post(entity), Status.BAD_REQUEST);
  }

  @Test
  public void testMissingAuthorizationHeader() {
    Response r = externalHttpsTarget.path(OPERATOR_HREF).request().get();
    verifyNotAuthenticated(r);
  }

  @Test
  public void testMissingBearerPrefix() {
    Response r =
        externalHttpsTarget
            .path(OPERATOR_HREF)
            .request()
            .header(HttpHeaders.AUTHORIZATION, "dummy token")
            .get();
    verifyNotAuthenticated(r);
  }

  @Test
  public void testMissingAccessToken() {
    Response r =
        externalHttpsTarget
            .path(OPERATOR_HREF)
            .request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer ")
            .get();
    verifyNotAuthenticated(r);
  }

  private VersionModel createLatestVersion() {
    return new VersionModel(V1, true, "active");
  }

  private DomainModel createDomainUID1() {
    return new DomainModel(DOMAIN1);
  }

  private ClusterModel createDomain1Cluster1() {
    return new ClusterModel(CLUSTER1);
  }

  private ScaleClusterParamsModel createScaleClusterParams() {
    ScaleClusterParamsModel params = new ScaleClusterParamsModel();
    params.setManagedServerCount(3);
    return params;
  }

  private void verifyOK(Response resp) {
    verifyStatusCode(resp, Status.OK);
  }

  private void verifyNotFound(Response resp) {
    verifyStatusCode(resp, Status.NOT_FOUND);
  }

  private void verifyNotAuthenticated(Response resp) {
    // weird http convention:
    // a 401 (unauthorized) status is returned when the user can't be authenticated
    verifyStatusCode(resp, Status.UNAUTHORIZED);
  }

  private void verifyStatusCode(Response resp, Status status) {
    assertEquals(status.getStatusCode(), resp.getStatus());
  }

  private void verifyEntity(Response resp, Object want) {
    assertEquals(resp.readEntity(want.getClass()).toString(), want.toString());
  }

  private Invocation.Builder request(String uri) {
    // by default, test the external https port
    return request(externalHttpsTarget, uri);
  }

  private Invocation.Builder request(WebTarget target, String uri) {
    return target.path(uri).request().header(HttpHeaders.AUTHORIZATION, "Bearer dummy token");
  }

  public static class TestRestConfigImpl implements RestConfig {
    private final int randomPort;

    public TestRestConfigImpl() {
      randomPort = (int) (Math.random() * 8000) + 9000;
    }

    @Override
    public String getHost() {
      return "localhost";
    }

    // TBD - should we generate a random port #s?

    @Override
    public int getExternalHttpsPort() {
      return randomPort;
    }

    @Override
    public int getInternalHttpsPort() {
      return randomPort + 1;
    }

    @Override
    public String getOperatorExternalCertificateData() {
      return OP_CERT_DATA;
    }

    @Override
    public String getOperatorInternalCertificateData() {
      return OP_CERT_DATA;
    }

    @Override
    public String getOperatorExternalCertificateFile() {
      return null;
    }

    @Override
    public String getOperatorInternalCertificateFile() {
      return null;
    }

    @Override
    public String getOperatorExternalKeyData() {
      return OP_KEY_DATA;
    }

    @Override
    public String getOperatorInternalKeyData() {
      return OP_KEY_DATA;
    }

    @Override
    public String getOperatorExternalKeyFile() {
      return null;
    }

    @Override
    public String getOperatorInternalKeyFile() {
      return null;
    }

    @Override
    public RestBackend getBackend(String accessToken) {
      // ignore the access token since we're not doing any k8s authentication
      return new TestRestBackendImpl();
    }
  }

  private static class TestRestBackendImpl implements RestBackend {
    Map<String, Set<String>> domains = new HashMap<>();

    private TestRestBackendImpl() {
      {
        Set<String> clusters = new HashSet<String>();
        clusters.add("cluster1");
        domains.put("domain1", clusters);
      }
    }

    @Override
    public Set<String> getDomainUIDs() {
      return domains.keySet();
    }

    @Override
    public boolean isDomainUID(String domainUID) {
      return getDomainUIDs().contains(domainUID);
    }

    @Override
    public Set<String> getClusters(String domainUID) {
      if (!isDomainUID(domainUID)) {
        throw new AssertionError("Invalid domainUID: " + domainUID);
      }

      return domains.get(domainUID);
    }

    @Override
    public boolean isCluster(String domainUID, String cluster) {
      return getClusters(domainUID).contains(cluster);
    }

    @Override
    public void scaleCluster(String domainId, String cluster, int managedServerCount) {}
  }

  private KeyStore createTrustStore() throws Exception {
    byte[] bytes = Base64.decodeBase64(CA_CERT_DATA);
    InputStream sslCaCert = new ByteArrayInputStream(bytes);

    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
    ks.load(null, null);
    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
    int index = 0;
    for (Certificate cert : certificateFactory.generateCertificates(sslCaCert)) {
      String alias = "ca" + Integer.toString(index++);
      ks.setCertificateEntry(alias, cert);
    }
    return ks;
  }
}

/* Here's how I generated te baked in test certs

#!/bin/bash
#set -x

CERTS_DIR="testcerts"
OP_HOST="localhost"
DAYS_VALID="3650"

TEMP_PW="temp_password"
BASE_NAME="OU=FOR TESTING ONLY, O=MyOrganization, L=MyTown, ST=MyState, C=US"
GEN_KEY_ARGS="-storepass ${TEMP_PW} -keypass ${TEMP_PW} -keysize 2048 -keyalg RSA -validity ${DAYS_VALID}"
IMPORT_KS_ARGS="-srcstorepass ${TEMP_PW} -deststorepass ${TEMP_PW} -deststoretype PKCS12"

CA_PREFIX="weblogic-operator-ca"
CA_JKS="${CA_PREFIX}.jks"
CA_ALIAS="${CA_PREFIX}.alias"
CA_PKCS12="${CA_PREFIX}.p12"
CA_CERT_PEM="${CA_PREFIX}.cert.pem"
CA_KEY_PEM="${CA_PREFIX}.key.pem"

OP_PREFIX="weblogic-operator"
OP_JKS="${OP_PREFIX}.jks"
OP_ALIAS="${OP_PREFIX}.alias"
OP_PKCS12="${OP_PREFIX}.p12"
OP_CSR="${OP_PREFIX}.csr"
OP_CERT_PEM="${OP_PREFIX}.cert.pem"
OP_KEY_PEM="${OP_PREFIX}.key.pem"

rm -rf ${CERTS_DIR}
mkdir ${CERTS_DIR}
cd ${CERTS_DIR}

# generate a keypair for the WebLogic Operator Certificate Authority, putting it in a keystore
keytool -genkey -keystore ${CA_JKS} -alias ${CA_ALIAS} ${GEN_KEY_ARGS} -dname "CN=WeblogicOperatorCA, ${BASE_NAME}" \
  -ext BC=1 -ext KU=keyCertSign

# extract the CA cert to a pem file
keytool -exportcert -keystore ${CA_JKS} -storepass ${TEMP_PW} -alias ${CA_ALIAS} -rfc > ${CA_CERT_PEM}

# convert the CA keystore to a pkcs12 file
keytool -importkeystore -srckeystore ${CA_JKS} -destkeystore ${CA_PKCS12} ${IMPORT_KS_ARGS}

# extract the CA private key from the pkcs12 file to a pem file
openssl pkcs12 -in ${CA_PKCS12} -passin pass:${TEMP_PW} -nodes -nocerts -out ${CA_KEY_PEM}

# generate a keypair for the WebLogic Operator Certificate Authority, putting it in a keystore
keytool -genkey -keystore ${OP_JKS} -alias ${OP_ALIAS} ${GEN_KEY_ARGS} -dname "CN=${OP_HOST}, ${BASE_NAME}"

# convert the operator keystore to a pkcs12 file
keytool -importkeystore -srckeystore ${OP_JKS} -srcstorepass ${TEMP_PW} -destkeystore ${OP_PKCS12} ${IMPORT_KS_ARGS}

# extract the operator's key from the pkcs12 file to a pem file
openssl pkcs12 -in ${OP_PKCS12} -passin pass:${TEMP_PW} -nodes -nocerts -out ${OP_KEY_PEM}

# create a certificate signing request for the operator's keystore
keytool -certreq -keystore ${OP_JKS} -storepass ${TEMP_PW} -alias ${OP_ALIAS} -keypass ${TEMP_PW} -keyalg rsa -file ${OP_CSR}

# use the CSR and CA to get a signed certificate for the operator
keytool -gencert -keystore ${CA_JKS} -storepass ${TEMP_PW} -alias ${CA_ALIAS} -validity ${DAYS_VALID} -rfc -infile ${OP_CSR} -outfile ${OP_CERT_PEM} \
  -ext KU=digitalSignature,nonRepudiation,keyEncipherment,dataEncipherment,keyAgreement

# could do this programmatically - see mod/security/network/ssl/src/main/java/utils/CertGen.java
# (it's a lot of code - I don't think the Operator should clone it)
CA_CERT_DATA=`base64 ${CA_CERT_PEM}`
OP_CERT_DATA=`base64 ${OP_CERT_PEM}`
OP_KEY_DATA=`base64 ${OP_KEY_PEM}`
echo "  private static final String CA_CERT_DATA = \"${CA_CERT_DATA}\";"
echo "  private static final String OP_CERT_DATA = \"${OP_CERT_DATA}\";"
echo "  private static final String OP_KEY_DATA = \"${OP_KEY_DATA}\";"

cd ..
rm -r ${CERTS_DIR}

*/
