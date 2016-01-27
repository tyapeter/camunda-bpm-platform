///* Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.camunda.bpm.engine.test.api.runtime.migration.cool;
//
//import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
//import org.camunda.bpm.engine.runtime.ProcessInstance;
//import org.camunda.bpm.model.bpmn.Bpmn;
//import org.camunda.bpm.model.bpmn.BpmnModelInstance;
//
///**
// * @author Thorben Lindhauer
// *
// */
//public class ScopeMigrationPseudoTest extends PluggableProcessEngineTestCase {
//
//  public static BpmnModelInstance ONE_TASK_PROCESS = Bpmn.createExecutableProcess()
//      .startEvent()
//      .userTask("foo")
//      .endEvent()
//      .done();
//
//  public static BpmnModelInstance SUBPROCESS_PROCESS = Bpmn.createExecutableProcess()
//      .startEvent()
//      .subProcess("subProcess1")
//        .embeddedSubProcess()
//          .startEvent()
//          .userTask("foo")
//          .endEvent()
//      .subProcessDone()
//      .endEvent()
//      .done();
//
//  public void testMigration() {
//    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
//    ExecutionTree originalTree = new ExecutionTree(processInstance);
//
//    runtimeService.setVariable(processInstance.getId(), "var", "value", "ProcessDefinition");
//
//    migrate(ONE_TASK_PROCESS, SUBPROCESS_PROCESS, "foo => foo");
//    ExecutionTree migratedTree = new ExecutionTree(processInstance);
//
//    assertThat(originalTree.getExecution("ProcessDefinition"))
//      .isEqualTo(migratedTree.getExecution("ProcessDefinition"));
//
//    assertThat(migratedTree).hasExecution("subProcess1");
//
//    assertThat(runtimeService.getVariable(processInstance.getId(), "var")).isInScope("ProcessDefinition");
//    assertThat(processInstance).hasStartExecutionListenerInvocationFor("subProcess1");
//    assertThat(processInstance).hasStartHistoryFor("subProcess1");
//
//  }
//
//  public void testMigration2() {
//    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("subProcessProcess");
//    ExecutionTree originalTree = new ExecutionTree(processInstance);
//
//    runtimeService.setVariable(processInstance.getId(), "var1", "value", "ProcessDefinition");
//    runtimeService.setVariable(processInstance.getId(), "var2", "value", "subProcess");
//
//    migrate(SUBPROCESS_PROCESS, ONE_TASK_PROCESS, "foo => foo");
//    ExecutionTree migratedTree = new ExecutionTree(processInstance);
//
//    assertThat(originalTree.getExecution("ProcessDefinition"))
//      .isEqualTo(migratedTree.getExecution("ProcessDefinition"));
//
//    assertThat(migratedTree).hasNoExecution("subProcess1");
//
//    assertThat(runtimeService.getVariable(processInstance.getId(), "var1")).isInScope("ProcessDefinition");
//    assertThat(runtimeService.getVariable(processInstance.getId(), "var2")).isGone();
//
//    assertThat(processInstance).hasEndExecutionListenerInvocationFor("subProcess1");
//    assertThat(processInstance).hasEndHistoryFor("subProcess1");
//  }
//
//  // sollten wir execution listener für neue scope executions aufrufen?
//  // pro:
//  //   * konsistent mit modification
//  //   * history kommt für diese Scopes gratis
//  // contra:
//  //   * Migration sollte eine reine Zustandsänderung der Prozessinstanz sein und nicht
//  //     Ausführungsaspekte wie das Feuern von ExecutionListenern anstoßen
//  //   * Listener könnte Code ausführen, der sich auf Dinge verlässt, die vorher nicht geschehen sind (z.B.
//  //     Ausführung von Aktivitäten, die es in der Quelldefinition nicht gibt)
//
//  // wie gehen wir mit History um?
//  // * sollte erzeugt werden für im Zuge der Migration erzeugte/zerstörte Scopes
//
//  // IO-Mappings:
//  // * input-Mappings für neu erzeugte Scopes sind sinnvoll (togglebar wie bei Modification)
//  // * output-Mappings ???
//}
//
