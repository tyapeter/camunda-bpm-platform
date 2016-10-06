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

package org.camunda.bpm.engine.impl.batch.deletion;

import org.camunda.bpm.engine.impl.batch.AbstractProcessInstanceBatchConfiguration;

import java.util.List;

/**
 * Configuration object that is passed to the Job that will actually perform execution of
 * deletion.
 *
 * This object will be serialized and persisted as run will be performed asynchronously.
 *
 * @see org.camunda.bpm.engine.impl.batch.deletion.DeleteProcessInstanceBatchConfigurationJsonConverter
 * @author Askar Akhmerov
 */
public class DeleteProcessInstanceBatchConfiguration extends AbstractProcessInstanceBatchConfiguration {
  protected String deleteReason;

  public static DeleteProcessInstanceBatchConfiguration create(List<String> processInstanceIds, String deleteReason) {
    DeleteProcessInstanceBatchConfiguration result = new DeleteProcessInstanceBatchConfiguration();
    result.setProcessInstanceIds(processInstanceIds);
    result.setDeleteReason(deleteReason);
    return result;
  }

  public String getDeleteReason() {
    return deleteReason;
  }

  public void setDeleteReason(String deleteReason) {
    this.deleteReason = deleteReason;
  }

}
