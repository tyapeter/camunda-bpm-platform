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
package org.camunda.spin.plugin;

import org.camunda.bpm.application.AbstractProcessApplication;
import org.camunda.bpm.application.ProcessApplicationInterface;
import org.camunda.bpm.application.ProcessApplicationReference;
import org.camunda.bpm.application.ProcessApplicationUnavailableException;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.core.model.PropertyKey;
import org.camunda.spin.DataFormats;
import org.camunda.spin.Spin;
import org.camunda.spin.json.SpinJsonNode;
import org.camunda.spin.spi.DataFormat;
import org.camunda.spin.xml.SpinXmlElement;

/**
 * @author Thorben Lindhauer
 *
 */
public class ProcessApplicationDataFormats {

  protected static final PropertyKey<DataFormats> DATA_FORMATS_KEY = new PropertyKey<DataFormats>("spin-data-formats");

  public static DataFormat<SpinXmlElement> xml() {
    return (DataFormat<SpinXmlElement>) getPaDataFormats()
        .getDataFormatByName(DataFormats.XML_DATAFORMAT_NAME);
  }

  public static DataFormat<SpinJsonNode> json() {
    return (DataFormat<SpinJsonNode>) getPaDataFormats()
        .getDataFormatByName(DataFormats.JSON_DATAFORMAT_NAME);
  }

  public static DataFormat<? extends Spin<?>> getDataFormat(String dataFormatName) {
    return getPaDataFormats().getDataFormatByName(dataFormatName);
  }

  protected static DataFormats getPaDataFormats() {
    // TODO: this may not rely on the context PA because
    // the lookup might happen out of context (e.g. via RuntimeService#getVariable or
    // on implicit update at the end of the transaction)
    ProcessApplicationReference currentProcessApplication = Context.getCurrentProcessApplication();

    if (currentProcessApplication != null) {
      try {
        ProcessApplicationInterface rawPa = currentProcessApplication.getProcessApplication().getRawObject();
        if (rawPa instanceof AbstractProcessApplication) {
          DataFormats paDataFormats = ((AbstractProcessApplication) rawPa).getPropertiesTyped().get(DATA_FORMATS_KEY);

          // lazy initialization
          if (paDataFormats == null) {
            paDataFormats = new DataFormats();
            paDataFormats.registerDataFormats(currentProcessApplication.getProcessApplication().getProcessApplicationClassloader());
            ((AbstractProcessApplication) rawPa).getPropertiesTyped().set(DATA_FORMATS_KEY, paDataFormats);
          }

          return paDataFormats;
        }
        else {
          return null;
        }

      } catch (ProcessApplicationUnavailableException e) {
        // TODO: deal with exception properly
        e.printStackTrace();
        return null;
      }
    }
    else {
      //TODO: or throw exception or delegate to default DataFormats instead
      return null;
    }
  }

  // TODO: this could also have a method that allows access outside of a command by handing in a PA(reference)


}
