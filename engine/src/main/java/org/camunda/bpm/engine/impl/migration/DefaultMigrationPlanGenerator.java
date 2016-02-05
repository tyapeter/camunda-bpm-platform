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
package org.camunda.bpm.engine.impl.migration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.engine.impl.migration.validation.MigrationActivityValidator;
import org.camunda.bpm.engine.impl.migration.validation.MigrationActivityValidators;
import org.camunda.bpm.engine.impl.migration.validation.MigrationInstructionValidator;
import org.camunda.bpm.engine.impl.migration.validation.MigrationInstructionValidators;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.migration.MigrationInstruction;

/**
 * @author Thorben Lindhauer
 *
 */
public class DefaultMigrationPlanGenerator implements MigrationInstructionGenerator {

  public static final List<MigrationActivityValidator> activityValidators = Arrays.asList(
    MigrationActivityValidators.SUPPORTED_ACTIVITY,
    MigrationActivityValidators.HAS_NO_BOUNDARY_EVENT,
    MigrationActivityValidators.NOT_MULTI_INSTANCE_CHILD
  );

  public static final List<MigrationInstructionValidator> instructionValidators = Arrays.asList(
    MigrationInstructionValidators.SAME_ID_VALIDATOR,
    MigrationInstructionValidators.AT_MOST_ONE_ADDITIONAL_SCOPE
  );

  @Override
  public List<MigrationInstruction> generate(ProcessDefinitionImpl sourceProcessDefinition, ProcessDefinitionImpl targetProcessDefinition) {
    List<ActivityImpl> availableSourceActivities = new ArrayList<ActivityImpl>();
    List<ActivityImpl> availableTargetActivities = new ArrayList<ActivityImpl>();

    collectAllActivityAvailableForMigration(sourceProcessDefinition, sourceProcessDefinition, availableSourceActivities);
    collectAllActivityAvailableForMigration(targetProcessDefinition, targetProcessDefinition, availableTargetActivities);

    return generateInstructionsForActivities(sourceProcessDefinition, availableSourceActivities, targetProcessDefinition, availableTargetActivities);
  }

  protected void collectAllActivityAvailableForMigration(ProcessDefinitionImpl processDefinition, ScopeImpl scope, List<ActivityImpl> activitiesAvailableForMigration) {
    for (ActivityImpl activity : scope.getActivities()) {
      if (canBeMigrated(activity, processDefinition)) {
        activitiesAvailableForMigration.add(activity);
        if (activity.isScope()) {
          collectAllActivityAvailableForMigration(processDefinition, activity, activitiesAvailableForMigration);
        }
      }
    }
  }

  protected boolean canBeMigrated(ActivityImpl activity, ProcessDefinitionImpl processDefinition) {
    for (MigrationActivityValidator activityValidator : activityValidators) {
      if (!activityValidator.canBeMigrated(activity, processDefinition)) {
        return false;
      }
    }
    return true;
  }

  protected List<MigrationInstruction> generateInstructionsForActivities(ProcessDefinitionImpl sourceProcessDefinition, List<ActivityImpl> availableSourceActivities, ProcessDefinitionImpl targetProcessDefinition, List<ActivityImpl> availableTargetActivities) {
    List<MigrationInstruction> migrationInstructions = new ArrayList<MigrationInstruction>();

    for (ActivityImpl availableSourceActivity : availableSourceActivities) {
      for (ActivityImpl availableTargetActivity : availableTargetActivities) {
        MigrationInstructionImpl instruction = new MigrationInstructionImpl(availableSourceActivity.getId(), availableTargetActivity.getId());
        if (isValidInstruction(instruction, sourceProcessDefinition, targetProcessDefinition)) {
          migrationInstructions.add(instruction);
        }
      }
    }

    return migrationInstructions;
  }

  protected boolean isValidInstruction(MigrationInstructionImpl instruction, ProcessDefinitionImpl sourceProcessDefinition, ProcessDefinitionImpl targetProcessDefinition) {
    for (MigrationInstructionValidator instructionValidator : instructionValidators) {
      if (!instructionValidator.isInstructionValid(instruction, sourceProcessDefinition, targetProcessDefinition)) {
        return false;
      }
    }
    return true;
  }

}
