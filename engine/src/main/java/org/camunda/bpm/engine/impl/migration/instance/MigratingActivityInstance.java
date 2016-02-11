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
package org.camunda.bpm.engine.impl.migration.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.camunda.bpm.engine.impl.bpmn.parser.EventSubscriptionDeclaration;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.persistence.entity.EventSubscriptionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.migration.MigrationInstruction;
import org.camunda.bpm.engine.runtime.ActivityInstance;

/**
 * @author Thorben Lindhauer
 *
 */
public abstract class MigratingActivityInstance implements MigratingInstance {
  protected MigrationInstruction migrationInstruction;
  protected ActivityInstance activityInstance;
  // scope execution for actual scopes,
  // concurrent execution in case of non-scope activity with expanded tree
  protected ExecutionEntity representativeExecution;
  protected List<MigratingInstance> dependentInstances;
  protected ScopeImpl sourceScope;
  protected ScopeImpl targetScope;

  protected Set<MigratingActivityInstance> childInstances;
  protected MigratingActivityInstance parentInstance;

  public abstract void detachState();

  public abstract void attachState(ExecutionEntity newScopeExecution);

  protected void removeEventSubscriptions(ExecutionEntity currentExecution) {
    for (EventSubscriptionEntity eventSubscription : currentExecution.getEventSubscriptions()) {
      if (isEventSubscriptionForSourceScope(eventSubscription)) {
        eventSubscription.delete();
      }
    }
  }

  protected boolean isEventSubscriptionForSourceScope(EventSubscriptionEntity eventSubscription) {
    ActivityImpl eventSubscriptionActivity = sourceScope.getProcessDefinition().findActivity(eventSubscription.getActivityId());
    if (eventSubscriptionActivity != null) {
      ScopeImpl eventScope = eventSubscriptionActivity.getEventScope();
      return sourceScope != null && sourceScope.getId().equals(eventScope.getId());
    }
    else {
      return false;
    }
  }

  protected void removeTimerJobs(ExecutionEntity currentExecution) {
    Context.getCommandContext()
      .getJobManager()
      .cancelTimers(currentExecution);
  }

  public abstract void remove();

  public void migrateDependentEntities() {

    if (dependentInstances != null) {
      for (MigratingInstance dependentInstance : dependentInstances) {
        dependentInstance.migrateState();
        dependentInstance.migrateDependentEntities();
      }
    }
  }

  public abstract ExecutionEntity resolveRepresentativeExecution();

  public void addDependentInstance(MigratingInstance migratingInstance) {
    if (dependentInstances == null) {
      dependentInstances = new ArrayList<MigratingInstance>();
    }

    dependentInstances.add(migratingInstance);
  }

  protected void createMissingEventSubscriptions(ExecutionEntity currentScopeExecution) {
    List<EventSubscriptionDeclaration> eventSubscriptionDeclarations = EventSubscriptionDeclaration.getDeclarationsForScope(targetScope);
    for (EventSubscriptionDeclaration eventSubscriptionDeclaration : eventSubscriptionDeclarations) {
      eventSubscriptionDeclaration.createSubscription(currentScopeExecution);
    }
  }

  protected void createMissingTimerJobs(ExecutionEntity currentScopeExecution) {
    currentScopeExecution.initializeTimerDeclarations();
  }

  public ActivityInstance getActivityInstance() {
    return activityInstance;
  }

  public ScopeImpl getSourceScope() {
    return sourceScope;
  }

  public ScopeImpl getTargetScope() {
    return targetScope;
  }

  public Set<MigratingActivityInstance> getChildren() {
    return childInstances;
  }

  public MigratingActivityInstance getParent() {
    return parentInstance;
  }

  public void setParent(MigratingActivityInstance parentInstance) {
    this.parentInstance = parentInstance;
  }

  public MigrationInstruction getMigrationInstruction() {
    return migrationInstruction;
  }

}
