package org.camunda.bpm.engine.rest.standalone;

import org.camunda.bpm.engine.rest.util.TestContainerRule;
import org.junit.ClassRule;

public class ServletAuthenticationFilterTest extends AbstractAuthenticationFilterTest {

  @ClassRule
  public static TestContainerRule testContainer = new TestContainerRule();

}
