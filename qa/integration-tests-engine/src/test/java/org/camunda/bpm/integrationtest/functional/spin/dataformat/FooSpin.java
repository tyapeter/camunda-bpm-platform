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
package org.camunda.bpm.integrationtest.functional.spin.dataformat;

import java.io.Writer;

import org.camunda.spin.Spin;

/**
 * @author Thorben Lindhauer
 *
 */
public class FooSpin extends Spin<FooSpin> {

  protected FooDataFormat dataFormat;

  public String getDataFormatName() {
    return dataFormat.getName();
  }

  /* (non-Javadoc)
   * @see org.camunda.spin.Spin#unwrap()
   */
  @Override
  public Object unwrap() {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.camunda.spin.Spin#toString()
   */
  @Override
  public String toString() {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.camunda.spin.Spin#writeToWriter(java.io.Writer)
   */
  @Override
  public void writeToWriter(Writer writer) {
    // TODO Auto-generated method stub

  }

  /* (non-Javadoc)
   * @see org.camunda.spin.Spin#mapTo(java.lang.Class)
   */
  @Override
  public <C> C mapTo(Class<C> type) {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see org.camunda.spin.Spin#mapTo(java.lang.String)
   */
  @Override
  public <C> C mapTo(String type) {
    // TODO Auto-generated method stub
    return null;
  }

}
