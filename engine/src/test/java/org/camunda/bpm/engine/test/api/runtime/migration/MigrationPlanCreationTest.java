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
package org.camunda.bpm.engine.test.api.runtime.migration;

import static org.camunda.bpm.engine.test.util.MigrationPlanAssert.assertThat;
import static org.camunda.bpm.engine.test.util.MigrationPlanAssert.migrate;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * @author Thorben Lindhauer
 *
 */
public class MigrationPlanCreationTest {

  protected ProcessEngineRule rule = new ProcessEngineRule();
  protected MigrationTestRule testHelper = new MigrationTestRule(rule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(rule).around(testHelper);

  @Test
  public void testExplicitInstructionGeneration() {

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService()
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    assertThat(migrationPlan)
      .hasSourceProcessDefinition(sourceProcessDefinition)
      .hasTargetProcessDefinition(targetProcessDefinition)
      .hasInstructions(
        migrate("userTask").to("userTask")
      );
  }

  @Test
  public void testMigrateNonExistingSourceDefinition() {
    ProcessDefinition processDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    try {
      rule.getRuntimeService()
        .createMigrationPlan("aNonExistingProcDefId", processDefinition.getId())
        .mapActivities("userTask", "userTask")
        .build();
      Assert.fail("Should not succeed");
    } catch (BadUserRequestException e) {
      Assert.assertThat(e.getMessage(), CoreMatchers.containsString("source process definition with id aNonExistingProcDefId does not exist"));
    }
  }

  @Test
  public void testMigrateNullSourceDefinition() {
    ProcessDefinition processDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    try {
      rule.getRuntimeService()
        .createMigrationPlan(null, processDefinition.getId())
        .mapActivities("userTask", "userTask")
        .build();
      Assert.fail("Should not succeed");
    } catch (BadUserRequestException e) {
      Assert.assertThat(e.getMessage(), CoreMatchers.containsString("sourceProcessDefinitionId is null"));
    }
  }

  @Test
  public void testMigrateNonExistingTargetDefinition() {
    ProcessDefinition processDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    try {
      rule.getRuntimeService()
        .createMigrationPlan(processDefinition.getId(), "aNonExistingProcDefId")
        .mapActivities("userTask", "userTask")
        .build();
      Assert.fail("Should not succeed");
    } catch (BadUserRequestException e) {
      Assert.assertThat(e.getMessage(), CoreMatchers.containsString("target process definition with id aNonExistingProcDefId does not exist"));
    }
  }

  @Test
  public void testMigrateNullTargetDefinition() {
    ProcessDefinition processDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    try {
      rule.getRuntimeService()
        .createMigrationPlan(processDefinition.getId(), null)
        .mapActivities("userTask", "userTask")
        .build();
      Assert.fail("Should not succeed");
    } catch (BadUserRequestException e) {
      Assert.assertThat(e.getMessage(), CoreMatchers.containsString("targetProcessDefinitionId is null"));
    }
  }

  @Test
  public void testMigrateNonExistingSourceActivityId() {
    ProcessDefinition sourceDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    try {
      rule.getRuntimeService()
        .createMigrationPlan(sourceDefinition.getId(), targetDefinition.getId())
        .mapActivities("thisActivityDoesNotExist", "userTask")
        .build();
      Assert.fail("Should not succeed");
    } catch (BadUserRequestException e) {
      Assert.assertThat(e.getMessage(), CoreMatchers.containsString("source activity does not exist"));
    }
  }

  @Test
  public void testMigrateNullSourceActivityId() {
    ProcessDefinition sourceDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    try {
      rule.getRuntimeService()
        .createMigrationPlan(sourceDefinition.getId(), targetDefinition.getId())
        .mapActivities(null, "userTask")
        .build();
      Assert.fail("Should not succeed");
    } catch (BadUserRequestException e) {
      Assert.assertThat(e.getMessage(), CoreMatchers.containsString("source activity id and target activity id must not be null"));
    }
  }

  @Test
  public void testMigrateNonExistingTargetActivityId() {
    ProcessDefinition sourceDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    try {
      rule.getRuntimeService()
        .createMigrationPlan(sourceDefinition.getId(), targetDefinition.getId())
        .mapActivities("userTask", "thisActivityDoesNotExist")
        .build();
      Assert.fail("Should not succeed");
    } catch (BadUserRequestException e) {
      Assert.assertThat(e.getMessage(), CoreMatchers.containsString("target activity does not exist"));
    }
  }

  @Test
  public void testMigrateNullTargetActivityId() {
    ProcessDefinition sourceDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);

    try {
      rule.getRuntimeService()
        .createMigrationPlan(sourceDefinition.getId(), targetDefinition.getId())
        .mapActivities("userTask", null)
        .build();
      Assert.fail("Should not succeed");
    } catch (BadUserRequestException e) {
      Assert.assertThat(e.getMessage(), CoreMatchers.containsString("source activity id and target activity id must not be null"));
    }
  }

  @Test
  public void testMigrateTaskToHigherScope() {
    ProcessDefinition sourceDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetDefinition = testHelper.deploy(ProcessModels.SUBPROCESS_PROCESS);

    MigrationPlan migrationPlan = rule.getRuntimeService()
      .createMigrationPlan(sourceDefinition.getId(), targetDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    assertThat(migrationPlan)
      .hasSourceProcessDefinition(sourceDefinition)
      .hasTargetProcessDefinition(targetDefinition)
      .hasInstructions(
        migrate("userTask").to("userTask")
      );
  }

  @Test
  public void testMigrateToDifferentActivityType() {

    ProcessDefinition sourceDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetDefinition = testHelper.deploy(ProcessModels.ONE_RECEIVE_TASK_PROCESS);

    try {
      rule.getRuntimeService()
        .createMigrationPlan(sourceDefinition.getId(), targetDefinition.getId())
        .mapActivities("userTask", "receiveTask")
        .build();
      Assert.fail("Should not succeed");
    } catch (BadUserRequestException e) {
      Assert.assertThat(e.getMessage(), CoreMatchers.containsString("Invalid migration instruction"));
    }
  }

}
