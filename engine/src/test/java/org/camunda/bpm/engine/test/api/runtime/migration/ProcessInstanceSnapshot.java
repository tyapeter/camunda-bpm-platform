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

package org.camunda.bpm.engine.test.api.runtime.migration;

import java.util.List;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.impl.util.EnsureUtil;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.EventSubscription;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.test.util.ExecutionTree;

/**
 * Helper class to save the current state of a process instance.
 */
public class ProcessInstanceSnapshot {

  protected String processInstanceId;
  protected String processDefinitionId;
  protected ActivityInstance activityTree;
  protected ExecutionTree executionTree;
  protected List<EventSubscription> eventSubscriptions;
  protected List<Job> jobs;

  public ProcessInstanceSnapshot(String processInstanceId, String processDefinitionId) {
    this.processInstanceId = processInstanceId;
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public ActivityInstance getActivityTree() {
    ensurePropertySaved("activity tree", activityTree);
    return activityTree;
  }

  public void setActivityTree(ActivityInstance activityTree) {
    this.activityTree = activityTree;
  }

  public ExecutionTree getExecutionTree() {
    ensurePropertySaved("execution tree", executionTree);
    return executionTree;
  }

  public void setExecutionTree(ExecutionTree executionTree) {
    this.executionTree = executionTree;
  }

  public List<EventSubscription> getEventSubscriptions() {
    ensurePropertySaved("event subscriptions", eventSubscriptions);
    return eventSubscriptions;
  }

  public void setEventSubscriptions(List<EventSubscription> eventSubscriptions) {
    this.eventSubscriptions = eventSubscriptions;
  }

  public List<Job> getJobs() {
    ensurePropertySaved("jobs", jobs);
    return jobs;
  }

  public void setJobs(List<Job> jobs) {
    this.jobs = jobs;
  }

  protected void ensurePropertySaved(String name, Object property) {
    EnsureUtil.ensureNotNull(BadUserRequestException.class, "The snapshot has not saved the " + name + " of the process instance", name, property);
  }

}
