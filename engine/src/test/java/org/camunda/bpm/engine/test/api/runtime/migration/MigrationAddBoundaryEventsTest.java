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
import static org.camunda.bpm.engine.test.util.ActivityInstanceAssert.assertThat;
import static org.camunda.bpm.engine.test.util.ActivityInstanceAssert.describeActivityInstanceTree;
import static org.camunda.bpm.engine.test.util.ExecutionAssert.assertThat;
import static org.camunda.bpm.engine.test.util.ExecutionAssert.describeExecutionTree;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.persistence.entity.MessageEventSubscriptionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.SignalEventSubscriptionEntity;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.EventSubscription;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.model.bpmn.builder.EndEventBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MigrationAddBoundaryEventsTest {

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

  protected ProcessEngine processEngine;
  protected RuntimeService runtimeService;
  protected TaskService taskService;
  protected ManagementService managementService;

  @Before
  public void initServices() {
    processEngine = rule.getProcessEngine();
    runtimeService = rule.getRuntimeService();
    taskService = rule.getTaskService();
    managementService = rule.getManagementService();
  }

  @Test
  public void testAddMessageBoundaryEventToUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.ONE_TASK_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope()
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertMessageEventSubscriptionExists(MESSAGE_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddMessageBoundaryEventToUserTaskAndCorrelateMessage() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.ONE_TASK_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the message and successfully complete the migrated instance
    correlateMessageAndCompleteTasks(MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddMessageBoundaryEventToScopeUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertMessageEventSubscriptionExists(MESSAGE_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddMessageBoundaryEventToScopeUserTaskAndCorrelateMessage() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the message and successfully complete the migrated instance
    correlateMessageAndCompleteTasks(MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddMessageBoundaryEventToConcurrentUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask1", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope()
          .up().up()
          .child("userTask2").concurrent().noScope()
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2").getId())
        .done());

    assertMessageEventSubscriptionExists(MESSAGE_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddMessageBoundaryEventToConcurrentUserTaskAndCorrelateMessage() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask1", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
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
  public void testAddMessageBoundaryEventToConcurrentScopeUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SCOPE_TASKS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addMessageBoundaryEventWithUserTask("userTask1", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2").getId())
        .done());

    assertMessageEventSubscriptionExists(MESSAGE_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddMessageBoundaryEventToConcurrentScopeUserTaskAndCorrelateMessage() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SCOPE_TASKS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addMessageBoundaryEventWithUserTask("userTask1", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
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
  public void testAddMessageBoundaryEventToSubProcess() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertMessageEventSubscriptionExists(MESSAGE_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddMessageBoundaryEventToSubProcessAndCorrelateMessage() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the message and successfully complete the migrated instance
    correlateMessageAndCompleteTasks(MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddMessageBoundaryEventToSubProcessWithScopeUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess"))
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertMessageEventSubscriptionExists(MESSAGE_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddMessageBoundaryEventToSubProcessWithScopeUserTaskAndCorrelateMessage() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the message and successfully complete the migrated instance
    correlateMessageAndCompleteTasks(MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddMessageBoundaryEventToParallelSubProcess() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess1", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
      .mapActivities("subProcess2", "subProcess2")
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess2"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess1").getId())
        .activity("userTask1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1").getId())
        .endScope()
        .beginScope("subProcess2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess2").getId())
        .activity("userTask2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2").getId())
        .done());

    assertMessageEventSubscriptionExists(MESSAGE_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddMessageBoundaryEventToParallelSubProcessAndCorrelateMessage() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addMessageBoundaryEventWithUserTask("subProcess1", MESSAGE_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
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
  public void testAddSignalBoundaryEventToUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.ONE_TASK_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope()
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertSignalEventSubscriptionExists(SIGNAL_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToUserTaskAndSendSignal() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.ONE_TASK_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to send the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToScopeUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope()
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertSignalEventSubscriptionExists(SIGNAL_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToScopeUserTaskAndSendSignal() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to send the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToConcurrentUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask1", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope()
          .up().up()
          .child("userTask2").concurrent().noScope()
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2").getId())
        .done());

    assertSignalEventSubscriptionExists(SIGNAL_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToConcurrentUserTaskAndSendSignal() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addSignalBoundaryEventWithUserTask("userTask1", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to send the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToConcurrentScopeUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SCOPE_TASKS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addSignalBoundaryEventWithUserTask("userTask1", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2").getId())
        .done());

    assertSignalEventSubscriptionExists(SIGNAL_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToConcurrentScopeUserTaskAndSendSignal() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SCOPE_TASKS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addSignalBoundaryEventWithUserTask("userTask1", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to send the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToSubProcess() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertSignalEventSubscriptionExists(SIGNAL_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToSubProcessAndCorrelateSignal() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToSubProcessWithScopeUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess"))
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertSignalEventSubscriptionExists(SIGNAL_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToSubProcessWithScopeUserTaskAndCorrelateSignal() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the signal and successfully complete the migrated instance
    sendSignalAndCompleteTasks(SIGNAL_NAME, AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToParallelSubProcess() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess1", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
      .mapActivities("subProcess2", "subProcess2")
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess2"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess1").getId())
        .activity("userTask1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1").getId())
        .endScope()
        .beginScope("subProcess2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess2").getId())
        .activity("userTask2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2").getId())
        .done());

    assertSignalEventSubscriptionExists(SIGNAL_NAME);

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddSignalBoundaryEventToParallelSubProcessAndCorrelateSignal() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addSignalBoundaryEventWithUserTask("subProcess1", SIGNAL_NAME, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
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
  public void testAddTimerBoundaryEventToUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.ONE_TASK_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope()
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertTimerJobExists();

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToUserTaskAndSendTimerWithDate() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.ONE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.ONE_TASK_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to send the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToScopeUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope()
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertTimerJobExists();

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToScopeUserTaskAndSendTimerWithDate() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to send the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToConcurrentUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask1", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope()
          .up().up()
          .child("userTask2").concurrent().noScope()
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2").getId())
        .done());

    assertTimerJobExists();

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToConcurrentUserTaskAndSendTimerWithDate() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_GATEWAY_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_GATEWAY_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("userTask1", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to send the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToConcurrentScopeUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SCOPE_TASKS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addTimerDateBoundaryEventWithUserTask("userTask1", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .activity("userTask1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1").getId())
        .activity("userTask2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2").getId())
        .done());

    assertTimerJobExists();

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToConcurrentScopeUserTaskAndSendTimerWithDate() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SCOPE_TASKS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SCOPE_TASKS)
      .addTimerDateBoundaryEventWithUserTask("userTask1", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to send the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK, "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToSubProcess() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertTimerJobExists();

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToSubProcessAndCorrelateTimerWithDate() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToSubProcessWithScopeUserTask() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess"))
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertTimerJobExists();

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToSubProcessWithScopeUserTaskAndCorrelateTimerWithDate() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SCOPE_TASK_SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then it is possible to correlate the timer and successfully complete the migrated instance
    triggerTimerAndCompleteTasks(AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToParallelSubProcess() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess1", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
      .mapActivities("subProcess2", "subProcess2")
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).concurrent().noScope()
          .child("userTask1").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess1"))
          .up().up()
          .child(null).concurrent().noScope()
          .child("userTask2").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess2"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess1").getId())
        .activity("userTask1", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask1").getId())
        .endScope()
        .beginScope("subProcess2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess2").getId())
        .activity("userTask2", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask2").getId())
        .done());

    assertTimerJobExists();

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask1", "userTask2");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddTimerBoundaryEventToParallelSubProcessAndCorrelateTimerWithDate() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.PARALLEL_SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.PARALLEL_SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEventWithUserTask("subProcess1", TIMER_DATE, AFTER_BOUNDARY_TASK)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess1", "subProcess1")
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
  public void testAddMultipleBoundaryEvents() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SUBPROCESS_PROCESS)
      .addTimerDateBoundaryEvent("subProcess", TIMER_DATE)
      .addMessageBoundaryEvent("userTask", MESSAGE_NAME)
      .addSignalBoundaryEvent("userTask", SIGNAL_NAME)
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child(null).scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess"))
          .child("userTask").scope()
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    assertMessageEventSubscriptionExists(MESSAGE_NAME);
    assertSignalEventSubscriptionExists(SIGNAL_NAME);
    assertTimerJobExists();

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddErrorBoundaryEventToSubProcessAndThrowError() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SUBPROCESS_PROCESS)
      .addErrorBoundaryEventWithUserTask("subProcess", ERROR_CODE, AFTER_BOUNDARY_TASK) // catch error with boundary event
      .getBuilderForElementById("subProcessEnd", EndEventBuilder.class)
      .error(ERROR_CODE) // let the end event of the subprocess throw an error
      .done()
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    completeTasks(AFTER_BOUNDARY_TASK);
    testHelper.assertProcessEnded(testHelper.snapshotBeforeMigration.getProcessInstanceId());
  }

  @Test
  public void testAddEscalationBoundaryEventToSubProcessAndThrowEscalation() {
    // given
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(ProcessModels.SUBPROCESS_PROCESS);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(ProcessModels.SUBPROCESS_PROCESS)
      .addEscalationBoundaryEventWithUserTask("subProcess", ESCALATION_CODE, AFTER_BOUNDARY_TASK) // catch escalation with boundary event
      .getBuilderForElementById("subProcessEnd", EndEventBuilder.class)
      .escalation(ESCALATION_CODE) // let the end event of the subprocess escalate
      .done()
    );

    MigrationPlan migrationPlan = runtimeService
      .createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("subProcess", "subProcess")
      .mapActivities("userTask", "userTask")
      .build();

    // when
    testHelper.createProcessInstanceAndMigrate(migrationPlan);

    // then
    assertThat(testHelper.snapshotAfterMigration.getExecutionTree())
      .hasProcessDefinitionId(targetProcessDefinition.getId())
      .matches(
        describeExecutionTree(null).scope().id(testHelper.snapshotBeforeMigration.getProcessInstanceId())
          .child("userTask").scope().id(testHelper.getSingleExecutionIdForActivity(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess"))
          .done());

    assertThat(testHelper.snapshotAfterMigration.getActivityTree()).hasStructure(
      describeActivityInstanceTree(targetProcessDefinition.getId())
        .beginScope("subProcess", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "subProcess").getId())
        .activity("userTask", testHelper.getSingleActivityInstance(testHelper.snapshotBeforeMigration.getActivityTree(), "userTask").getId())
        .done());

    // and it is possible to successfully complete the migrated instance
    completeTasks("userTask");
    completeTasks(AFTER_BOUNDARY_TASK);
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

  protected void assertMessageEventSubscriptionExists(String messageName) {
    EventSubscription eventSubscription = assertAndGetEventSubscription(messageName);
    assertEquals(MessageEventSubscriptionEntity.EVENT_TYPE, eventSubscription.getEventType());
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

  protected void assertTimerJobExists() {
    Job job = managementService.createJobQuery().processInstanceId(testHelper.snapshotBeforeMigration.getProcessInstanceId()).timers().singleResult();
    assertNotNull("Expected a timer job to exist", job);
  }

  protected void triggerTimerAndCompleteTasks(String... taskKeys) {
    Job job = managementService.createJobQuery().processInstanceId(testHelper.snapshotBeforeMigration.getProcessInstanceId()).timers().singleResult();
    assertNotNull("Expected a timer job to exist", job);
    managementService.executeJob(job.getId());
    completeTasks(taskKeys);
  }

}
