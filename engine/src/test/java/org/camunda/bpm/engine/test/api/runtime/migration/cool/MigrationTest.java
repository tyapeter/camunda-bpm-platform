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

import static org.camunda.bpm.engine.test.util.ActivityInstanceAssert.assertThat;
import static org.camunda.bpm.engine.test.util.ActivityInstanceAssert.describeActivityInstanceTree;

import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.engine.impl.migration.MigrateProcessInstanceCmd;
import org.camunda.bpm.engine.impl.migration.MigrationInstructionImpl;
import org.camunda.bpm.engine.impl.migration.MigrationPlanImpl;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.TimeDuration;
import org.camunda.bpm.model.bpmn.instance.TimerEventDefinition;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/**
 * @author Thorben Lindhauer
 *
 */
public class MigrationTest extends PluggableProcessEngineTestCase {

  private static final String TEST_PROCESS_USER_TASK_V1 = "org/camunda/bpm/engine/test/api/runtime/migration/SetProcessDefinitionVersionCmdTest.testSetProcessDefinitionVersionWithTask.bpmn20.xml";
  private static final String TEST_PROCESS_USER_TASK_V2 = "org/camunda/bpm/engine/test/api/runtime/migration/SetProcessDefinitionVersionCmdTest.testSetProcessDefinitionVersionWithTaskV2.bpmn20.xml";

  protected static BpmnModelInstance BOUNDARY_PROCESS_V1 = Bpmn.createExecutableProcess("BoundaryProcess")
      .startEvent()
      .userTask("userTask")
      .endEvent()
      .done();

  static {
    timerBoundaryEventOn(BOUNDARY_PROCESS_V1, "BoundaryProcess", "userTask", "PT5M");
  }

  protected static BpmnModelInstance SUBPROCESS_PROCESS = Bpmn.createExecutableProcess("SubProcess")
      .startEvent()
      .subProcess("subProcess")
       .embeddedSubProcess()
          .startEvent()
          .userTask("userTask")
          .endEvent()
      .subProcessDone()
      .endEvent()
      .done();

  protected static BpmnModelInstance BOUNDARY_SUBPROCESS_PROCESS = Bpmn.createExecutableProcess("BoundarySubProcess")
      .startEvent()
      .subProcess("subProcess")
       .embeddedSubProcess()
          .startEvent()
          .userTask("userTask")
          .endEvent()
      .subProcessDone()
      .endEvent()
      .done();

  static {
    timerBoundaryEventOn(BOUNDARY_SUBPROCESS_PROCESS, "subProcess", "userTask", "PT5M");
  }

  protected static final BpmnModelInstance PARALLEL_GW_PROCESS = Bpmn.createExecutableProcess("ParallelGatewayProcess")
      .startEvent()
      .parallelGateway()
      .userTask("userTask1")
      .endEvent()
      .moveToLastGateway()
      .userTask("userTask2")
      .endEvent()
      .done();

  protected static void timerBoundaryEventOn(BpmnModelInstance modelInstance, String parentScope, String activityId, String duration) {
    ModelElementInstance parentScopeInstance = modelInstance.getModelElementById(parentScope);

    BoundaryEvent boundaryEvent = modelInstance.newInstance(BoundaryEvent.class);
    boundaryEvent.setAttachedTo((Activity) modelInstance.getModelElementById(activityId));
    parentScopeInstance.addChildElement(boundaryEvent);

    TimeDuration timerDuration = modelInstance.newInstance(TimeDuration.class);
    timerDuration.setTextContent(duration);

    TimerEventDefinition timerEventDefinition = modelInstance.newInstance(TimerEventDefinition.class);
    timerEventDefinition.setTimeDuration(timerDuration);
    boundaryEvent.addChildElement(timerEventDefinition);
  }

  @Deployment(resources = {TEST_PROCESS_USER_TASK_V1})
  public void testOneTaskProcessMigration() {
    String deploymentId = repositoryService.createDeployment().addClasspathResource(TEST_PROCESS_USER_TASK_V2).deploy().getId();

    ProcessDefinition sourceProcessDefinition = findProcessDefinition("userTask", 1);
    ProcessDefinition targetProcessDefinition = findProcessDefinition("userTask", 2);

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());

    // when
    MigrationPlan migration = runtimeService.createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("waitState1", "waitState1")
      .build();

    List<String> processInstanceIds = Arrays.asList(processInstance.getId());
    runtimeService.executeMigrationPlan(migration, processInstanceIds);

    // then
    ProcessInstance updatedInstance = runtimeService.createProcessInstanceQuery().singleResult();
    assertEquals(targetProcessDefinition.getId(), updatedInstance.getProcessDefinitionId());

    Task updatedTask = taskService.createTaskQuery().singleResult();
    assertEquals(targetProcessDefinition.getId(), updatedTask.getProcessDefinitionId());

    taskService.complete(updatedTask.getId());

    deleteDeployments(deploymentId);
  }

  @Deployment(resources = {TEST_PROCESS_USER_TASK_V1})
  public void testLeafActivityInstanceIdIsPreserved() {
    String deploymentId = repositoryService.createDeployment().addClasspathResource(TEST_PROCESS_USER_TASK_V2).deploy().getId();

    ProcessDefinition sourceProcessDefinition = findProcessDefinition("userTask", 1);
    ProcessDefinition targetProcessDefinition = findProcessDefinition("userTask", 2);

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());
    ActivityInstance sourceActivityInstanceTree = runtimeService.getActivityInstance(processInstance.getId());
    String userTaskActivityInstanceId = sourceActivityInstanceTree.getActivityInstances("waitState1")[0].getId();

    // when
    MigrationPlan migration = runtimeService.createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
        .mapActivities("waitState1", "waitState1")
        .build();

      List<String> processInstanceIds = Arrays.asList(processInstance.getId());
      runtimeService.executeMigrationPlan(migration, processInstanceIds);

    // then
    ActivityInstance targetActivityInstanceTree = runtimeService.getActivityInstance(processInstance.getId());
    assertThat(targetActivityInstanceTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .activity("waitState1")
        .done());

    assertEquals(userTaskActivityInstanceId, targetActivityInstanceTree.getActivityInstances("waitState1")[0].getId());

    deleteDeployments(deploymentId);

  }

  public void testBoundaryUserTaskMigration() {
    String deployment1Id = repositoryService.createDeployment().addModelInstance("foo.bpmn", BOUNDARY_PROCESS_V1).deploy().getId();
    String deployment2Id = repositoryService.createDeployment().addDeploymentResources(deployment1Id).deploy().getId();

    ProcessDefinition sourceProcessDefinition = findProcessDefinition("BoundaryProcess", 1);
    ProcessDefinition targetProcessDefinition = findProcessDefinition("BoundaryProcess", 2);

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());

    // when

    MigrationPlan migration = runtimeService.createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
        .mapActivities("userTask", "userTask")
        .build();

    List<String> processInstanceIds = Arrays.asList(processInstance.getId());
    runtimeService.executeMigrationPlan(migration, processInstanceIds);

    // then
    ProcessInstance updatedInstance = runtimeService.createProcessInstanceQuery().singleResult();
    assertEquals(targetProcessDefinition.getId(), updatedInstance.getProcessDefinitionId());

    Task updatedTask = taskService.createTaskQuery().singleResult();
    assertEquals(targetProcessDefinition.getId(), updatedTask.getProcessDefinitionId());

    Job timerJob = managementService.createJobQuery().singleResult();
    assertNotNull(timerJob);
    assertEquals(targetProcessDefinition.getId(), timerJob.getProcessDefinitionId());

    taskService.complete(updatedTask.getId());

    deleteDeployments(deployment1Id, deployment2Id);
  }

  public void testBoundaryActivityInstanceTree() {
    String deployment1Id = repositoryService.createDeployment().addModelInstance("foo.bpmn", BOUNDARY_PROCESS_V1).deploy().getId();
    String deployment2Id = repositoryService.createDeployment().addDeploymentResources(deployment1Id).deploy().getId();

    ProcessDefinition sourceProcessDefinition = findProcessDefinition("BoundaryProcess", 1);
    ProcessDefinition targetProcessDefinition = findProcessDefinition("BoundaryProcess", 2);

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());
    ActivityInstance sourceActivityInstanceTree = runtimeService.getActivityInstance(processInstance.getId());
    String userTaskActivityInstanceId = sourceActivityInstanceTree.getActivityInstances("userTask")[0].getId();

    // when
    MigrationPlan migration = runtimeService.createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
        .mapActivities("userTask", "userTask")
        .build();

    List<String> processInstanceIds = Arrays.asList(processInstance.getId());
    runtimeService.executeMigrationPlan(migration, processInstanceIds);

    // then
    ActivityInstance targetActivityInstanceTree = runtimeService.getActivityInstance(processInstance.getId());
    assertThat(targetActivityInstanceTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .activity("userTask")
        .done());

    assertEquals(userTaskActivityInstanceId, targetActivityInstanceTree.getActivityInstances("userTask")[0].getId());

    deleteDeployments(deployment1Id, deployment2Id);
  }

  public void testSubProcessUserTaskMigration() {
    String deployment1Id = repositoryService.createDeployment().addModelInstance("foo.bpmn", SUBPROCESS_PROCESS).deploy().getId();
    String deployment2Id = repositoryService.createDeployment().addDeploymentResources(deployment1Id).deploy().getId();

    ProcessDefinition sourceProcessDefinition = findProcessDefinition("SubProcess", 1);
    ProcessDefinition targetProcessDefinition = findProcessDefinition("SubProcess", 2);

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());

    // when
    MigrationPlan migration = runtimeService.createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
        .mapActivities("userTask", "userTask")
        .build();

    List<String> processInstanceIds = Arrays.asList(processInstance.getId());
    runtimeService.executeMigrationPlan(migration, processInstanceIds);

    // then
    ProcessInstance updatedInstance = runtimeService.createProcessInstanceQuery().singleResult();
    assertEquals(targetProcessDefinition.getId(), updatedInstance.getProcessDefinitionId());

    Task updatedTask = taskService.createTaskQuery().singleResult();
    assertEquals(targetProcessDefinition.getId(), updatedTask.getProcessDefinitionId());

    taskService.complete(updatedTask.getId());

    deleteDeployments(deployment1Id, deployment2Id);
  }

  @Deployment(resources = {TEST_PROCESS_USER_TASK_V1})
  public void testSubProcessActivityInstanceTree() {
    String deployment1Id = repositoryService.createDeployment().addModelInstance("foo.bpmn", SUBPROCESS_PROCESS).deploy().getId();
    String deployment2Id = repositoryService.createDeployment().addDeploymentResources(deployment1Id).deploy().getId();

    ProcessDefinition sourceProcessDefinition = findProcessDefinition("SubProcess", 1);
    ProcessDefinition targetProcessDefinition = findProcessDefinition("SubProcess", 2);

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());
    ActivityInstance sourceActivityInstanceTree = runtimeService.getActivityInstance(processInstance.getId());
    String userTaskActivityInstanceId = sourceActivityInstanceTree.getActivityInstances("userTask")[0].getId();

    // when
    MigrationPlan migration = runtimeService.createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
        .mapActivities("userTask", "userTask")
        .build();

    List<String> processInstanceIds = Arrays.asList(processInstance.getId());
    runtimeService.executeMigrationPlan(migration, processInstanceIds);

    // then
    ActivityInstance targetActivityInstanceTree = runtimeService.getActivityInstance(processInstance.getId());
    assertThat(targetActivityInstanceTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .beginScope("subProcess")
            .activity("userTask")
        .done());

    assertEquals(userTaskActivityInstanceId, targetActivityInstanceTree.getActivityInstances("userTask")[0].getId());

    deleteDeployments(deployment1Id, deployment2Id);

  }

  public void testBoundarySubProcessActivityInstanceTree() {
    String deployment1Id = repositoryService.createDeployment().addModelInstance("foo.bpmn", BOUNDARY_SUBPROCESS_PROCESS).deploy().getId();
    String deployment2Id = repositoryService.createDeployment().addDeploymentResources(deployment1Id).deploy().getId();

    ProcessDefinition sourceProcessDefinition = findProcessDefinition("BoundarySubProcess", 1);
    ProcessDefinition targetProcessDefinition = findProcessDefinition("BoundarySubProcess", 2);

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());
    ActivityInstance sourceActivityInstanceTree = runtimeService.getActivityInstance(processInstance.getId());
    String userTaskActivityInstanceId = sourceActivityInstanceTree.getActivityInstances("userTask")[0].getId();

    // when
    MigrationPlan migration = runtimeService.createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
        .mapActivities("userTask", "userTask")
      .build();

    List<String> processInstanceIds = Arrays.asList(processInstance.getId());
    runtimeService.executeMigrationPlan(migration, processInstanceIds);

    // then
    ActivityInstance targetActivityInstanceTree = runtimeService.getActivityInstance(processInstance.getId());
    assertThat(targetActivityInstanceTree).hasStructure(
        describeActivityInstanceTree(targetProcessDefinition.getId())
          .beginScope("subProcess")
            .activity("userTask")
        .done());

    assertEquals(userTaskActivityInstanceId, targetActivityInstanceTree.getActivityInstances("userTask")[0].getId());

    deleteDeployments(deployment1Id, deployment2Id);
  }

  public void testAddScopeUserTaskMigration() {
    String deployment1Id = repositoryService.createDeployment().addClasspathResource(TEST_PROCESS_USER_TASK_V1).deploy().getId();
    String deployment2Id = repositoryService.createDeployment().addModelInstance("foo.bpmn", SUBPROCESS_PROCESS).deploy().getId();

    ProcessDefinition sourceProcessDefinition = findProcessDefinition("userTask", 1);
    ProcessDefinition targetProcessDefinition = findProcessDefinition("SubProcess", 1);

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());

    // when
    MigrationPlan migration = runtimeService.createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("waitState1", "userTask")
      .build();

    List<String> processInstanceIds = Arrays.asList(processInstance.getId());
    runtimeService.executeMigrationPlan(migration, processInstanceIds);

    // then
    ProcessInstance updatedInstance = runtimeService.createProcessInstanceQuery().singleResult();
    assertEquals(targetProcessDefinition.getId(), updatedInstance.getProcessDefinitionId());

    Task updatedTask = taskService.createTaskQuery().singleResult();
    assertEquals(targetProcessDefinition.getId(), updatedTask.getProcessDefinitionId());

    taskService.complete(updatedTask.getId());

    deleteDeployments(deployment1Id, deployment2Id);
  }

  public void testConcurrentUserTasksMigration() {
    String deployment1Id = repositoryService.createDeployment().addModelInstance("foo.bpmn", PARALLEL_GW_PROCESS).deploy().getId();
    String deployment2Id = repositoryService.createDeployment().addDeploymentResources(deployment1Id).deploy().getId();

    ProcessDefinition sourceProcessDefinition = findProcessDefinition("ParallelGatewayProcess", 1);
    ProcessDefinition targetProcessDefinition = findProcessDefinition("ParallelGatewayProcess", 2);

    ProcessInstance processInstance = runtimeService.startProcessInstanceById(sourceProcessDefinition.getId());

    // when
    MigrationPlan migration = runtimeService.createMigrationPlan(sourceProcessDefinition.getId(), targetProcessDefinition.getId())
      .mapActivities("userTask1", "userTask1")
      .mapActivities("userTask2", "userTask2")
      .build();

    List<String> processInstanceIds = Arrays.asList(processInstance.getId());
    runtimeService.executeMigrationPlan(migration, processInstanceIds);

    // then
    ProcessInstance updatedInstance = runtimeService.createProcessInstanceQuery().singleResult();
    assertEquals(targetProcessDefinition.getId(), updatedInstance.getProcessDefinitionId());

    List<Task> updatedTasks = taskService.createTaskQuery().list();
    assertEquals(2, updatedTasks.size());
    assertEquals(targetProcessDefinition.getId(), updatedTasks.get(0).getProcessDefinitionId());
    assertEquals(targetProcessDefinition.getId(), updatedTasks.get(1).getProcessDefinitionId());

    taskService.complete(updatedTasks.get(0).getId());
    taskService.complete(updatedTasks.get(1).getId());

    assertProcessEnded(processInstance.getId());

    deleteDeployments(deployment1Id, deployment2Id);
  }

  // TODO:
  // + assert activity instance tree and preservation of activity instance ids
  // + test concurrency
  // + test infrastructure

  protected void deleteDeployments(String... deploymentIds) {
    for (String deploymentId : deploymentIds) {
      repositoryService.deleteDeployment(deploymentId, true);
    }

  }

  protected ProcessDefinition findProcessDefinition(String key, int version) {
    return repositoryService.createProcessDefinitionQuery().processDefinitionKey(key)
        .processDefinitionVersion(version)
        .singleResult();
  }
}
