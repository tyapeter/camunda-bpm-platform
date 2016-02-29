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
package org.camunda.bpm.engine.impl.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.camunda.bpm.engine.impl.tree.TreeWalker.WalkCondition;

/**
 * @author Thorben Lindhauer
 *
 */
public abstract class BranchingWalker<T> {

  protected List<T> currentElements;

  protected List<TreeVisitor<T>> preVisitor = new ArrayList<TreeVisitor<T>>();

  protected List<TreeVisitor<T>> postVisitor = new ArrayList<TreeVisitor<T>>();

  protected abstract Collection<T> nextElements();

  public BranchingWalker(T initialElement) {
    currentElements = new LinkedList<T>();
    currentElements.add(initialElement);
  }

  public BranchingWalker<T> addPreVisitor(TreeVisitor<T> collector) {
    this.preVisitor.add(collector);
    return this;
  }

  public BranchingWalker<T> addPostVisitor(TreeVisitor<T> collector) {
    this.postVisitor.add(collector);
    return this;
  }

  public T walkWhile() {
    return walkWhile(new TreeWalker.NullCondition<T>());
  }

  public T walkWhile(WalkCondition<T> condition) {
    while (!condition.isFulfilled(getCurrentElement())) {
      for (TreeVisitor<T> collector : preVisitor) {
        collector.visit(getCurrentElement());
      }

      currentElements.addAll(nextElements());
      currentElements.remove(0);

      for (TreeVisitor<T> collector : postVisitor) {
        collector.visit(getCurrentElement());
      }
    }
    return getCurrentElement();
  }

  // TODO: not sure if this is of any use
  public T walkUntil(WalkCondition<T> condition) {
    do {
      for (TreeVisitor<T> collector : preVisitor) {
        collector.visit(getCurrentElement());
      }

      currentElements.addAll(nextElements());
      currentElements.remove(0);

      for (TreeVisitor<T> collector : postVisitor) {
        collector.visit(getCurrentElement());
      }
    } while (!condition.isFulfilled(getCurrentElement()));
    return getCurrentElement();
  }


  public T getCurrentElement() {
    return currentElements.isEmpty() ? null : currentElements.get(0);
  }


}
