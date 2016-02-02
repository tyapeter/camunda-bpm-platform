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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.ActivityExecutionTreeMapping;
import org.camunda.bpm.engine.impl.ProcessEngineLogger;
import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.cmd.GetActivityInstanceCmd;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.pvm.PvmActivity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.tree.ActivityStackCollector;
import org.camunda.bpm.engine.impl.tree.FlowScopeWalker;
import org.camunda.bpm.engine.impl.tree.ScopeCollector;
import org.camunda.bpm.engine.impl.tree.TreeWalker.WalkCondition;
import org.camunda.bpm.engine.migration.MigrationInstruction;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.runtime.ActivityInstance;

/**
 * @author Thorben Lindhauer
 *
 */
public class MigrateProcessInstanceCmd implements Command<Void> {

  protected MigrationPlan migrationPlan;
  protected List<String> processInstanceIds;

  protected static final MigrationLogger LOGGER = ProcessEngineLogger.MIGRATION_LOGGER;


  public MigrateProcessInstanceCmd(MigrationPlan migrationPlan, List<String> processInstanceIds) {
    this.migrationPlan = migrationPlan;
    this.processInstanceIds = processInstanceIds;
  }

  public Void execute(CommandContext commandContext) {
    for (String processInstanceId : processInstanceIds) {
      migrateProcessInstance(commandContext, processInstanceId);
    }
    return null;
  }

  public Void migrateProcessInstance(CommandContext commandContext, String processInstanceId) {

    ExecutionEntity processInstance = commandContext.getExecutionManager().findExecutionById(processInstanceId);

    ProcessDefinitionImpl sourceProcessDefinition = processInstance.getProcessDefinition();
    ProcessDefinitionEntity targetProcessDefinition = Context.getProcessEngineConfiguration()
        .getDeploymentCache().findDeployedProcessDefinitionById(migrationPlan.getTargetProcessDefinitionId());


    ActivityInstance activityInstanceTree = new GetActivityInstanceCmd(processInstanceId).execute(commandContext);
    Map<String, MigratingActivityInstance> migratingInstances = new HashMap<String, MigratingActivityInstance>();

    Set<ActivityInstance> unmappedInstances = collectInstances(activityInstanceTree);
    unmappedInstances.remove(activityInstanceTree);

    final ActivityExecutionTreeMapping mapping = new ActivityExecutionTreeMapping(commandContext, processInstanceId);

    // 1. collect activity instances that are migrated
    for (MigrationInstruction instruction : migrationPlan.getInstructions()) {
      ActivityInstance[] instancesForSourceActivity =
          activityInstanceTree.getActivityInstances(instruction.getSourceActivityIds().get(0));

      for (ActivityInstance instance : instancesForSourceActivity) {
        MigratingActivityInstance migratingInstance = new MigratingActivityInstance();
        migratingInstance.activityInstance = instance;
        migratingInstance.instruction = instruction;
        migratingInstance.sourceScope = sourceProcessDefinition.findActivity(instruction.getSourceActivityIds().get(0));
        migratingInstance.targetScope = targetProcessDefinition.findActivity(instruction.getTargetActivityIds().get(0));

        ActivityImpl sourceActivity = sourceProcessDefinition.findActivity(instance.getActivityId());
        if (sourceActivity.getActivityBehavior() instanceof UserTaskActivityBehavior) {
          String taskExecutionId = instance.getExecutionIds()[0];
          List<TaskEntity> tasksByExecutionId = Context.getCommandContext().getTaskManager().findTasksByExecutionId(taskExecutionId);
          migratingInstance.userTask = tasksByExecutionId.get(0);
        }

        migratingInstances.put(instance.getId(), migratingInstance);

        unmappedInstances.remove(instance);
      }
    }

    if (!unmappedInstances.isEmpty()) {
      throw LOGGER.unmappedActivityInstances(processInstanceId, unmappedInstances);
    }


    migrateActivityInstance(mapping, activityInstanceTree, migratingInstances);

    // 2. update process definition IDs
    processInstance.setProcessDefinition(targetProcessDefinition);

    for (MigratingActivityInstance migratingInstance : migratingInstances) {
      migratingInstance.userTask.setProcessDefinitionId(targetProcessDefinition.getId());
    }

    return null;
  }

  protected void migrateActivityInstance(ActivityExecutionTreeMapping mapping,
      ActivityInstance activityInstanceTree, Map<String, MigratingActivityInstance> migratingInstances) {
    if (!activityInstanceTree.getId().equals(activityInstanceTree.getProcessInstanceId())) {
      final MigratingActivityInstance migratingInstance = migratingInstances.get(activityInstanceTree.getId());
      final MigratingActivityInstance parentMigratingInstance = migratingInstances.get(activityInstanceTree.getParentActivityInstanceId());

      if (!(migratingInstance.targetScope.getFlowScope() == parentMigratingInstance.targetScope)) {
        ScopeCollector scopeCollector = new ScopeCollector();
        FlowScopeWalker flowScopeWalker = new FlowScopeWalker(migratingInstance.targetScope);
        flowScopeWalker.addPostVisitor(scopeCollector);
        flowScopeWalker.walkUntil(new WalkCondition<ScopeImpl>() {

          @Override
          public boolean isFulfilled(ScopeImpl element) {
            return element.getFlowScope() == parentMigratingInstance.targetScope
                || element.getFlowScope() == migratingInstance.targetScope.getProcessDefinition();
          }
        });

        List<ScopeImpl> scopesToCreate = scopeCollector.getScopes();
        Collections.reverse(scopesToCreate);

        Set<ExecutionEntity> parentSourceScopeExecutions = mapping.getExecutions(parentMigratingInstance.sourceScope);
        String[] executionIds = parentMigratingInstance.activityInstance.getExecutionIds();
        ExecutionEntity scopeExecution = intersect(parentSourceScopeExecutions, executionIds);

        // erzeuge neue Scope-Executions für die fehlenden Scopes
        ExecutionEntity lowestScopeExecution = scopeExecution.createScopesConcurrent(scopesToCreate);

        // hänge Scope-Execution für die Aktivitätsinstanz, die wir gerade migrieren, unter diese neue Instanz
        // oder alternativ bei nicht-scope-Aktivität: setzt lowestScopeExecution auf diese Aktivitätsinstanz
      }

      // aktualisiere Prozessdefinitions-ID auf den Executions
    }

    for (ActivityInstance childInstance : activityInstanceTree.getChildActivityInstances()) {
      migrateActivityInstance(mapping, childInstance, migratingInstances);
    }

  }

  protected ExecutionEntity intersect(Set<ExecutionEntity> executions, String[] executionIds) {
    Set<String> executionIdSet = new HashSet<String>();
    for (String executionId : executionIds) {
      executionIdSet.add(executionId);
    }

    for (ExecutionEntity execution : executions) {
      if (executionIdSet.contains(execution.getId())) {
        return execution;
      }
    }
    throw new ProcessEngineException("Could not determine execution");
  }

  protected Set<ActivityInstance> collectInstances(ActivityInstance activityInstanceTree) {
    Set<ActivityInstance> instances = Collections.singleton(activityInstanceTree);

    for (ActivityInstance childInstance : activityInstanceTree.getChildActivityInstances()) {
      instances.addAll(collectInstances(childInstance));
    }

    return instances;
  }

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

  public static class MigratingActivityInstance {
    protected ActivityInstance activityInstance;
    protected TaskEntity userTask;
    protected MigrationInstruction instruction;
    protected ScopeImpl sourceScope;
    protected ScopeImpl targetScope;
  }


}
