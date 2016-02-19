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

import static org.camunda.bpm.engine.test.api.runtime.migration.ModifiableBpmnModelInstance.modify;
import static org.camunda.bpm.engine.test.util.ActivityInstanceAssert.describeActivityInstanceTree;
import static org.camunda.bpm.engine.test.util.ExecutionAssert.describeExecutionTree;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TimerEntity;
import org.camunda.bpm.engine.management.JobDefinition;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.EventSubscription;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MigrationBoundaryEventsTest {

  public static final String AFTER_BOUNDARY_TASK = "afterBoundary";
  public static final String MESSAGE_NAME = "Message";
  public static final String SIGNAL_NAME = "Signal";
  public static final String TIMER_DATE = "2016-02-11T12:13:14Z";
  public static final String ERROR_CODE = "Error";
  public static final String ESCALATION_CODE = "Escalation";

  protected ProcessEngineRule rule = new ProcessEngineRule();
  protected MigrationTestRule testHelper = new MigrationTestRule(rule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(rule).around(testHelper);

  protected RuntimeService runtimeService;
  protected TaskService taskService;

  @Before
  public void initServices() {
    runtimeService = rule.getRuntimeService();
    taskService = rule.getTaskService();
  }

  @Test
  public void testMigrateMessageBoundaryEventOnUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.ONE_TASK_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventOnUserTaskAndCorrelateMessage() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.ONE_TASK_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the message and successfully complete the migrated instance
    correlateMessageAndCompleteTasks(MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventOnScopeUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventOnScopeUserTaskAndCorrelateMessage() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the message and successfully complete the migrated instance
    correlateMessageAndCompleteTasks(MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventOnConcurrentUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask1", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope()
          .up().up()
          .child("userTask2").concurrent().noScope()
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstanceBeforeMigration("userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstanceBeforeMigration("userTask2").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventOnConcurrentUserTaskAndCorrelateMessage() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask1", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the message and successfully complete the migrated instance
    correlateMessageAndCompleteTasks(MESSAGE_NAME, AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventOnConcurrentScopeUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addMessageBoundaryEventWithUserTask("userTask1", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask2"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstanceBeforeMigration("userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstanceBeforeMigration("userTask2").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventOnConcurrentScopeUserTaskAndCorrelateMessage() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addMessageBoundaryEventWithUserTask("userTask1", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the message and successfully complete the migrated instance
    correlateMessageAndCompleteTasks(MESSAGE_NAME, AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventToSubProcess() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstanceBeforeMigration("subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventToSubProcessAndCorrelateMessage() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the message and successfully complete the migrated instance
    correlateMessageAndCompleteTasks(MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventToSubProcessWithScopeUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess"))
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstanceBeforeMigration("subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventToSubProcessWithScopeUserTaskAndCorrelateMessage() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the message and successfully complete the migrated instance
    correlateMessageAndCompleteTasks(MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventToParallelSubProcess() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess1", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("subProcess2", "subProcess2")
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess2"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess1", testHelper.getSingleActivityInstanceBeforeMigration("subProcess1").getId())
        .activity("userTask1", testHelper.getSingleActivityInstanceBeforeMigration("userTask1").getId())
        .endScope()
        .beginScope("subProcess2", testHelper.getSingleActivityInstanceBeforeMigration("subProcess2").getId())
        .activity("userTask2", testHelper.getSingleActivityInstanceBeforeMigration("userTask2").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMessageBoundaryEventToParallelSubProcessAndCorrelateMessage() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess1", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("subProcess2", "subProcess2")
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the message and successfully complete the migrated instance
    correlateMessageAndCompleteTasks(MESSAGE_NAME, AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventOnUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.ONE_TASK_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventOnUserTaskAndCorrelateSignal() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.ONE_TASK_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventOnScopeUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventOnScopeUserTaskAndCorrelateSignal() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventOnConcurrentUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask1", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope()
          .up().up()
          .child("userTask2").concurrent().noScope()
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstanceBeforeMigration("userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstanceBeforeMigration("userTask2").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventOnConcurrentUserTaskAndCorrelateSignal() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask1", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventOnConcurrentScopeUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addSignalBoundaryEventWithUserTask("userTask1", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask2"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstanceBeforeMigration("userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstanceBeforeMigration("userTask2").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventOnConcurrentScopeUserTaskAndCorrelateSignal() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addSignalBoundaryEventWithUserTask("userTask1", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventToSubProcess() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstanceBeforeMigration("subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventToSubProcessAndCorrelateSignal() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventToSubProcessWithScopeUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess"))
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstanceBeforeMigration("subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventToSubProcessWithScopeUserTaskAndCorrelateSignal() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventToParallelSubProcess() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess1", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("subProcess2", "subProcess2")
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess2"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess1", testHelper.getSingleActivityInstanceBeforeMigration("subProcess1").getId())
        .activity("userTask1", testHelper.getSingleActivityInstanceBeforeMigration("userTask1").getId())
        .endScope()
        .beginScope("subProcess2", testHelper.getSingleActivityInstanceBeforeMigration("subProcess2").getId())
        .activity("userTask2", testHelper.getSingleActivityInstanceBeforeMigration("userTask2").getId())
        .done());

    assertEventSubscriptionMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateSignalBoundaryEventToParallelSubProcessAndCorrelateSignal() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess1", "boundary", SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("subProcess2", "subProcess2")
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventOnUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.ONE_TASK_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertTimerJobMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventOnUserTaskAndTriggerTimer() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.ONE_TASK_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventOnScopeUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertTimerJobMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventOnScopeUserTaskAndTriggerTimer() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .mapActivities("boundary", "newBoundary")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventOnConcurrentUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask1", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope()
          .up().up()
          .child("userTask2").concurrent().noScope()
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstanceBeforeMigration("userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstanceBeforeMigration("userTask2").getId())
        .done());

    assertTimerJobMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventOnConcurrentUserTaskAndTriggerTimer() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask1", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventOnConcurrentScopeUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addTimerDateBoundaryEventWithUserTask("userTask1", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask2"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstanceBeforeMigration("userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstanceBeforeMigration("userTask2").getId())
        .done());

    assertTimerJobMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventOnConcurrentScopeUserTaskAndTriggerTimer() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addTimerDateBoundaryEventWithUserTask("userTask1", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventToSubProcess() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstanceBeforeMigration("subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertTimerJobMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventToSubProcessAndTriggerTimer() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventToSubProcessWithScopeUserTask() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess"))
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstanceBeforeMigration("subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertTimerJobMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventToSubProcessWithScopeUserTaskAndTriggerTimer() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventToParallelSubProcess() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess1", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("subProcess2", "subProcess2")
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess2"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess1", testHelper.getSingleActivityInstanceBeforeMigration("subProcess1").getId())
        .activity("userTask1", testHelper.getSingleActivityInstanceBeforeMigration("userTask1").getId())
        .endScope()
        .beginScope("subProcess2", testHelper.getSingleActivityInstanceBeforeMigration("subProcess2").getId())
        .activity("userTask2", testHelper.getSingleActivityInstanceBeforeMigration("userTask2").getId())
        .done());

    assertTimerJobMigrated("boundary", "newBoundary");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateTimerBoundaryEventToParallelSubProcessAndTriggerTimer() {
    // given
    BpmnModelInstance sourceTestProcess = modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess1", "boundary", TIMER_DATE, AFTER_BOUNDARY_TASK);
    BpmnModelInstance targetTestProcess = modify(sourceTestProcess)
      .changeElementId("boundary", "newBoundary");
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(sourceTestProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(targetTestProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
      .mapActivities("boundary", "newBoundary")
      .mapActivities("subProcess2", "subProcess2")
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testMigrateMultipleBoundaryEvents() {
    // given
    BpmnModelInstance testProcess = modify(ProcessModels.SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEvent("subProcess", "timerBoundary1", TIMER_DATE)
      .addMessageBoundaryEvent("subProcess", "messageBoundary1", MESSAGE_NAME)
      .addSignalBoundaryEvent("subProcess", "signalBoundary1", SIGNAL_NAME)
      .addTimerDateBoundaryEvent("userTask", "timerBoundary2", TIMER_DATE)
      .addMessageBoundaryEvent("userTask", "messageBoundary2", MESSAGE_NAME)
      .addSignalBoundaryEvent("userTask", "signalBoundary2", SIGNAL_NAME);

    ProcessDefinition sourceProcessDefinition = testHelper.deploy(testProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(testProcess);

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("timerBoundary1", "timerBoundary1")
      .mapActivities("signalBoundary1", "signalBoundary1")
      .mapActivities("userTask", "userTask")
      .mapActivities("messageBoundary2", "messageBoundary2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    testHelper.assertExecutionTreeAfterMigration()
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("subProcess"))
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivityBeforeMigration("userTask"))
          .done());

    testHelper.assertActivityTreeAfterMigration().hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstanceBeforeMigration("subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstanceBeforeMigration("userTask").getId())
        .done());

    assertEventSubscriptionsRemoved("messageBoundary1", "signalBoundary2");
    assertEventSubscriptionMigrated("signalBoundary1", "signalBoundary1");
    assertEventSubscriptionMigrated("messageBoundary2", "messageBoundary2");
    assertEventSubscriptionsCreated("messageBoundary1", "signalBoundary2");
    assertTimerJobsRemoved("timerBoundary2");
    assertTimerJobMigrated("timerBoundary1", "timerBoundary1");
    assertTimerJobsCreated("timerBoundary2");

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  // helper

  protected void completeTasks(String... taskKeys) {
    List<Task> tasks = taskService.createTaskQuery().taskDefinitionKeyIn(taskKeys).list();
    assertEquals(taskKeys.length, tasks.size());
    for (Task task : tasks) {
      assertNotNull(task);
      taskService.complete(task.getId());
    }
  }

  protected void correlateMessageAndCompleteTasks(String messageName, String... taskKeys) {
    runtimeService.createMessageCorrelation(messageName).correlate();
    completeTasks(taskKeys);
  }


  protected void sendSignalAndCompleteTasks(String signalName, String... taskKeys) {
    runtimeService.signalEventReceived(signalName);
    completeTasks(taskKeys);
  }

  protected void assertEventSubscriptionMigrated(String activityIdBefore, String activityIdAfter) {
    EventSubscription eventSubscriptionBefore = testHelper.snapshotBeforeMigration.getEventSubscriptionForActivityId(activityIdBefore);
    assertNotNull("Expected that an event subscription for activity '" + activityIdBefore + "' exists before migration", eventSubscriptionBefore);
    EventSubscription eventSubscriptionAfter = testHelper.snapshotAfterMigration.getEventSubscriptionForActivityId(activityIdAfter);
    assertNotNull("Expected that an event subscription for activity '" + activityIdAfter + "' exists after migration", eventSubscriptionAfter);

    assertEquals(eventSubscriptionBefore.getId(), eventSubscriptionAfter.getId());
    assertEquals(eventSubscriptionBefore.getEventType(), eventSubscriptionAfter.getEventType());
    assertEquals(eventSubscriptionBefore.getEventName(), eventSubscriptionAfter.getEventName());
  }

  protected void assertEventSubscriptionsRemoved(String... activityIds) {
    for (String activityId : activityIds) {
      assertEventSubscriptionRemoved(activityId);
    }
  }

  protected void assertEventSubscriptionRemoved(String activityId) {
    EventSubscription eventSubscriptionBefore = testHelper.snapshotBeforeMigration.getEventSubscriptionForActivityId(activityId);
    assertNotNull("Expected an event subscription for activity '" + activityId + "' before the migration", eventSubscriptionBefore);

    for (EventSubscription eventSubscription : testHelper.snapshotAfterMigration.getEventSubscriptions()) {
      if (eventSubscriptionBefore.getId().equals(eventSubscription.getId())) {
        fail("Expected event subscription '" + eventSubscriptionBefore.getId() + "' to be removed after migration");
      }
    }
  }

  protected void assertEventSubscriptionsCreated(String... activityIds) {
    for (String activityId : activityIds) {
      assertEventSubscriptionCreated(activityId);
    }
  }

  protected void assertEventSubscriptionCreated(String activityId) {
    EventSubscription eventSubscriptionAfter = testHelper.snapshotAfterMigration.getEventSubscriptionForActivityId(activityId);
    assertNotNull("Expected an event subscription for activity '" + activityId + "' after the migration", eventSubscriptionAfter);

    for (EventSubscription eventSubscription : testHelper.snapshotBeforeMigration.getEventSubscriptions()) {
      if (eventSubscriptionAfter.getId().equals(eventSubscription.getId())) {
        fail("Expected event subscription '" + eventSubscriptionAfter.getId() + "' to be first created after migration");
      }
    }
  }

  protected void assertTimerJobMigrated(String activityIdBefore, String activityIdAfter) {
    JobDefinition jobDefinitionBefore = testHelper.snapshotBeforeMigration.getJobDefinitionForActivityId(activityIdBefore);
    assertNotNull("Expected that a job definition for activity '" + activityIdBefore + "' exists before migration", jobDefinitionBefore);

    Job jobBefore = testHelper.snapshotBeforeMigration.getJobForDefinitionId(jobDefinitionBefore.getId());
    assertNotNull("Expected that a timer job for activity '" + activityIdBefore + "' exists before migration", jobBefore);
    assertTimerJob(jobBefore);

    JobDefinition jobDefinitionAfter = testHelper.snapshotAfterMigration.getJobDefinitionForActivityId(activityIdAfter);
    assertNotNull("Expected that a job definition for activity '" + activityIdAfter + "' exists after migration", jobDefinitionAfter);

    Job jobAfter = testHelper.snapshotAfterMigration.getJobForDefinitionId(jobDefinitionAfter.getId());
    assertNotNull("Expected that a timer job for activity '" + activityIdAfter + "' exists after migration", jobAfter);
    assertTimerJob(jobAfter);

    assertEquals(jobBefore.getId(), jobAfter.getId());
    assertEquals(jobBefore.getDuedate(), jobAfter.getDuedate());

  }

  protected void assertTimerJobsRemoved(String... activityIds) {
    for (String activityId : activityIds) {
      assertTimerJobRemoved(activityId);
    }
  }

  protected void assertTimerJobRemoved(String activityId) {
    JobDefinition jobDefinitionBefore = testHelper.snapshotBeforeMigration.getJobDefinitionForActivityId(activityId);
    assertNotNull("Expected that a job definition for activity '" + activityId + "' exists before migration", jobDefinitionBefore);

    Job jobBefore = testHelper.snapshotBeforeMigration.getJobForDefinitionId(jobDefinitionBefore.getId());
    assertNotNull("Expected that a timer job for activity '" + activityId + "' exists before migration", jobBefore);
    assertTimerJob(jobBefore);

    for (Job job : testHelper.snapshotAfterMigration.getJobs()) {
      if (jobBefore.getId().equals(job.getId())) {
        fail("Expected job '" + jobBefore.getId() + "' to be removed after migration");
      }
    }
  }

  protected void assertTimerJobsCreated(String... activityIds) {
    for (String activityId : activityIds) {
      assertTimerJobCreated(activityId);
    }
  }

  protected void assertTimerJobCreated(String activityId) {
    JobDefinition jobDefinitionAfter = testHelper.snapshotAfterMigration.getJobDefinitionForActivityId(activityId);
    assertNotNull("Expected that a job definition for activity '" + activityId + "' exists after migration", jobDefinitionAfter);

    Job jobAfter = testHelper.snapshotAfterMigration.getJobForDefinitionId(jobDefinitionAfter.getId());
    assertNotNull("Expected that a timer job for activity '" + activityId + "' exists after migration", jobAfter);
    assertTimerJob(jobAfter);

    for (Job job : testHelper.snapshotBeforeMigration.getJobs()) {
      if (jobAfter.getId().equals(job.getId())) {
        fail("Expected job '" + jobAfter.getId() + "' to be created first after migration");
      }
    }
  }

  protected EventSubscription assertAndGetEventSubscription(String eventName) {
    EventSubscription eventSubscription = runtimeService.createEventSubscriptionQuery().eventName(eventName).singleResult();
    assertNotNull("Expected event subscription for event name: " + eventName, eventSubscription);
    return eventSubscription;
  }

  protected Job assertTimerJobExists(ProcessInstanceSnapshot snapshot) {
    List<Job> jobs = snapshot.getJobs();
    assertEquals(1, jobs.size());
    Job job = jobs.get(0);
    assertTimerJob(job);
    return job;
  }

  protected void assertTimerJob(Job job) {
    assertEquals("Expected job to be a timer job", TimerEntity.TYPE, ((JobEntity) job).getType());
  }

  protected void triggerTimerAndCompleteTasks(String... taskKeys) {
    Job job = assertTimerJobExists(testHelper.snapshotAfterMigration);
    rule.getManagementService().executeJob(job.getId());
    completeTasks(taskKeys);
  }

}
