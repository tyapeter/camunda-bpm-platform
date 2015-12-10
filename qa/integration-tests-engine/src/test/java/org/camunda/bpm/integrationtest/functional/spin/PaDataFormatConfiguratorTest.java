/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.integrationtest.functional.spin;

import java.io.IOException;
import java.util.Date;

import org.camunda.bpm.application.ProcessApplicationContext;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.Variables.SerializationDataFormats;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.camunda.bpm.engine.variable.value.SerializationDataFormat;
import org.camunda.bpm.integrationtest.functional.spin.dataformat.JsonDataFormatConfigurator;
import org.camunda.bpm.integrationtest.functional.spin.dataformat.JsonSerializable;
import org.camunda.bpm.integrationtest.util.AbstractFoxPlatformIntegrationTest;
import org.camunda.bpm.integrationtest.util.DeploymentHelper;
import org.camunda.bpm.integrationtest.util.TestContainer;
import org.camunda.spin.spi.DataFormatConfigurator;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Thorben Lindhauer
 *
 */
@RunWith(Arquillian.class)
public class PaDataFormatConfiguratorTest extends AbstractFoxPlatformIntegrationTest {

  public static final long ONE_DAY_IN_MILLIS = 1000 * 60 * 60 * 24;

  @Deployment
  public static WebArchive createDeployment() {
    return ShrinkWrap.create(WebArchive.class, "PaDataFormatTest.war")
        .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
        .addAsLibraries(DeploymentHelper.getEngineCdi())
        .addAsResource("META-INF/processes.xml")
        .addClass(AbstractFoxPlatformIntegrationTest.class)
        .addClass(TestContainer.class)
        .addClass(ReferenceStoringProcessApplication.class)
        .addAsResource("org/camunda/bpm/integrationtest/oneTaskProcess.bpmn")
        .addClass(JsonDataFormatConfigurator.class)
        .addClass(JsonSerializable.class)
        .addAsServiceProvider(DataFormatConfigurator.class, JsonDataFormatConfigurator.class)
        .addAsLibraries(DeploymentHelper.getSpinJacksonJsonDataFormat());
  }

  @Test
  public void testBuiltinFormatApplies() throws JsonProcessingException, IOException {

    Date date = new Date(ONE_DAY_IN_MILLIS * 10); // 10th of January 1970
    JsonSerializable jsonSerializable = new JsonSerializable(date);

    final ProcessInstance pi = runtimeService.startProcessInstanceByKey("testProcess");

    try {
      ProcessApplicationContext.setCurrentProcessApplication(ReferenceStoringProcessApplication.INSTANCE);
      runtimeService.setVariable(pi.getId(),
        "jsonSerializable",
        Variables.objectValue(jsonSerializable).serializationDataFormat(SerializationDataFormats.JSON).create());
    } finally {
      ProcessApplicationContext.clear();
    }

    ObjectValue objectValue = runtimeService.getVariableTyped(pi.getId(), "jsonSerializable", false);

    String serializedValue = objectValue.getValueSerialized();
    String expectedSerializedValue = jsonSerializable.toExpectedJsonString(JsonDataFormatConfigurator.DATE_FORMAT);

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode actualJsonTree = objectMapper.readTree(serializedValue);
    JsonNode expectedJsonTree = objectMapper.readTree(expectedSerializedValue);
    // JsonNode#equals makes a deep comparison
    Assert.assertEquals(expectedJsonTree, actualJsonTree);
  }

  @Test
  public void testBuiltinFormatDoesNotApply() throws JsonProcessingException, IOException {
    Date date = new Date(ONE_DAY_IN_MILLIS * 10); // 10th of January 1970
    JsonSerializable jsonSerializable = new JsonSerializable(date);

    final ProcessInstance pi = runtimeService.startProcessInstanceByKey("testProcess");

    runtimeService.setVariable(pi.getId(),
      "jsonSerializable",
      Variables.objectValue(jsonSerializable).serializationDataFormat(SerializationDataFormats.JSON).create());

    ObjectValue objectValue = runtimeService.getVariableTyped(pi.getId(), "jsonSerializable", false);

    String serializedValue = objectValue.getValueSerialized();
    String expectedSerializedValue = jsonSerializable.toExpectedJsonString();

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode actualJsonTree = objectMapper.readTree(serializedValue);
    JsonNode expectedJsonTree = objectMapper.readTree(expectedSerializedValue);
    // JsonNode#equals makes a deep comparison
    Assert.assertEquals(expectedJsonTree, actualJsonTree);
  }
}
