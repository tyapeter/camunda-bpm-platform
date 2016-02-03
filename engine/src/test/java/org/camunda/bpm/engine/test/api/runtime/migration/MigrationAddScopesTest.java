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

import static org.camunda.bpm.engine.test.util.ActivityInstanceAssert.assertThat;
import static org.camunda.bpm.engine.test.util.ActivityInstanceAssert.describeActivityInstanceTree;
import static org.camunda.bpm.engine.test.util.ExecutionAssert.assertThat;
import static org.camunda.bpm.engine.test.util.ExecutionAssert.describeExecutionTree;
import static org.camunda.bpm.engine.test.util.ExecutionAssert.hasProcessDefinitionId;

import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ExecutionTree;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * @author Thorben Lindhauer
 *
 */
public class MigrationAddScopesTest {

  protected ProcessEngineRule rule = new ProcessEngineRule();
  protected MigrationTestRule testHelper = new MigrationTestRule(rule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(rule).around(testHelper);


  @Test
  public void testScopeUserTaskMigration() {
    // given
    testHelper.deploy("scopeTask.bpmn20.xml", ProcessModels.SCOPE_TASK_PROCESS);
    testHelper.deploy("scopeTaskSubProcess.bpmn20.xml", ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS);

    ProcessDefinition sourceProcessDefinition = testHelper.findProcessDefinition("UserTaskProcess", 1);
    ProcessDefinition targetProcessDefinition = testHelper.findProcessDefinition("SubProcess", 1);

    MigrationPlan migrationPlan = rule.getRuntimeService()
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    ProcessInstance processInstance = rule.getRuntimeService().startProcessInstanceById(sourceProcessDefinition.getId());
    ActivityInstance activityInstance = rule.getRuntimeService().getActivityInstance(processInstance.getId());

    // when
    rule.getRuntimeService().executeMigrationPlan(migrationPlan, Arrays.asList(processInstance.getId()));

    // then
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());
    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope().id(processInstance.getId())
        .child(null).scope()
          .child("userTask").scope().id(activityInstance.getActivityInstances("userTask")[0].getExecutionIds()[0])
      .done());

    assertThat(executionTree).matches(hasProcessDefinitionId(targetProcessDefinition.getId()));

    ActivityInstance updatedTree = rule.getRuntimeService().getActivityInstance(processInstance.getId());
    assertThat(updatedTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .beginScope("subProcess")
            .activity("userTask")
        .done());

    Task migratedTask = rule.getTaskService().createTaskQuery().singleResult();
    Assert.assertNotNull(migratedTask);
    Assert.assertEquals(targetProcessDefinition.getId(), migratedTask.getProcessDefinitionId());

    // and it is possible to successfully complete the migrated instance
    rule.getTaskService().complete(migratedTask.getId());
    testHelper.assertProcessEnded(processInstance.getId());
  }

  @Test
  public void testConcurrentScopeUserTaskMigration() {
    // given
    testHelper.deploy("scopeTask.bpmn20.xml", ProcessModels.PARALLEL_SCOPE_TASKS);
    testHelper.deploy("scopeTaskSubProcess.bpmn20.xml", ProcessModels.PARALLEL_SCOPE_TASKS_SUB_PROCESS);

    ProcessDefinition sourceProcessDefinition = testHelper.findProcessDefinition("ParallelGatewayProcess", 1);
    ProcessDefinition targetProcessDefinition = testHelper.findProcessDefinition("ParallelGatewaySubProcess", 1);

    MigrationPlan migrationPlan = rule.getRuntimeService()
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = rule.getRuntimeService().startProcessInstanceById(sourceProcessDefinition.getId());
    ActivityInstance activityInstance = rule.getRuntimeService().getActivityInstance(processInstance.getId());

    // when
    rule.getRuntimeService().executeMigrationPlan(migrationPlan, Arrays.asList(processInstance.getId()));

    // then
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());
    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope().id(processInstance.getId())
        .child(null).scope()
          .child(null).concurrent().noScope()
            .child("userTask1").scope().id(activityInstance.getActivityInstances("userTask1")[0].getExecutionIds()[0]).up().up()
          .child(null).concurrent().noScope()
            .child("userTask2").scope().id(activityInstance.getActivityInstances("userTask2")[0].getExecutionIds()[0])
      .done());
    assertThat(executionTree).matches(hasProcessDefinitionId(targetProcessDefinition.getId()));

    ActivityInstance updatedTree = rule.getRuntimeService().getActivityInstance(processInstance.getId());
    assertThat(updatedTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .beginScope("subProcess")
            .activity("userTask1")
            .activity("userTask2")
        .done());

    List<Task> migratedTasks = rule.getTaskService().createTaskQuery().list();
    Assert.assertEquals(2, migratedTasks.size());

    for (Task migratedTask : migratedTasks) {
      Assert.assertEquals(targetProcessDefinition.getId(), migratedTask.getProcessDefinitionId());
    }

    // and it is possible to successfully complete the migrated instance
    for (Task migratedTask : migratedTasks) {
      rule.getTaskService().complete(migratedTask.getId());
    }
    testHelper.assertProcessEnded(processInstance.getId());
  }

  @Test
  public void testUserTaskMigration() {
    // given
    testHelper.deploy("scopeTask.bpmn20.xml", ProcessModels.ONE_TASK_PROCESS);
    testHelper.deploy("scopeTaskSubProcess.bpmn20.xml", ProcessModels.SUBPROCESS_PROCESS);

    ProcessDefinition sourceProcessDefinition = testHelper.findProcessDefinition("UserTaskProcess", 1);
    ProcessDefinition targetProcessDefinition = testHelper.findProcessDefinition("SubProcess", 1);

    MigrationPlan migrationPlan = rule.getRuntimeService()
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    ProcessInstance processInstance = rule.getRuntimeService().startProcessInstanceById(sourceProcessDefinition.getId());

    // when
    rule.getRuntimeService().executeMigrationPlan(migrationPlan, Arrays.asList(processInstance.getId()));

    // then
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());
    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope().id(processInstance.getId())
        .child("userTask").scope()
      .done());
    assertThat(executionTree).matches(hasProcessDefinitionId(targetProcessDefinition.getId()));

    ActivityInstance updatedTree = rule.getRuntimeService().getActivityInstance(processInstance.getId());
    assertThat(updatedTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .beginScope("subProcess")
            .activity("userTask")
        .done());

    Task migratedTask = rule.getTaskService().createTaskQuery().singleResult();
    Assert.assertNotNull(migratedTask);
    Assert.assertEquals(targetProcessDefinition.getId(), migratedTask.getProcessDefinitionId());

    // and it is possible to successfully complete the migrated instance
    rule.getTaskService().complete(migratedTask.getId());
    testHelper.assertProcessEnded(processInstance.getId());
  }

  @Test
  public void testConcurrentUserTaskMigration() {
    // given
    testHelper.deploy("scopeTask.bpmn20.xml", ProcessModels.PARALLEL_GATEWAY_PROCESS);
    testHelper.deploy("scopeTaskSubProcess.bpmn20.xml", ProcessModels.PARALLEL_GATEWAY_SUBPROCESS_PROCESS);

    ProcessDefinition sourceProcessDefinition = testHelper.findProcessDefinition("ParallelGatewayProcess", 1);
    ProcessDefinition targetProcessDefinition = testHelper.findProcessDefinition("ParallelGatewaySubProcess", 1);

    MigrationPlan migrationPlan = rule.getRuntimeService()
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    ProcessInstance processInstance = rule.getRuntimeService().startProcessInstanceById(sourceProcessDefinition.getId());

    // when
    rule.getRuntimeService().executeMigrationPlan(migrationPlan, Arrays.asList(processInstance.getId()));

    // then
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());
    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope().id(processInstance.getId())
        .child(null).scope()
          .child("userTask1").concurrent().noScope().up()
          .child("userTask2").concurrent().noScope()
      .done());
    assertThat(executionTree).matches(hasProcessDefinitionId(targetProcessDefinition.getId()));

    ActivityInstance updatedTree = rule.getRuntimeService().getActivityInstance(processInstance.getId());
    assertThat(updatedTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .beginScope("subProcess")
            .activity("userTask1")
            .activity("userTask2")
        .done());

    List<Task> migratedTasks = rule.getTaskService().createTaskQuery().list();
    Assert.assertEquals(2, migratedTasks.size());

    for (Task migratedTask : migratedTasks) {
      Assert.assertEquals(targetProcessDefinition.getId(), migratedTask.getProcessDefinitionId());
    }

    // and it is possible to successfully complete the migrated instance
    for (Task migratedTask : migratedTasks) {
      rule.getTaskService().complete(migratedTask.getId());
    }
    testHelper.assertProcessEnded(processInstance.getId());
  }

  @Test
  public void testNestedScopesMigration1() {
    // given
    testHelper.deploy("subProcess.bpmn20.xml", ProcessModels.SUBPROCESS_PROCESS);
    testHelper.deploy("nestedSubProcess.bpmn20.xml", ProcessModels.NESTED_SUBPROCESS_PROCESS);

    ProcessDefinition sourceProcessDefinition = testHelper.findProcessDefinition("SubProcess", 1);
    ProcessDefinition targetProcessDefinition = testHelper.findProcessDefinition("NestedSubProcess", 1);

    MigrationPlan migrationPlan = rule.getRuntimeService()
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("subProcess", "outerSubProcess")
      .build();

    ProcessInstance processInstance = rule.getRuntimeService().startProcessInstanceById(sourceProcessDefinition.getId());
    ActivityInstance activityInstance = rule.getRuntimeService().getActivityInstance(processInstance.getId());

    // when
    rule.getRuntimeService().executeMigrationPlan(migrationPlan, Arrays.asList(processInstance.getId()));

    // then
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());
    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope().id(processInstance.getId())
        .child(null).scope().id(activityInstance.getActivityInstances("subProcess")[0].getExecutionIds()[0])
          .child("userTask").scope()
      .done());
    assertThat(executionTree).matches(hasProcessDefinitionId(targetProcessDefinition.getId()));

    ActivityInstance updatedTree = rule.getRuntimeService().getActivityInstance(processInstance.getId());
    assertThat(updatedTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .beginScope("outerSubProcess")
            .beginScope("innerSubProcess")
              .activity("userTask")
        .done());

    Task migratedTask = rule.getTaskService().createTaskQuery().singleResult();
    Assert.assertNotNull(migratedTask);
    Assert.assertEquals(targetProcessDefinition.getId(), migratedTask.getProcessDefinitionId());

    // and it is possible to successfully complete the migrated instance
    rule.getTaskService().complete(migratedTask.getId());
    testHelper.assertProcessEnded(processInstance.getId());
  }

  @Test
  public void testNestedScopesMigration2() {
    // given
    testHelper.deploy("subProcess.bpmn20.xml", ProcessModels.SUBPROCESS_PROCESS);
    testHelper.deploy("nestedSubProcess.bpmn20.xml", ProcessModels.NESTED_SUBPROCESS_PROCESS);

    ProcessDefinition sourceProcessDefinition = testHelper.findProcessDefinition("SubProcess", 1);
    ProcessDefinition targetProcessDefinition = testHelper.findProcessDefinition("NestedSubProcess", 1);

    MigrationPlan migrationPlan = rule.getRuntimeService()
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("subProcess", "innerSubProcess")
      .build();

    ProcessInstance processInstance = rule.getRuntimeService().startProcessInstanceById(sourceProcessDefinition.getId());
    ActivityInstance activityInstance = rule.getRuntimeService().getActivityInstance(processInstance.getId());

    // when
    rule.getRuntimeService().executeMigrationPlan(migrationPlan, Arrays.asList(processInstance.getId()));

    // then
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());
    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope().id(processInstance.getId())
        .child(null).scope()
          .child("userTask").scope().id(activityInstance.getActivityInstances("subProcess")[0].getExecutionIds()[0])
      .done());
    assertThat(executionTree).matches(hasProcessDefinitionId(targetProcessDefinition.getId()));

    ActivityInstance updatedTree = rule.getRuntimeService().getActivityInstance(processInstance.getId());
    assertThat(updatedTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .beginScope("outerSubProcess")
            .beginScope("innerSubProcess")
              .activity("userTask")
        .done());

    Task migratedTask = rule.getTaskService().createTaskQuery().singleResult();
    Assert.assertNotNull(migratedTask);
    Assert.assertEquals(targetProcessDefinition.getId(), migratedTask.getProcessDefinitionId());

    // and it is possible to successfully complete the migrated instance
    rule.getTaskService().complete(migratedTask.getId());
    testHelper.assertProcessEnded(processInstance.getId());
  }

  @Test
  public void testMultipleInstancesOfScope() {
    testHelper.deploy("subProcess.bpmn20.xml", ProcessModels.SUBPROCESS_PROCESS);
    testHelper.deploy("nestedSubProcess.bpmn20.xml", ProcessModels.NESTED_SUBPROCESS_PROCESS);

    ProcessDefinition sourceProcessDefinition = testHelper.findProcessDefinition("SubProcess", 1);
    ProcessDefinition targetProcessDefinition = testHelper.findProcessDefinition("NestedSubProcess", 1);

    MigrationPlan migrationPlan = rule.getRuntimeService()
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("subProcess", "outerSubProcess")
      .build();

    ProcessInstance processInstance = rule.getRuntimeService().createProcessInstanceById(sourceProcessDefinition.getId())
        .startBeforeActivity("subProcess")
        .startBeforeActivity("subProcess")
        .execute();

    // when
    rule.getRuntimeService().executeMigrationPlan(migrationPlan, Arrays.asList(processInstance.getId()));

    // then
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());
    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope().id(processInstance.getId())
        .child(null).concurrent().noScope()
          .child(null).scope()
            .child("userTask").scope().up().up().up()
        .child(null).concurrent().noScope()
          .child(null).scope()
            .child("userTask").scope()
      .done());
    assertThat(executionTree).matches(hasProcessDefinitionId(targetProcessDefinition.getId()));

    ActivityInstance updatedTree = rule.getRuntimeService().getActivityInstance(processInstance.getId());
    assertThat(updatedTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .beginScope("outerSubProcess")
            .beginScope("innerSubProcess")
              .activity("userTask")
            .endScope()
          .endScope()
          .beginScope("outerSubProcess")
            .beginScope("innerSubProcess")
              .activity("userTask")
        .done());

    List<Task> migratedTasks = rule.getTaskService().createTaskQuery().list();
    Assert.assertEquals(2, migratedTasks.size());

    for (Task migratedTask : migratedTasks) {
      Assert.assertEquals(targetProcessDefinition.getId(), migratedTask.getProcessDefinitionId());
    }

    // and it is possible to successfully complete the migrated instance
    for (Task migratedTask : migratedTasks) {
      rule.getTaskService().complete(migratedTask.getId());
    }
    testHelper.assertProcessEnded(processInstance.getId());
  }

  @Test
  public void testChangeActivityId() {
    testHelper.deploy("scopeTask.bpmn20.xml", ProcessModels.PARALLEL_GATEWAY_PROCESS);
    testHelper.deploy("scopeTaskSubProcess.bpmn20.xml", ProcessModels.PARALLEL_GATEWAY_SUBPROCESS_PROCESS);

    ProcessDefinition sourceProcessDefinition = testHelper.findProcessDefinition("ParallelGatewayProcess", 1);
    ProcessDefinition targetProcessDefinition = testHelper.findProcessDefinition("ParallelGatewaySubProcess", 1);

    MigrationPlan migrationPlan = rule.getRuntimeService()
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask2")
      .build();

    ProcessInstance processInstance = rule.getRuntimeService()
        .createProcessInstanceById(sourceProcessDefinition.getId())
        .startBeforeActivity("userTask1")
        .execute();

    // when
    rule.getRuntimeService().executeMigrationPlan(migrationPlan, Arrays.asList(processInstance.getId()));

    // then
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());
    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope().id(processInstance.getId())
        .child("userTask2").scope()
      .done());
    assertThat(executionTree).matches(hasProcessDefinitionId(targetProcessDefinition.getId()));

    ActivityInstance updatedTree = rule.getRuntimeService().getActivityInstance(processInstance.getId());
    assertThat(updatedTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .beginScope("subProcess")
            .activity("userTask2")
        .done());

    Task migratedTask = rule.getTaskService().createTaskQuery().singleResult();
    Assert.assertNotNull(migratedTask);
    Assert.assertEquals(targetProcessDefinition.getId(), migratedTask.getProcessDefinitionId());

    // and it is possible to successfully complete the migrated instance
    rule.getTaskService().complete(migratedTask.getId());
    testHelper.assertProcessEnded(processInstance.getId());
  }

  @Test
  public void testChangeScopeActivityId() {
    testHelper.deploy("scopeTask.bpmn20.xml", ProcessModels.PARALLEL_SCOPE_TASKS);
    testHelper.deploy("scopeTaskSubProcess.bpmn20.xml", ProcessModels.PARALLEL_SCOPE_TASKS_SUB_PROCESS);

    ProcessDefinition sourceProcessDefinition = testHelper.findProcessDefinition("ParallelGatewayProcess", 1);
    ProcessDefinition targetProcessDefinition = testHelper.findProcessDefinition("ParallelGatewaySubProcess", 1);

    MigrationPlan migrationPlan = rule.getRuntimeService()
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask2")
      .build();

    ProcessInstance processInstance = rule.getRuntimeService()
        .createProcessInstanceById(sourceProcessDefinition.getId())
        .startBeforeActivity("userTask1")
        .execute();

    // when
    rule.getRuntimeService().executeMigrationPlan(migrationPlan, Arrays.asList(processInstance.getId()));

    // then
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstance.getId(), rule.getProcessEngine());
    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope().id(processInstance.getId())
        .child(null).scope()
          .child("userTask2").scope()
      .done());
    assertThat(executionTree).matches(hasProcessDefinitionId(targetProcessDefinition.getId()));

    ActivityInstance updatedTree = rule.getRuntimeService().getActivityInstance(processInstance.getId());
    assertThat(updatedTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .beginScope("subProcess")
            .activity("userTask2")
        .done());

    Task migratedTask = rule.getTaskService().createTaskQuery().singleResult();
    Assert.assertNotNull(migratedTask);
    Assert.assertEquals(targetProcessDefinition.getId(), migratedTask.getProcessDefinitionId());

    // and it is possible to successfully complete the migrated instance
    rule.getTaskService().complete(migratedTask.getId());
    testHelper.assertProcessEnded(processInstance.getId());
  }

  // TODO: test deletion of migrated instances
  // TODO: actually assert that listeners are invoked
}
