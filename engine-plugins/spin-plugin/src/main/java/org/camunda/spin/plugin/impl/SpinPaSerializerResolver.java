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
import org.camunda.bpm.application.ProcessApplicationReference;
import org.camunda.bpm.application.ProcessApplicationUnavailableException;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.variable.serializer.DefaultVariableSerializers;
import org.camunda.bpm.engine.impl.variable.serializer.TypedValueSerializer;
import org.camunda.bpm.engine.impl.variable.serializer.VariableSerializerResolver;
import org.camunda.bpm.engine.impl.variable.serializer.VariableSerializers;
import org.camunda.spin.DataFormats;
import org.camunda.spin.spi.DataFormat;

/**
 * @author Thorben Lindhauer
 *
 */
public class SpinPaSerializerResolver implements VariableSerializerResolver {

  public VariableSerializers resolve() {
    ProcessApplicationReference currentProcessApplication = Context.getCurrentProcessApplication();

    // TODO: initialization requires synchronization or must be done on PA startup (but this is probably not possible;
    // hen-egg-problem between engine plugin and PA?)
    // something like a ProcessApplicationPlugin would be nice
    if (currentProcessApplication != null) {
      try {
        ProcessApplicationInterface rawPa = currentProcessApplication.getProcessApplication().getRawObject();
        if (rawPa instanceof AbstractProcessApplication) {
          AbstractProcessApplication abstractRawPa = (AbstractProcessApplication) rawPa;
          VariableSerializers paVariableSerializers = abstractRawPa.getVariableSerializers();

          if (paVariableSerializers == null) {
            paVariableSerializers = new DefaultVariableSerializers();
            abstractRawPa.setVariableSerializers(paVariableSerializers);
            for (TypedValueSerializer<?> serializer : lookupSpinSerializers(abstractRawPa.getProcessApplicationClassloader())) {
              paVariableSerializers.addSerializer(serializer);
            }
          }

          return paVariableSerializers;
        }
      } catch (ProcessApplicationUnavailableException e) {
        // TODO: deal with exception properly
        throw new RuntimeException(e);
      }
    }

    return new DefaultVariableSerializers();
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
