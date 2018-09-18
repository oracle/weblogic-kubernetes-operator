package oracle.kubernetes.weblogic.domain.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.fge.jackson.JsonNodeReader;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import oracle.kubernetes.weblogic.domain.DomainTestBase;
import org.junit.Test;

public class DomainV1SchemaTest {
  @Test
  public void sampleMatchesSchema() throws IOException {
    JsonNode instance =
        new JsonNodeReader().fromReader(new StringReader(jsonFromYaml("v1/domain-sample.yaml")));
    JsonNode schema =
        new JsonNodeReader().fromInputStream(getClass().getResourceAsStream("/schema/domain.json"));
    ProcessingReport report =
        JsonSchemaFactory.byDefault().getValidator().validateUnchecked(schema, instance);
  }

  private String jsonFromYaml(String resourceName) throws IOException {
    URL resource = DomainTestBase.class.getResource(resourceName);
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj = yamlReader.readValue(resource, Object.class);

    ObjectMapper jsonWriter = new ObjectMapper();
    return jsonWriter.writeValueAsString(obj);
  }
}
