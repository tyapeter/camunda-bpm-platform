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
package org.camunda.bpm.engine.test.api.runtime.migration.cool;

import java.util.Arrays;

import org.camunda.bpm.engine.impl.migration.MigrateProcessInstanceCmd;
import org.camunda.bpm.engine.impl.migration.MigrationInstruction;
import org.camunda.bpm.engine.impl.migration.MigrationPlan;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;

/**
 * @author Thorben Lindhauer
 *
 */
public class MigrationTest extends PluggableProcessEngineTestCase {

  private static final String TEST_PROCESS_USER_TASK_V1 = "org/camunda/bpm/engine/test/api/runtime/migration/SetProcessDefinitionVersionCmdTest.testSetProcessDefinitionVersionWithTask.bpmn20.xml";
  private static final String TEST_PROCESS_USER_TASK_V2 = "org/camunda/bpm/engine/test/api/runtime/migration/SetProcessDefinitionVersionCmdTest.testSetProcessDefinitionVersionWithTaskV2.bpmn20.xml";


  @Deployment(resources = {TEST_PROCESS_USER_TASK_V1})
  public void testOneTaskProcessMigration() {
    String deploymentId = repositoryService.createDeployment().addClasspathResource(TEST_PROCESS_USER_TASK_V2).deploy().getId();

    ProcessDefinition sourceProcessDefinition =
        repositoryService.createProcessDefinitionQuery().processDefinitionKey("userTask").processDefinitionVersion(1).singleResult();
    ProcessDefinition targetProcessDefinition =
        repositoryService.createProcessDefinitionQuery().processDefinitionKey("userTask").processDefinitionVersion(2).singleResult();

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());

    // when
    MigrationPlan migrationPlan = new MigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId());
    MigrationInstruction instruction = new MigrationInstruction("waitState1", "waitState1");
    migrationPlan.setInstructions(Arrays.asList(instruction));

    processEngineConfiguration.getCommandExecutorTxRequired()
      .execute(new MigrateProcessInstanceCmd(migrationPlan, processInstance.getId()));

    // then
    ProcessInstance updatedInstance = runtimeService.createProcessInstanceQuery().singleResult();
    assertEquals(targetProcessDefinition.getId(), updatedInstance.getProcessDefinitionId());

    Task updatedTask = taskService.createTaskQuery().singleResult();
    assertEquals(targetProcessDefinition.getId(), updatedTask.getProcessDefinitionId());

    taskService.complete(updatedTask.getId());

    repositoryService.deleteDeployment(deploymentId, true);
  }
}
