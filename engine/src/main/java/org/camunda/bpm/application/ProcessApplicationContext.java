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
package org.camunda.bpm.application;

/**
 * @author Thorben Lindhauer
 *
 */
public class ProcessApplicationContext {

  protected static ThreadLocal<ProcessApplicationIdentifier> currentProcessApplication =
      new ThreadLocal<ProcessApplicationContext.ProcessApplicationIdentifier>();

  public static void setCurrentProcessApplication(String name) {
    ProcessApplicationIdentifier identifier = new ProcessApplicationIdentifier();
    identifier.name = name;
    currentProcessApplication.set(identifier);
  }

  public static void setCurrentProcessApplication(ProcessApplicationReference reference) {
    ProcessApplicationIdentifier identifier = new ProcessApplicationIdentifier();
    identifier.reference = reference;
    currentProcessApplication.set(identifier);
  }

  public static void setCurrentProcessApplication(ProcessApplicationInterface processApplication) {
    ProcessApplicationIdentifier identifier = new ProcessApplicationIdentifier();
    identifier.processApplication = processApplication;
    currentProcessApplication.set(identifier);
  }

  public static void clear() {
    currentProcessApplication.remove();
  }

  // TODO: this should not be public API
  // => thread-local could be moved to impl class
  public static ProcessApplicationIdentifier get() {
    return currentProcessApplication.get();
  }

  // TODO: methods that take a callable and a process application identifier

  // TODO: this should not be public API either
  public static class ProcessApplicationIdentifier {

    protected String name;
    protected ProcessApplicationReference reference;
    protected ProcessApplicationInterface processApplication;

    public String getName() {
      return name;
    }

    public ProcessApplicationReference getReference() {
      return reference;
    }

    public ProcessApplicationInterface getProcessApplication() {
      return processApplication;
    }
  }
}
