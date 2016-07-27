package org.camunda.bpm.engine.rest.history;

import org.camunda.bpm.engine.rest.dto.history.HistoricTaskInstanceReportResultDto;
import org.camunda.bpm.engine.rest.dto.history.ReportResultDto;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.List;

/**
 * @author Stefan Hentschel.
 */
@Produces(MediaType.APPLICATION_JSON)
public interface HistoricTaskInstanceReportService {

  String PATH = "/report";

  /**
   * creates a historic task instance report
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  List<HistoricTaskInstanceReportResultDto> getTaskReportResults(@Context UriInfo uriInfo);

  /**
   * creates a historic task instance duration report.
   */
  @GET
  @Path("/duration")
  @Produces(MediaType.APPLICATION_JSON)
  List<ReportResultDto> getTaskDurationReportResults(@Context UriInfo uriInfo);
}
