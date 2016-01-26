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
package org.camunda.bpm.engine.impl.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.camunda.bpm.engine.impl.ActivityExecutionTreeMapping;
import org.camunda.bpm.engine.impl.cmd.GetActivityInstanceCmd;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.pvm.PvmActivity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.pvm.runtime.PvmExecutionImpl;
import org.camunda.bpm.engine.impl.tree.ActivityStackCollector;
import org.camunda.bpm.engine.impl.tree.FlowScopeWalker;
import org.camunda.bpm.engine.impl.tree.TreeWalker.WalkCondition;
import org.camunda.bpm.engine.runtime.ActivityInstance;

/**
 * @author Thorben Lindhauer
 *
 */
public class MigrateProcessInstanceCmd implements Command<Void> {

  protected MigrationPlan migrationPlan;
  protected String processInstanceId;


  public MigrateProcessInstanceCmd(MigrationPlan migrationPlan, String processInstanceId) {
    this.migrationPlan = migrationPlan;
    this.processInstanceId = processInstanceId;
  }

  @Override
  public Void execute(CommandContext commandContext) {
    ProcessDefinitionEntity targetProcessDefinition = Context.getProcessEngineConfiguration()
        .getDeploymentCache().findDeployedProcessDefinitionById(migrationPlan.getTargetProcessDefinitionId());

    ExecutionEntity processInstance = commandContext.getExecutionManager().findExecutionById(processInstanceId);

    ActivityInstance activityInstanceTree = new GetActivityInstanceCmd(processInstanceId).execute(commandContext);
    List<DetachedActivityInstance> detachedInstances = new ArrayList<DetachedActivityInstance>();

    // 1. detach activity instances and involved entities
    for (MigrationInstruction instruction : migrationPlan.getInstructions()) {
      ActivityInstance[] instancesForSourceActivity =
          activityInstanceTree.getActivityInstances(instruction.getSourceActivityId());


      for (ActivityInstance instance : instancesForSourceActivity) {
        DetachedActivityInstance detachedInstance = new DetachedActivityInstance();
        detachedInstance.activityInstanceId = instance.getId();
        detachedInstance.instruction = instruction;

        String taskExecutionId = instance.getExecutionIds()[0];
        List<TaskEntity> tasksByExecutionId = Context.getCommandContext().getTaskManager().findTasksByExecutionId(taskExecutionId);
        detachedInstance.userTask = tasksByExecutionId.get(0);
        detachedInstance.userTask.getExecution().removeTask(detachedInstance.userTask);
        detachedInstance.userTask.setExecution(null);
        detachedInstances.add(detachedInstance);
      }
    }

    // 2. delete all executions but process instance
    for (PvmExecutionImpl child : new ArrayList<PvmExecutionImpl>(processInstance.getExecutions())) {
      // TODO: we should not use deleteCascade because this call end execution listeners etc.
      child.deleteCascade(null);
    }
    processInstance.setActivity(null);


    // 3. update process definition IDs
    processInstance.setProcessDefinition(targetProcessDefinition);

    for (DetachedActivityInstance detachedInstance : detachedInstances) {
      detachedInstance.userTask.setProcessDefinitionId(targetProcessDefinition.getId());
    }

    // 4. build execution tree
    for (int i = 0; i < detachedInstances.size(); i++) {
      DetachedActivityInstance detachedInstance = detachedInstances.get(i);
      String targetActivityId = detachedInstance.instruction.getTargetActivityId();
      ActivityImpl targetActivity = targetProcessDefinition.findActivity(targetActivityId);

      ActivityExecutionTreeMapping mapping = null;
      // first instantiation is special: cannot derive from existing execution tree which scopes need to be instantiated
      if (i == 0) {
        mapping = new ActivityExecutionTreeMapping();
      }
      else {
        mapping = new ActivityExecutionTreeMapping(Context.getCommandContext(), processInstance.getId());
      }
      List<PvmActivity> activitiesToInstantiate = collectFlowScopes(targetActivity, mapping);

      Collections.reverse(activitiesToInstantiate);

      ScopeImpl existingScope = null;
      if (activitiesToInstantiate.isEmpty()) {
        existingScope = targetActivity.getFlowScope();
      }
      else {
        existingScope = activitiesToInstantiate.get(0).getFlowScope();
      }

      ExecutionEntity ancestorExecution = null;
      if (i == 0) {
        ancestorExecution = processInstance;
      }
      else {
        Set<ExecutionEntity> executions = mapping.getExecutions(existingScope);
        ancestorExecution = executions.iterator().next();
      }

      for (PvmActivity activityToInstantiate : activitiesToInstantiate) {
        ancestorExecution = ancestorExecution.createExecution();
        ancestorExecution.setActive(false);
        ancestorExecution.setActivityId(activityToInstantiate.getId());
        ancestorExecution.initialize();
        ancestorExecution.initializeTimerDeclarations();
        ancestorExecution.setActivityId(null);
      }

      ancestorExecution.setActivityId(targetActivityId);
      ancestorExecution.setActive(true);

      // attach entities (e.g. tasks)
      detachedInstance.userTask.setExecution(ancestorExecution);
      ancestorExecution.addTask(detachedInstance.userTask);
    }

    return null;
  }
//
//  protected List<PvmActivity> collectFlowScopes(final ActivityImpl sourceActivity, final ScopeImpl targetScope) {
//    ActivityStackCollector stackCollector = new ActivityStackCollector();
//    FlowScopeWalker walker = new FlowScopeWalker(sourceActivity.isScope() ? sourceActivity : sourceActivity.getFlowScope());
//    walker.addPreVisitor(stackCollector);
//
//    // walk until a scope is reached for which executions exist
//    walker.walkWhile(new WalkCondition<ScopeImpl>() {
//      public boolean isFulfilled(ScopeImpl element) {
//        return element == targetScope;
//      }
//    });
//
//    return stackCollector.getActivityStack();
//  }

  protected List<PvmActivity> collectFlowScopes(final ActivityImpl sourceActivity, final ActivityExecutionTreeMapping mapping) {
    ActivityStackCollector stackCollector = new ActivityStackCollector();
    FlowScopeWalker walker = new FlowScopeWalker(sourceActivity.isScope() ? sourceActivity : sourceActivity.getFlowScope());
    walker.addPreVisitor(stackCollector);

    // walk until a scope is reached for which executions exist
    walker.walkWhile(new WalkCondition<ScopeImpl>() {
      public boolean isFulfilled(ScopeImpl element) {
        return !mapping.getExecutions(element).isEmpty() || element == sourceActivity.getProcessDefinition();
      }
    });

    return stackCollector.getActivityStack();
  }

  public static class DetachedActivityInstance {
    protected String activityInstanceId;
    protected TaskEntity userTask;
    protected MigrationInstruction instruction;
  }


}
