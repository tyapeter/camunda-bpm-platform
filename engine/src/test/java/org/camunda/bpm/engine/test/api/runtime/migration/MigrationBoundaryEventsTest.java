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

import java.util.Collections;
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
import org.camunda.bpm.engine.runtime.ProcessInstance;
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
  public void testMigrateMessageBoundaryEventOnUserTask() {
    // given
    BpmnModelInstance testProcess = modify(ProcessModels.ONE_TASK_PROCESS)
      .addMessageBoundaryEventWithUserTask("userTask", "boundary", MESSAGE_NAME, AFTER_BOUNDARY_TASK);
    ProcessDefinition sourceProcessDefinition = testHelper.deploy(testProcess);
    ProcessDefinition targetProcessDefinition = testHelper.deploy(modify(testProcess)
      .changeElementId("boundary", "newBoundary")
    );

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
