package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.models.VersionInfo;
import oracle.kubernetes.weblogic.domain.v1.DomainList;

public interface SynchronousCallFactory {

  V1beta1CustomResourceDefinition readCustomResourceDefinition(ApiClient client, String name, String pretty, Boolean exact, Boolean export) throws ApiException;

  V1beta1CustomResourceDefinition createCustomResourceDefinition(ApiClient client, V1beta1CustomResourceDefinition body, String pretty) throws ApiException;

  V1SelfSubjectRulesReview createSelfSubjectRulesReview(ApiClient client, V1SelfSubjectRulesReview body, String pretty) throws ApiException;

  V1PersistentVolumeList listPersistentVolumes(String _continue, ApiClient client, String pretty, String fieldSelector, Boolean includeUninitialized, String labelSelector, Integer limit, String resourceVersion, Integer timeoutSeconds, Boolean watch) throws ApiException;

  VersionInfo getVersionCode(ApiClient client) throws ApiException;

  DomainList getDomainList(ApiClient client, String namespace, String _continue, String pretty, String fieldSelector, Boolean includeUninitialized, String labelSelector, Integer limit, String resourceVersion, Integer timeoutSeconds, Boolean watch) throws ApiException;
}
