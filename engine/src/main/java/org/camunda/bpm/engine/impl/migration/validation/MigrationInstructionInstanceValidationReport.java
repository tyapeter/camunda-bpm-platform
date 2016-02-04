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
package org.camunda.bpm.engine.impl.migration.validation;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.impl.migration.instance.MigratingActivityInstance;
import org.camunda.bpm.engine.impl.migration.instance.MigratingProcessInstance;

/**
 * @author Thorben Lindhauer
 *
 */
public class MigrationInstructionInstanceValidationReport {

  protected MigratingProcessInstance migratingProcessInstance;
  protected List<ValidationFailure> validationFailures = new ArrayList<MigrationInstructionInstanceValidationReport.ValidationFailure>();

  public MigrationInstructionInstanceValidationReport(MigratingProcessInstance migratingProcessInstance) {
    this.migratingProcessInstance = migratingProcessInstance;
  }

  public void addValidationFailure(MigratingActivityInstance activityInstance, String errorMessage) {
    validationFailures.add(new ValidationFailure(activityInstance, errorMessage));
  }

  public void writeTo(StringBuilder sb) {
    sb.append("Migration plan is not valid for process:\n");

    for (ValidationFailure failure : validationFailures) {
      failure.writeTo(sb);
      sb.append("\n");
    }

  }

  public static class ValidationFailure {
    protected MigratingActivityInstance migratingInstance;
    protected String errorMessage;

    public ValidationFailure(MigratingActivityInstance migratingInstance, String errorMessage) {
      this.migratingInstance = migratingInstance;
      this.errorMessage = errorMessage;
    }

    public void writeTo(StringBuilder sb) {
      sb.append("ActivityInstance ");
      sb.append(migratingInstance.getActivityInstance().getId());
      sb.append(": ");
      sb.append(errorMessage);
    }
  }

  public boolean hasFailures() {
    return !validationFailures.isEmpty();
  }

  public MigratingProcessInstance getMigratingProcessInstance() {
    return migratingProcessInstance;
  }
}
