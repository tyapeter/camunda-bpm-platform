/*
 * Copyright 2016 camunda services GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package org.camunda.bpm.engine.test.bpmn.event.conditional;

import java.util.List;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.test.Deployment;
import static org.camunda.bpm.engine.test.bpmn.event.conditional.AbstractConditionalEventTestCase.CONDITIONAL_EVENT_PROCESS_KEY;
import org.junit.Test;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import static org.camunda.bpm.engine.test.api.runtime.migration.ModifiableBpmnModelInstance.modify;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

/**
 *
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class EventSubProcessStartConditionalEventTest extends AbstractConditionalEventTestCase {

  @Test
  @Deployment
  public void testTrueCondition() {
    //given process with event sub process conditional start event
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);

    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());
    assertEquals(1, conditionEventSubscriptionQuery.list().size());

    //when variable is set on task with condition
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1);

    //then execution is at user task after conditional start event
    task = taskQuery.singleResult();
    assertEquals(TASK_AFTER_CONDITION, task.getName());
    assertEquals(0, conditionEventSubscriptionQuery.list().size());
  }

  @Test
  @Deployment
  public void testFalseCondition() {
    //given process with event sub process conditional start event
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);

    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());

    //when variable is set on task with condition
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1);

    //then execution stays at user task
    task = taskQuery.singleResult();
    assertEquals(TASK_BEFORE_CONDITION, task.getName());
  }

  @Test
  @Deployment
  public void testVariableCondition() {
    //given process with event sub process conditional start event
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);

    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());

    //when variable is set on task with condition
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1);

    //then execution is at user task after conditional start event
    task = taskQuery.singleResult();
    assertEquals(TASK_AFTER_CONDITION, task.getName());
  }

  @Test
  @Deployment(resources ={ "org/camunda/bpm/engine/test/bpmn/event/conditional/EventSubProcessStartConditionalEventTest.testVariableCondition.bpmn20.xml"})
  public void testWrongVariableCondition() {
    //given process with event sub process conditional start event
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);

    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());
    assertEquals(1, conditionEventSubscriptionQuery.list().size());

    //when variable is set on task contawith condition
    taskService.setVariable(task.getId(), VARIABLE_NAME+1, 1);

    //then execution stays at user task
    task = taskQuery.singleResult();
    assertEquals(TASK_BEFORE_CONDITION, task.getName());
    assertEquals(1, conditionEventSubscriptionQuery.list().size());
  }

  @Test
  @Deployment
  public void testNonInterruptingVariableCondition() {
    //given process with event sub process conditional start event
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);

    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());
    assertEquals(1, conditionEventSubscriptionQuery.list().size());

    //when variable is set on task with condition
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1);

    //then execution is at user task after conditional start event
    final List<Task> tasks = taskQuery.list();
    assertEquals(2, tasks.size());
    assertEquals(1, conditionEventSubscriptionQuery.list().size());
  }


  @Test
  @Deployment
  public void testSubProcessVariableCondition() {
    //given process with event sub process conditional start event and user task in sub process
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);

    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());

    //when local variable is set on task with condition
    taskService.setVariableLocal(task.getId(), VARIABLE_NAME, 1);

    //then execution stays at user task
    task = taskQuery.singleResult();
    assertEquals(TASK_BEFORE_CONDITION, task.getName());
  }

  @Test
  @Deployment(resources ={ "org/camunda/bpm/engine/test/bpmn/event/conditional/EventSubProcessStartConditionalEventTest.testSubProcessVariableCondition.bpmn20.xml"})
  public void testSubProcessSetVariableOnTaskCondition() {
    //given process with event sub process conditional start event and user task in sub process
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);

    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());

    //when variable is set on task, variable is propagated to process instance
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1);

    //then execution is at user task after conditional start event
    task = taskQuery.singleResult();
    assertEquals(TASK_AFTER_CONDITION, task.getName());
  }

  @Test
  @Deployment(resources ={ "org/camunda/bpm/engine/test/bpmn/event/conditional/EventSubProcessStartConditionalEventTest.testSubProcessVariableCondition.bpmn20.xml"})
  public void testSubProcessSetVariableOnExecutionCondition() {
    //given process with event sub process conditional start event and user task in sub process
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);

    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());

    //when local variable is set on task execution
    runtimeService.setVariableLocal(task.getExecutionId(), VARIABLE_NAME, 1);

    //then execution stays at user task
    task = taskQuery.singleResult();
    assertEquals(TASK_BEFORE_CONDITION, task.getName());
  }

  @Test
  @Deployment(resources ={ "org/camunda/bpm/engine/test/bpmn/event/conditional/EventSubProcessStartConditionalEventTest.testSubProcessVariableCondition.bpmn20.xml"})
  public void testSubProcessSetVariableOnProcessInstanceCondition() {
    //given process with event sub process conditional start event and user task in sub process
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);

    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());

    //when variable is set on process instance
    runtimeService.setVariable(procInst.getId(), VARIABLE_NAME, 1);

    //then execution is at user task after conditional start event
    task = taskQuery.singleResult();
    assertEquals(TASK_AFTER_CONDITION, task.getName());
  }

  @Test
  public void testVariableConditionWithVariableName() {

    //given process with event sub process conditional start event and defined variable name
    deployEventSubProcessWithVarName(true);

    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);
    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);

    //when variable with name `variable1` is set on execution
    taskService.setVariable(task.getId(), VARIABLE_NAME+1, 1);

    //then nothing happens
    task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());
    assertEquals(1, conditionEventSubscriptionQuery.list().size());

    //when variable with name `variable` is set on execution
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1);

    //then execution is at user task after conditional start event
    task = taskQuery.singleResult();
    assertEquals(TASK_AFTER_CONDITION, task.getName());
    assertEquals(0, conditionEventSubscriptionQuery.list().size());
  }


  @Test
  public void testVariableConditionWithVariableNameAndEvent() {

    //given process with event sub process conditional start event and defined variable name
    deployEventSubProcessWithVarEvents(true, CONDITIONAL_VAR_EVENT_UPDATE);

    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);
    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);

    //when variable with name `variable` is set on execution
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1);

    //then nothing happens
    task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());
    assertEquals(1, conditionEventSubscriptionQuery.list().size());

    //when variable with name `variable` is updated
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1);

    //then execution is at user task after conditional start event
    task = taskQuery.singleResult();
    assertEquals(TASK_AFTER_CONDITION, task.getName());
    assertEquals(0, conditionEventSubscriptionQuery.list().size());
  }

  @Test
  public void testNonInterruptingVariableConditionWithVariableName() {

    //given process with event sub process non interrupting conditional start event and defined variable name
    deployEventSubProcessWithVarName(false);

    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);
    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);

    //when variable with name `variable1` is set on execution
    taskService.setVariable(task.getId(), VARIABLE_NAME+1, 1);

    //then nothing happens
    task = taskQuery.singleResult();
    assertNotNull(task);
    assertEquals(TASK_BEFORE_CONDITION, task.getName());
    assertEquals(1, conditionEventSubscriptionQuery.list().size());

    //when variable with name `variable` is set, updated and deleted
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1); //create
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1); //update
    taskService.removeVariable(task.getId(), VARIABLE_NAME); //delete

    //then execution is for three times at user task after conditional start event
    List<Task> tasks = taskQuery.taskName(TASK_AFTER_CONDITION).list();
    assertEquals(3, tasks.size());
    assertEquals(1, conditionEventSubscriptionQuery.list().size());
  }

  @Test
  public void testNonInterruptingVariableConditionWithVariableNameAndEvents() {

    //given process with event sub process conditional start event and defined variable name
    deployEventSubProcessWithVarEvents(false, CONDITIONAL_VAR_EVENTS);

    ProcessInstance procInst = runtimeService.startProcessInstanceByKey(CONDITIONAL_EVENT_PROCESS_KEY);
    TaskQuery taskQuery = taskService.createTaskQuery().processInstanceId(procInst.getId());
    Task task = taskQuery.singleResult();
    assertNotNull(task);

    //when variable with name `variable` is set, updated and deleted
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1); //create
    taskService.setVariable(task.getId(), VARIABLE_NAME, 1); //update
    taskService.removeVariable(task.getId(), VARIABLE_NAME); //delete

    //then execution is for two times at user task after conditional start event
    List<Task> tasks = taskQuery.taskName(TASK_AFTER_CONDITION).list();
    assertEquals(2, tasks.size());
    assertEquals(1, conditionEventSubscriptionQuery.list().size());
  }

  protected void deployEventSubProcessWithVarName(boolean interrupting) {
    final BpmnModelInstance modelInstance = modify(TASK_MODEL)
            .addSubProcessTo(CONDITIONAL_EVENT_PROCESS_KEY)
              .triggerByEvent()
              .embeddedSubProcess()
              .startEvent()
                .interrupting(interrupting)
                .conditionalEventDefinition(CONDITIONAL_EVENT)
                  .condition(TRUE_CONDITION)
                  .camundaVariableName(VARIABLE_NAME)
                .conditionalEventDefinitionDone()
                .userTask()
                .name(TASK_AFTER_CONDITION)
              .endEvent()
            .done();

    engine.manageDeployment(repositoryService.createDeployment().addModelInstance(CONDITIONAL_MODEL, modelInstance).deploy());
  }

  protected void deployEventSubProcessWithVarEvents(boolean interrupting, String varEvent) {
    final BpmnModelInstance modelInstance = modify(TASK_MODEL)
            .addSubProcessTo(CONDITIONAL_EVENT_PROCESS_KEY)
              .triggerByEvent()
              .embeddedSubProcess()
              .startEvent()
                .interrupting(interrupting)
                .conditionalEventDefinition(CONDITIONAL_EVENT)
                  .condition(CONDITION_EXPR)
                  .camundaVariableName(VARIABLE_NAME)
                  .camundaVariableEvents(varEvent)
                .conditionalEventDefinitionDone()
                .userTask()
                .name(TASK_AFTER_CONDITION)
              .endEvent()
            .done();

    engine.manageDeployment(repositoryService.createDeployment().addModelInstance(CONDITIONAL_MODEL, modelInstance).deploy());
  }
}
