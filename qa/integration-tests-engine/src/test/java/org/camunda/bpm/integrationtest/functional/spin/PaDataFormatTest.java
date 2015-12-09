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

import static org.camunda.bpm.engine.variable.Variables.serializedObjectValue;

import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.context.ProcessApplicationContextUtil;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.camunda.bpm.integrationtest.functional.spin.dataformat.Foo;
import org.camunda.bpm.integrationtest.functional.spin.dataformat.FooDataFormat;
import org.camunda.bpm.integrationtest.functional.spin.dataformat.FooDataFormatProvider;
import org.camunda.bpm.integrationtest.functional.spin.dataformat.FooSpin;
import org.camunda.bpm.integrationtest.util.AbstractFoxPlatformIntegrationTest;
import org.camunda.spin.spi.DataFormatProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Thorben Lindhauer
 *
 */
@RunWith(Arquillian.class)
public class PaDataFormatTest extends AbstractFoxPlatformIntegrationTest  {

  @Deployment
  public static WebArchive createDeployment() {
    return initWebArchiveDeployment()
        .addAsResource("org/camunda/bpm/integrationtest/oneTaskProcess.bpmn")
        .addClass(Foo.class)
        .addClass(FooDataFormat.class)
        .addClass(FooDataFormatProvider.class)
        .addClass(FooSpin.class)
        .addAsServiceProvider(DataFormatProvider.class, FooDataFormatProvider.class);
  }

  @Test
  public void customFormatCanBeUsedForVariableSerialization() {
    final ProcessInstance pi = runtimeService.startProcessInstanceByKey("testProcess",
        Variables.createVariables()
          .putValue("serializedObject",
              serializedObjectValue("foo")
              .serializationDataFormat(FooDataFormat.NAME)
              .objectTypeName(Foo.class.getName())));

    final String deployment = repositoryService.createDeploymentQuery().singleResult().getId();

    ObjectValue objectValue = null;

    objectValue = processEngineConfiguration.getCommandExecutorTxRequired().execute(new Command<ObjectValue>() {

      @Override
      public ObjectValue execute(CommandContext commandContext) {
        try {
          Context.setCurrentProcessApplication(ProcessApplicationContextUtil.getTargetProcessApplication(deployment));
          return runtimeService.getVariableTyped(pi.getId(), "serializedObject", true);
        } finally {
          Context.removeCurrentProcessApplication();
        }
      }

    });

    Object value = objectValue.getValue();
    Assert.assertNotNull(value);
    Assert.assertTrue(value instanceof Foo);
  }

}
