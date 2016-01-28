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
package org.camunda.bpm.engine.test.api.multitenancy;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

/**
 * @author Daniel Meyer
 *
 */
public class MultiTenancyTaskServiceTest extends PluggableProcessEngineTestCase{

  private static final String tenant1 = "the-tenant-1";
  private static final String tenant2 = "the-tenant-2";

  public void testStandaloneTaskCreateWithTenantId() {

    // given a transient task with tenant id
    Task task = taskService.newTask();
    task.setTenantId(tenant1);

    // if
    // it is saved
    taskService.saveTask(task);

    // then
    // when I load it, the tenant id is preserved
    task = taskService.createTaskQuery().taskId(task.getId()).singleResult();
    assertEquals(tenant1, task.getTenantId());

    // Finally, delete task
    taskService.deleteTask(task.getId(), true);
  }

  public void testStandaloneTaskCannotChangeTenantIdIfNull() {

    // given a persistent task without tenant id
    Task task = taskService.newTask();
    taskService.saveTask(task);
    task = taskService.createTaskQuery().singleResult();

    // if
    // change the tenant id
    task.setTenantId(tenant1);

    // then
    // an exception is thrown on 'save'
    try {
      taskService.saveTask(task);
      fail("Expected an exception");
    }
    catch(ProcessEngineException e) {
      assertTextPresent("ENGINE-03072 Cannot cange tenantId of Task", e.getMessage());
    }

    // Finally, delete task
    taskService.deleteTask(task.getId(), true);
  }

  public void testStandaloneTaskCannotChangeTenantId() {

    // given a persistent task with tenant id
    Task task = taskService.newTask();
    task.setTenantId(tenant1);
    taskService.saveTask(task);
    task = taskService.createTaskQuery().singleResult();

    // if
    // change the tenant id
    task.setTenantId(tenant2);

    // then
    // an exception is thrown on 'save'
    try {
      taskService.saveTask(task);
      fail("Expected an exception");
    }
    catch(ProcessEngineException e) {
      assertTextPresent("ENGINE-03072 Cannot cange tenantId of Task", e.getMessage());
    }

    // Finally, delete task
    taskService.deleteTask(task.getId(), true);
  }

  public void testStandaloneTaskPropagateTenantIdToSubTask() {

    // given a persistent task with a tenant id
    Task task = taskService.newTask();
    task.setTenantId(tenant1);
    taskService.saveTask(task);

    // if
    // I create a subtask without tenant id
    Task subTask = taskService.newTask();
    subTask.setParentTaskId(task.getId());
    taskService.saveTask(subTask);

    // then
    // the parent task's tenant id is propagated to the sub task
    subTask = taskService.createTaskQuery().taskId(subTask.getId()).singleResult();
    assertEquals(tenant1, subTask.getTenantId());

    // Finally, delete task
    taskService.deleteTask(subTask.getId(), true);
    taskService.deleteTask(task.getId(), true);
  }

  public void testStandaloneTaskCannotSetDifferentTenantIdOnSubTask() {

    // given a persistent task with a tenant id
    Task task = taskService.newTask();
    task.setTenantId(tenant1);
    taskService.saveTask(task);

    // if
    // I create a subtask with a different tenant id
    Task subTask = taskService.newTask();
    subTask.setParentTaskId(task.getId());
    subTask.setTenantId(tenant2);

    // then an exception is thrown on save
    try {
      taskService.saveTask(subTask);
      fail("Exception expected.");
    }
    catch(ProcessEngineException e) {
      assertTextPresent("ENGINE-03073 Cannot set different tenantId on subtask than on parent Task", e.getMessage());
    }
    // Finally, delete task
    taskService.deleteTask(task.getId(), true);
  }

  public void testStandaloneTaskCannotSetDifferentTenantIdOnSubTaskWithNull() {

    // given a persistent task without tenant id
    Task task = taskService.newTask();
    taskService.saveTask(task);

    // if
    // I create a subtask with a different tenant id
    Task subTask = taskService.newTask();
    subTask.setParentTaskId(task.getId());
    subTask.setTenantId(tenant1);

    // then an exception is thrown on save
    try {
      taskService.saveTask(subTask);
      fail("Exception expected.");
    }
    catch(ProcessEngineException e) {
      assertTextPresent("ENGINE-03073 Cannot set different tenantId on subtask than on parent Task", e.getMessage());
    }
    // Finally, delete task
    taskService.deleteTask(task.getId(), true);
  }

  public void testExecutionPropagatesTenantId() {
    final BpmnModelInstance theProcess = Bpmn.createExecutableProcess("theProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();

    // given
    // a deployment for tenant 1
    Deployment deployment = repositoryService.createDeployment()
      .addModelInstance("process.bpmn", theProcess)
      .tenantId(tenant1)
      .deploy();

    // if
    // we start the process
    runtimeService.startProcessInstanceByKey("theProcess");

    // then
    // a task with the same tenant id is created
    Task task = taskService.createTaskQuery().singleResult();
    assertEquals(tenant1, task.getTenantId());

    // cleanup
    repositoryService.deleteDeployment(deployment.getId(), true);
  }
}
