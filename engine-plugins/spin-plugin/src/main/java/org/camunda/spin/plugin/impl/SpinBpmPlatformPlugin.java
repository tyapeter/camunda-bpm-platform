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
package org.camunda.spin.plugin.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.camunda.bpm.application.AbstractProcessApplication;
import org.camunda.bpm.application.ProcessApplicationInterface;
import org.camunda.bpm.container.impl.plugin.BpmPlatformPlugin;
import org.camunda.bpm.engine.impl.variable.serializer.DefaultVariableSerializers;
import org.camunda.bpm.engine.impl.variable.serializer.TypedValueSerializer;
import org.camunda.bpm.engine.impl.variable.serializer.VariableSerializers;
import org.camunda.spin.DataFormats;
import org.camunda.spin.spi.DataFormat;

/**
 * @author Thorben Lindhauer
 *
 */
public class SpinBpmPlatformPlugin implements BpmPlatformPlugin {

  public void postProcessApplicationDeploy(ProcessApplicationInterface processApplication) {
    ProcessApplicationInterface rawPa = processApplication.getRawObject();
    if (rawPa instanceof AbstractProcessApplication) {
      initializeVariableSerializers((AbstractProcessApplication) rawPa);
    }
    else {
      // TODO: log in this case?
    }
  }

  protected void initializeVariableSerializers(AbstractProcessApplication abstractProcessApplication) {
    VariableSerializers paVariableSerializers = abstractProcessApplication.getVariableSerializers();

    paVariableSerializers = new DefaultVariableSerializers();
    for (TypedValueSerializer<?> serializer : lookupSpinSerializers(abstractProcessApplication.getProcessApplicationClassloader())) {
      paVariableSerializers.addSerializer(serializer);
    }

    abstractProcessApplication.setVariableSerializers(paVariableSerializers);
  }

  //TODO: consolidate with code in SpinProcessEnginePlugin
  protected List<TypedValueSerializer<?>> lookupSpinSerializers(ClassLoader classLoader) {
    List<TypedValueSerializer<?>> serializers = new ArrayList<TypedValueSerializer<?>>();

    DataFormats paDataFormats = new DataFormats();
    paDataFormats.registerDataFormats(classLoader);

    Set<DataFormat<?>> availableDataFormats = paDataFormats.getAllAvailableDataFormats();
    for (DataFormat<?> dataFormat : availableDataFormats) {
      serializers.add(new SpinObjectValueSerializer("spin://"+dataFormat.getName(), dataFormat));
    }

    // TODO: this should not use DateFormats.json but should use paDataFormats to determine a json or xml
    // serializer
    if(DataFormats.json() != null) {
      serializers.add(new JsonValueSerializer());
    }
    if(DataFormats.xml() != null){
      serializers.add(new XmlValueSerializer());
    }

    return serializers;
  }

}
