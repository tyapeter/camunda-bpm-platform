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

import java.util.List;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;
import org.camunda.bpm.engine.impl.persistence.entity.MessageEventSubscriptionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.SignalEventSubscriptionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TimerEntity;
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
    EventSubscription eventSubscriptionBefore = findEventSubscriptionFromSnapshotForActivityId(activityIdBefore, testHelper.snapshotBeforeMigration);
    assertNotNull("Expected that an event subscription for activity '" + activityIdBefore + "' exists before migration", eventSubscriptionBefore);
    EventSubscription eventSubscriptionAfter = findEventSubscriptionFromSnapshotForActivityId(activityIdAfter, testHelper.snapshotAfterMigration);
    assertNotNull("Expected that an event subscription for activity '" + activityIdAfter + "' exists after migration", eventSubscriptionAfter);

    assertEquals(eventSubscriptionBefore.getId(), eventSubscriptionAfter.getId());
    assertEquals(eventSubscriptionBefore.getEventType(), eventSubscriptionAfter.getEventType());
    assertEquals(eventSubscriptionBefore.getEventName(), eventSubscriptionAfter.getEventName());
  }

  protected EventSubscription findEventSubscriptionFromSnapshotForActivityId(String activityId, ProcessInstanceSnapshot snapshot) {
    for (EventSubscription eventSubscription : snapshot.getEventSubscriptions()) {
      if (activityId.equals(eventSubscription.getActivityId())) {
        return eventSubscription;
      }
    }
    return null;
  }

  protected void assertMessageEventSubscriptionExists(String messageName, String eventSubscriptionId, String activityId) {
    EventSubscription eventSubscription = assertAndGetEventSubscription(messageName);
    assertEquals(MessageEventSubscriptionEntity.EVENT_TYPE, eventSubscription.getEventType());
    assertEquals("Expected to have same event subscription id", eventSubscriptionId, eventSubscription.getId());
    assertEquals("Expected to have same activity id", activityId, eventSubscription.getActivityId());
  }

  protected void assertSignalEventSubscriptionExists(String signalName) {
    EventSubscription eventSubscription = assertAndGetEventSubscription(signalName);
    assertEquals(SignalEventSubscriptionEntity.EVENT_TYPE, eventSubscription.getEventType());
  }

  protected EventSubscription assertAndGetEventSubscription(String eventName) {
    EventSubscription eventSubscription = runtimeService.createEventSubscriptionQuery().eventName(eventName).singleResult();
    assertNotNull("Expected event subscription for event name: " + eventName, eventSubscription);
    return eventSubscription;
  }

  protected Job assertTimerJobExists() {
    List<Job> jobs = testHelper.snapshotAfterMigration.getJobs();
    assertEquals(1, jobs.size());
    Job job = jobs.get(0);
    assertEquals("Expected a timer job to exist", TimerEntity.TYPE, ((JobEntity) job).getType());
    return job;
  }

  protected void triggerTimerAndCompleteTasks(String... taskKeys) {
    Job job = assertTimerJobExists();
    rule.getManagementService().executeJob(job.getId());
    completeTasks(taskKeys);
  }

}
