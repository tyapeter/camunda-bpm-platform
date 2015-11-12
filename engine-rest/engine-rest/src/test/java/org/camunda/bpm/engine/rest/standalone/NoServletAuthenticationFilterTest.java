package org.camunda.bpm.engine.rest.standalone;

import org.camunda.bpm.engine.rest.util.TestContainerRule;
import org.junit.ClassRule;

public class NoServletAuthenticationFilterTest extends AbstractAuthenticationFilterTest {

  @ClassRule
  public static TestContainerRule testContainer = new TestContainerRule();

}
