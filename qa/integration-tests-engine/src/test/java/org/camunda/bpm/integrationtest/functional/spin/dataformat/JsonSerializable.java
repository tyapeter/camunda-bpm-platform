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
package org.camunda.bpm.integrationtest.functional.spin.dataformat;

import java.text.DateFormat;
import java.util.Date;

/**
 * @author Thorben Lindhauer
 *
 */
public class JsonSerializable {

  public static final long ONE_DAY_IN_MILLIS = 1000 * 60 * 60 * 24;

  private Date dateProperty;

  public JsonSerializable() {

  }

  public JsonSerializable(Date dateProperty) {
    this.dateProperty = dateProperty;
  }

  public Date getDateProperty() {
    return dateProperty;
  }

  public void setDateProperty(Date dateProperty) {
    this.dateProperty = dateProperty;
  }

  /**
   * Serializes the value according to the given date format
   */
  public String toExpectedJsonString(DateFormat dateFormat) {
    StringBuilder jsonBuilder = new StringBuilder();

    jsonBuilder.append("{\"dateProperty\":\"");
    jsonBuilder.append(dateFormat.format(dateProperty));
    jsonBuilder.append("\"}");

    return jsonBuilder.toString();
  }

  /**
   * Serializes the value as milliseconds
   */
  public String toExpectedJsonString() {
    StringBuilder jsonBuilder = new StringBuilder();

    jsonBuilder.append("{\"dateProperty\":");
    jsonBuilder.append(Long.toString(dateProperty.getTime()));
    jsonBuilder.append("}");

    return jsonBuilder.toString();
  }
}
