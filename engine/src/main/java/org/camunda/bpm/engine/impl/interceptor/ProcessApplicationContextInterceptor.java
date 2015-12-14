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
package org.camunda.bpm.engine.impl.interceptor;

import org.camunda.bpm.application.ProcessApplicationReference;
import org.camunda.bpm.application.impl.ProcessApplicationContextImpl;
import org.camunda.bpm.application.impl.ProcessApplicationIdentifier;
import org.camunda.bpm.container.RuntimeContainerDelegate;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;

/**
 * @author Thorben Lindhauer
 *
 */
public class ProcessApplicationContextInterceptor extends CommandInterceptor {

  protected ProcessEngineConfigurationImpl processEngineConfiguration;

  public ProcessApplicationContextInterceptor(ProcessEngineConfigurationImpl processEngineConfiguration) {
    this.processEngineConfiguration = processEngineConfiguration;
  }

  @Override
  public <T> T execute(Command<T> command) {
    ProcessApplicationIdentifier processApplicationIdentifier = ProcessApplicationContextImpl.get();

    if (processApplicationIdentifier != null) {
      ProcessApplicationReference reference = getPaReference(processApplicationIdentifier);
      try {
        Context.setCurrentProcessApplication(reference);
        return next.execute(command);
      } finally {
        Context.removeCurrentProcessApplication();
      }
    }
    else {
      return next.execute(command);
    }
  }

  protected ProcessApplicationReference getPaReference(ProcessApplicationIdentifier processApplicationIdentifier) {
    if (processApplicationIdentifier.getReference() != null) {
      return processApplicationIdentifier.getReference();
    }
    else if (processApplicationIdentifier.getProcessApplication() != null) {
      return processApplicationIdentifier.getProcessApplication().getReference();
    }
    else if (processApplicationIdentifier.getName() != null) {
       RuntimeContainerDelegate runtimeContainerDelegate = RuntimeContainerDelegate.INSTANCE.get();
       return runtimeContainerDelegate.getDeployedProcessApplication(processApplicationIdentifier.getName());
    }
    else {
      throw new ProcessEngineException("Cannot resolve Process Application");
    }
  }

}
