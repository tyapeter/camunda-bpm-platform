/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.impl.migration.validation;

import java.util.Arrays;
import java.util.List;

import org.camunda.bpm.engine.impl.bpmn.behavior.BoundaryEventActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.FlowNodeActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.MultiInstanceActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.SubProcessActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.tree.FlowScopeWalker;
import org.camunda.bpm.engine.impl.tree.TreeWalker;

public class MigrationActivityValidators {

  // Validators

  public static final MigrationActivityValidator SUPPORTED_ACTIVITY = new AbstractMigrationActivityValidator() {

    @SuppressWarnings("unchecked")
    public final List<Class<? extends FlowNodeActivityBehavior>> SUPPORTED_ACTIVITY_BEHAVIORS = Arrays.asList(
      SubProcessActivityBehavior.class,
      UserTaskActivityBehavior.class,
      BoundaryEventActivityBehavior.class
    );

    public boolean canBeMigrated(ActivityImpl activity, ProcessDefinitionImpl processDefinition) {
      Class<? extends ActivityBehavior> activityBehaviorClass = activity.getActivityBehavior().getClass();
      for (Class<? extends ActivityBehavior> supportedActivityBehaviorClass : SUPPORTED_ACTIVITY_BEHAVIORS) {
        if (activityBehaviorClass.isAssignableFrom(supportedActivityBehaviorClass)) {
          return true;
        }
      }
      return false;
    }
  };

  public static final MigrationActivityValidator NOT_MULTI_INSTANCE_CHILD = new AbstractMigrationActivityValidator() {
    public boolean canBeMigrated(ActivityImpl activity, ProcessDefinitionImpl processDefinition) {
      return !hasMultiInstanceParent(activity);
    }
  };

  public static final MigrationActivityValidator SUPPORTED_BOUNDARY_EVENT = new AbstractMigrationActivityValidator() {
    public final List<String> supportedTypes = Arrays.asList(
      "boundaryMessage",
      "boundarySignal",
      "boundaryTimer"
    );

    public boolean canBeMigrated(ActivityImpl activity, ProcessDefinitionImpl processDefinition) {
      if (activity.getActivityBehavior().getClass().isAssignableFrom(BoundaryEventActivityBehavior.class)) {
        String boundaryType = (String) activity.getProperty("type");
        return supportedTypes.contains(boundaryType);
      }
      else {
        return true;
      }
    }
  };

  // Helper

  protected static boolean hasMultiInstanceParent(ActivityImpl activity) {
    FlowScopeWalker flowScopeWalker = new FlowScopeWalker(activity);
    flowScopeWalker.walkUntil(new TreeWalker.WalkCondition<ScopeImpl>() {
      public boolean isFulfilled(ScopeImpl element) {
        return isProcessDefinition(element) || isMultiInstance(element);
      }
    });

    return isMultiInstance(flowScopeWalker.getCurrentElement());
  }

  protected static boolean isMultiInstance(ScopeImpl scope) {
    return !isProcessDefinition(scope) && scope.getActivityBehavior() instanceof MultiInstanceActivityBehavior;
  }

  protected static boolean isProcessDefinition(ScopeImpl scope) {
    return scope == scope.getProcessDefinition();
  }

  protected static boolean isScope(ActivityImpl activity) {
    return activity.isScope();
  }

}
