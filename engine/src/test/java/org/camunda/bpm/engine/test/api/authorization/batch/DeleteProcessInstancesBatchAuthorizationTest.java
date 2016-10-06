package org.camunda.bpm.engine.test.api.authorization.batch;

import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.batch.Batch;
import org.camunda.bpm.engine.batch.history.HistoricBatch;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.api.authorization.util.AuthorizationScenario;
import org.camunda.bpm.engine.test.api.authorization.util.AuthorizationTestRule;
import org.camunda.bpm.engine.test.api.runtime.migration.models.ProcessModels;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static org.camunda.bpm.engine.test.api.authorization.util.AuthorizationScenario.scenario;
import static org.camunda.bpm.engine.test.api.authorization.util.AuthorizationSpec.grant;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Askar Akhmerov
 */
@RunWith(Parameterized.class)
public class DeleteProcessInstancesBatchAuthorizationTest {
  protected static final String TEST_REASON = "test reason";
  protected static final long BATCH_OPERATIONS = 3L;
  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected AuthorizationTestRule authRule = new AuthorizationTestRule(engineRule);
  protected ProcessEngineTestRule testHelper = new ProcessEngineTestRule(engineRule);

  protected ProcessInstance processInstance;
  protected ProcessInstance processInstance2;
  protected Batch batch;
  protected RuntimeService runtimeService;
  protected ManagementService managementService;
  protected int invocationsPerBatchJob;

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(authRule).around(testHelper);

  @Parameterized.Parameter
  public AuthorizationScenario scenario;

  @Parameterized.Parameters(name = "Scenario {index}")
  public static Collection<AuthorizationScenario[]> scenarios() {
    return AuthorizationTestRule.asParameters(
        scenario()
            .withAuthorizations(
                grant(Resources.BATCH, "*", "userId", Permissions.CREATE),
                grant(Resources.PROCESS_INSTANCE, "processInstance1", "userId", Permissions.READ, Permissions.DELETE),
                grant(Resources.PROCESS_INSTANCE, "processInstance2", "userId", Permissions.READ)
            )
            .failsDueToRequired(
                grant(Resources.PROCESS_INSTANCE, "processInstance2", "userId", Permissions.DELETE),
                grant(Resources.PROCESS_DEFINITION, "Process", "userId", Permissions.DELETE_INSTANCE)
            ),
        scenario()
            .withAuthorizations(
                grant(Resources.BATCH, "*", "userId", Permissions.CREATE),
                grant(Resources.PROCESS_INSTANCE, "processInstance1", "userId", Permissions.ALL),
                grant(Resources.PROCESS_INSTANCE, "processInstance2", "userId", Permissions.ALL)
            ).succeeds(),
        scenario()
            .withAuthorizations(
                grant(Resources.BATCH, "*", "userId", Permissions.CREATE),
                grant(Resources.PROCESS_DEFINITION, "Process", "userId", Permissions.READ_INSTANCE, Permissions.DELETE_INSTANCE)
            ).succeeds()
    );
  }

  @Before
  public void setUp() {
    authRule.createUserAndGroup("userId", "groupId");
    runtimeService = engineRule.getRuntimeService();
    managementService = engineRule.getManagementService();
    invocationsPerBatchJob = engineRule.getProcessEngineConfiguration().getInvocationsPerBatchJob();
  }

  @Before
  public void deployProcesses() {
    ProcessDefinition sourceDefinition = testHelper.deployAndGetDefinition(ProcessModels.ONE_TASK_PROCESS);
    processInstance = engineRule.getRuntimeService().startProcessInstanceById(sourceDefinition.getId());
    processInstance2 = engineRule.getRuntimeService().startProcessInstanceById(sourceDefinition.getId());
  }

  @After
  public void tearDown() {
    authRule.deleteUsersAndGroups();
    engineRule.getProcessEngineConfiguration().setInvocationsPerBatchJob(invocationsPerBatchJob);
  }

  @After
  public void cleanBatch() {
    Batch batch = engineRule.getManagementService().createBatchQuery().singleResult();
    if (batch != null) {
      engineRule.getManagementService().deleteBatch(
          batch.getId(), true);
    }

    HistoricBatch historicBatch = engineRule.getHistoryService().createHistoricBatchQuery().singleResult();
    if (historicBatch != null) {
      engineRule.getHistoryService().deleteHistoricBatch(
          historicBatch.getId());
    }
  }

  @Test
  public void testWithTwoInvocationsProcessInstancesList() {
    engineRule.getProcessEngineConfiguration().setInvocationsPerBatchJob(2);
    setupAndExecuteProcessInstancesListTest();

    // then
    if (authRule.assertScenario(scenario)) {
      if (testHelper.isHistoryLevelFull()) {
        assertThat(engineRule.getHistoryService().createUserOperationLogQuery().count(), is(BATCH_OPERATIONS));
      }

      if (authRule.scenarioSucceeded()) {
        assertThat(runtimeService.createProcessInstanceQuery().count(), is(0L));
      } else {
        assertThat(runtimeService.createProcessInstanceQuery().count(), is(2L));
      }
    }
  }

  @Test
  public void testProcessInstancesList() {
    setupAndExecuteProcessInstancesListTest();
    // then
    if (authRule.assertScenario(scenario)) {
      if (testHelper.isHistoryLevelFull()) {
        assertThat(engineRule.getHistoryService().createUserOperationLogQuery().count(), is(BATCH_OPERATIONS));
      }

      if (authRule.scenarioSucceeded()) {
        assertThat(runtimeService.createProcessInstanceQuery().count(), is(0L));
      } else {
        assertThat(runtimeService.createProcessInstanceQuery().count(), is(1L));
      }
    }
  }

  protected void setupAndExecuteProcessInstancesListTest() {
    //given
    List<String> processInstanceIds = Arrays.asList(processInstance.getId(), processInstance2.getId());
    authRule
        .init(scenario)
        .withUser("userId")
        .bindResource("processInstance1", processInstance.getId())
        .bindResource("processInstance2", processInstance2.getId())
        .start();

    // when
    batch = runtimeService.deleteProcessInstancesAsync(
        processInstanceIds, null, TEST_REASON);

    executeSeedAndBatchJobs();
  }

  @Test
  public void testWithQuery() {
    //given
    ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery()
        .processInstanceIds(new HashSet<String>(Arrays.asList(processInstance.getId(), processInstance2.getId())));

    authRule
        .init(scenario)
        .withUser("userId")
        .bindResource("processInstance1", processInstance.getId())
        .bindResource("processInstance2", processInstance2.getId())
        .start();

    // when

    batch = runtimeService.deleteProcessInstancesAsync(null,
        processInstanceQuery, TEST_REASON);
    executeSeedAndBatchJobs();

    // then
    if (authRule.assertScenario(scenario)) {
      if (testHelper.isHistoryLevelFull()) {
        assertThat(engineRule.getHistoryService().createUserOperationLogQuery().count(), is(BATCH_OPERATIONS));
      }
    }
  }

  protected void executeSeedAndBatchJobs() {
    engineRule.getManagementService().executeJob(
        engineRule.getManagementService().createJobQuery().singleResult().getId());

    for (Job pending : engineRule.getManagementService().createJobQuery().list()) {
      engineRule.getManagementService().executeJob(pending.getId());
    }
  }

}
