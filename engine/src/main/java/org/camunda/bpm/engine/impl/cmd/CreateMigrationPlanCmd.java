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
package org.camunda.bpm.engine.impl.cmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.migration.MigrationInstructionImpl;
import org.camunda.bpm.engine.impl.migration.MigrationPlanBuilderImpl;
import org.camunda.bpm.engine.impl.migration.MigrationPlanImpl;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.camunda.bpm.engine.migration.MigrationInstruction;
import org.camunda.bpm.engine.migration.MigrationPlan;

/**
 * @author Thorben Lindhauer
 *
 */
public class CreateMigrationPlanCmd implements Command<MigrationPlan> {

  protected MigrationPlanBuilderImpl migrationBuilder;

  public CreateMigrationPlanCmd(MigrationPlanBuilderImpl migrationPlanBuilderImpl) {
    this.migrationBuilder = migrationPlanBuilderImpl;
  }

  @Override
  public MigrationPlan execute(CommandContext commandContext) {
    MigrationPlanImpl migrationPlan = new MigrationPlanImpl(
        migrationBuilder.getSourceProcessDefinitionId(),
        migrationBuilder.getTargetProcessDefinitionId());

    List<MigrationInstruction> instructions = new ArrayList<MigrationInstruction>();

    if (migrationBuilder.isMapEqualActivities()) {
      ProcessDefinitionImpl sourceProcessDefinition = commandContext.getProcessEngineConfiguration()
          .getDeploymentCache().findProcessDefinitionFromCache(migrationBuilder.getSourceProcessDefinitionId());

      ProcessDefinitionImpl targetProcessDefinition = commandContext.getProcessEngineConfiguration()
          .getDeploymentCache().findProcessDefinitionFromCache(migrationBuilder.getTargetProcessDefinitionId());

      for (ActivityImpl sourceActivity : sourceProcessDefinition.getActivities()) {
        ActivityImpl targetActivity = targetProcessDefinition.findActivity(sourceActivity.getId());
        // TODO: validate more?!, e.g. that they are in same scopes or ancestor/decendant scopes?!

        if (targetActivity != null) {
          instructions.add(new MigrationInstructionImpl(Arrays.asList(sourceActivity.getId()), Arrays.asList(targetActivity.getId())));
        }

      }
    }

    instructions.addAll(migrationBuilder.getExplicitMigrationInstructions());
    migrationPlan.setInstructions(instructions);

    return migrationPlan;
  }



}
