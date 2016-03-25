create table if not exists ACT_GE_PROPERTY (
  NAME_ varchar(64),
  VALUE_ lvarchar(300),
  REV_ integer,
  primary key (NAME_)
);

insert into ACT_GE_PROPERTY
values ('schema.version', 'fox', 1);

insert into ACT_GE_PROPERTY
values ('schema.history', 'create(fox)', 1);

insert into ACT_GE_PROPERTY
values ('next.dbid', '1', 1);

insert into ACT_GE_PROPERTY
values ('deployment.lock', '0', 1);

create table if not exists ACT_GE_BYTEARRAY (
  ID_ varchar(64),
  REV_ integer,
  NAME_ varchar(255),
  DEPLOYMENT_ID_ varchar(64),
  BYTES_ byte,
  GENERATED_ boolean,
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_RE_DEPLOYMENT (
  ID_ varchar(64),
  NAME_ varchar(255),
  DEPLOY_TIME_ datetime year to fraction(5),
  SOURCE_ varchar(255),
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_RU_EXECUTION (
  ID_ varchar(64),
  REV_ integer,
  PROC_INST_ID_ varchar(64),
  BUSINESS_KEY_ varchar(255),
  PARENT_ID_ varchar(64),
  PROC_DEF_ID_ varchar(64),
  SUPER_EXEC_ varchar(64),
  SUPER_CASE_EXEC_ varchar(64),
  CASE_INST_ID_ varchar(64),
  ACT_INST_ID_ varchar(64),
  ACT_ID_ varchar(255),
  IS_ACTIVE_ boolean,
  IS_CONCURRENT_ boolean,
  IS_SCOPE_ boolean,
  IS_EVENT_SCOPE_ boolean,
  SUSPENSION_STATE_ integer,
  CACHED_ENT_STATE_ integer,
  SEQUENCE_COUNTER_ bigint,
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_RU_JOB (
  ID_ varchar(64) not null,
  REV_ integer,
  TYPE_ varchar(255) not null,
  LOCK_EXP_TIME_ datetime year to fraction(5),
  LOCK_OWNER_ varchar(255),
  EXCLUSIVE_ boolean,
  EXECUTION_ID_ varchar(64),
  PROCESS_INSTANCE_ID_ varchar(64),
  PROCESS_DEF_ID_ varchar(64),
  PROCESS_DEF_KEY_ varchar(64),
  RETRIES_ integer,
  EXCEPTION_STACK_ID_ varchar(64),
  EXCEPTION_MSG_ lvarchar(4000),
  DUEDATE_ datetime year to fraction(5),
  REPEAT_ varchar(255),
  HANDLER_TYPE_ varchar(255),
  HANDLER_CFG_ lvarchar(4000),
  DEPLOYMENT_ID_ varchar(64),
  SUSPENSION_STATE_ integer not null default 1,
  JOB_DEF_ID_ varchar(64),
  PRIORITY_ bigint not null default 0,
  SEQUENCE_COUNTER_ bigint,
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_RU_JOBDEF (
  ID_ varchar(64) not null,
  REV_ integer,
  PROC_DEF_ID_ varchar(64),
  PROC_DEF_KEY_ varchar(255),
  ACT_ID_ varchar(255),
  JOB_TYPE_ varchar(255) not null,
  JOB_CONFIGURATION_ varchar(255),
  SUSPENSION_STATE_ integer,
  JOB_PRIORITY_ bigint,
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_RE_PROCDEF (
  ID_ varchar(64) not null,
  REV_ integer,
  CATEGORY_ varchar(255),
  NAME_ varchar(255),
  KEY_ varchar(255) not null,
  VERSION_ integer not null,
  DEPLOYMENT_ID_ varchar(64),
  RESOURCE_NAME_ lvarchar(4000),
  DGRM_RESOURCE_NAME_ lvarchar(4000),
  HAS_START_FORM_KEY_ boolean,
  SUSPENSION_STATE_ integer,
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_RU_TASK (
  ID_ varchar(64),
  REV_ integer,
  EXECUTION_ID_ varchar(64),
  PROC_INST_ID_ varchar(64),
  PROC_DEF_ID_ varchar(64),
  CASE_EXECUTION_ID_ varchar(64),
  CASE_INST_ID_ varchar(64),
  CASE_DEF_ID_ varchar(64),
  NAME_ varchar(255),
  PARENT_TASK_ID_ varchar(64),
  DESCRIPTION_ lvarchar(4000),
  TASK_DEF_KEY_ varchar(255),
  OWNER_ varchar(255),
  ASSIGNEE_ varchar(255),
  DELEGATION_ varchar(64),
  PRIORITY_ integer,
  CREATE_TIME_ datetime year to fraction(5),
  DUE_DATE_ datetime year to fraction(5),
  FOLLOW_UP_DATE_ datetime year to fraction(5),
  SUSPENSION_STATE_ integer,
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_RU_IDENTITYLINK (
  ID_ varchar(64),
  REV_ integer,
  GROUP_ID_ varchar(255),
  TYPE_ varchar(255),
  USER_ID_ varchar(255),
  TASK_ID_ varchar(64),
  PROC_DEF_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_RU_VARIABLE (
  ID_ varchar(64) not null,
  REV_ integer,
  TYPE_ varchar(255) not null,
  NAME_ varchar(255) not null,
  EXECUTION_ID_ varchar(64),
  PROC_INST_ID_ varchar(64),
  CASE_EXECUTION_ID_ varchar(64),
  CASE_INST_ID_ varchar(64),
  TASK_ID_ varchar(64),
  BYTEARRAY_ID_ varchar(64),
  DOUBLE_ double precision,
  LONG_ bigint,
  TEXT_ lvarchar(4000),
  TEXT2_ lvarchar(4000),
  VAR_SCOPE_ varchar(64) not null,
  SEQUENCE_COUNTER_ bigint,
  IS_CONCURRENT_LOCAL_ boolean,
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_RU_EVENT_SUBSCR (
  ID_ varchar(64) not null,
  REV_ integer,
  EVENT_TYPE_ varchar(255) not null,
  EVENT_NAME_ varchar(255),
  EXECUTION_ID_ varchar(64),
  PROC_INST_ID_ varchar(64),
  ACTIVITY_ID_ varchar(64),
  CONFIGURATION_ varchar(255),
  CREATED_ datetime year to fraction(5) not null,
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_RU_INCIDENT (
  ID_ varchar(64) not null,
  REV_ integer not null,
  INCIDENT_TIMESTAMP_ datetime year to fraction(5) not null,
  INCIDENT_MSG_ lvarchar(4000),
  INCIDENT_TYPE_ varchar(255) not null,
  EXECUTION_ID_ varchar(64),
  ACTIVITY_ID_ varchar(255),
  PROC_INST_ID_ varchar(64),
  PROC_DEF_ID_ varchar(64),
  CAUSE_INCIDENT_ID_ varchar(64),
  ROOT_CAUSE_INCIDENT_ID_ varchar(64),
  CONFIGURATION_ varchar(255),
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_RU_AUTHORIZATION (
  ID_ varchar(64) not null,
  REV_ integer not null,
  TYPE_ integer not null,
  GROUP_ID_ varchar(255),
  USER_ID_ varchar(255),
  RESOURCE_TYPE_ integer not null,
  RESOURCE_ID_ varchar(64),
  PERMS_ integer,
  primary key (ID_)
);

create table if not exists ACT_RU_FILTER (
  ID_ varchar(64) not null,
  REV_ integer not null,
  RESOURCE_TYPE_ varchar(255) not null,
  NAME_ varchar(255) not null,
  OWNER_ varchar(255),
  QUERY_ text not null,
  PROPERTIES_ text,
  primary key (ID_)
);

create table ACT_RU_METER_LOG (
  ID_ varchar(64) not null,
  NAME_ varchar(64) not null,
  REPORTER_ varchar(255),
  VALUE_ bigint,
  TIMESTAMP_ datetime year to fraction(5) not null,
  primary key (ID_)
);

create table ACT_RU_EXT_TASK (
  ID_ varchar(64) not null,
  REV_ integer not null,
  WORKER_ID_ varchar(255),
  TOPIC_NAME_ varchar(255),
  RETRIES_ integer,
  ERROR_MSG_ lvarchar(4000),
  LOCK_EXP_TIME_ datetime year to fraction(5),
  SUSPENSION_STATE_ integer,
  EXECUTION_ID_ varchar(64),
  PROC_INST_ID_ varchar(64),
  PROC_DEF_ID_ varchar(64),
  PROC_DEF_KEY_ varchar(255),
  ACT_ID_ varchar(255),
  ACT_INST_ID_ varchar(64),
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create table ACT_RU_BATCH (
  ID_ varchar(64) not null,
  REV_ integer not null,
  TYPE_ varchar(255),
  SIZE_ integer,
  JOBS_PER_SEED_ integer,
  INVOCATIONS_PER_JOB_ integer,
  SEED_JOB_DEF_ID_ varchar(64),
  BATCH_JOB_DEF_ID_ varchar(64),
  MONITOR_JOB_DEF_ID_ varchar(64),
  CONFIGURATION_ varchar(255),
  TENANT_ID_ varchar(64),
  primary key (ID_)
);

create index if not exists ACT_IDX_EXEC_BUSKEY on ACT_RU_EXECUTION(BUSINESS_KEY_);
create index if not exists ACT_IDX_EXEC_TENANT_ID on ACT_RU_EXECUTION(TENANT_ID_);
create index if not exists ACT_IDX_TASK_CREATE on ACT_RU_TASK(CREATE_TIME_);
create index if not exists ACT_IDX_TASK_ASSIGNEE on ACT_RU_TASK(ASSIGNEE_);
create index if not exists ACT_IDX_TASK_TENANT_ID on ACT_RU_TASK(TENANT_ID_);
create index if not exists ACT_IDX_IDENT_LNK_USER on ACT_RU_IDENTITYLINK(USER_ID_);
create index if not exists ACT_IDX_IDENT_LNK_GROUP on ACT_RU_IDENTITYLINK(GROUP_ID_);
create index if not exists ACT_IDX_EVENT_SUBSCR_CONFIG_ on ACT_RU_EVENT_SUBSCR(CONFIGURATION_);
create index if not exists ACT_IDX_EVENT_SUBSCR_TENANT_ID on ACT_RU_EVENT_SUBSCR(TENANT_ID_);
create index if not exists ACT_IDX_VARIABLE_TASK_ID on ACT_RU_VARIABLE(TASK_ID_);
create index if not exists ACT_IDX_VARIABLE_TENANT_ID on ACT_RU_VARIABLE(TENANT_ID_);
create index if not exists ACT_IDX_ATHRZ_PROCEDEF  on ACT_RU_IDENTITYLINK(PROC_DEF_ID_);
create index if not exists ACT_IDX_INC_CONFIGURATION on ACT_RU_INCIDENT(CONFIGURATION_);
create index if not exists ACT_IDX_INC_TENANT_ID on ACT_RU_INCIDENT(TENANT_ID_);
create index if not exists ACT_IDX_JOB_PROCINST on ACT_RU_JOB(PROCESS_INSTANCE_ID_);
create index if not exists ACT_IDX_JOB_TENANT_ID on ACT_RU_JOB(TENANT_ID_);
create index if not exists ACT_IDX_JOBDEF_TENANT_ID on ACT_RU_JOBDEF(TENANT_ID_);
create index if not exists ACT_IDX_METER_LOG on ACT_RU_METER_LOG(NAME_,TIMESTAMP_);
create index if not exists ACT_IDX_EXT_TASK_TOPIC ON ACT_RU_EXT_TASK(TOPIC_NAME_);
create index if not exists ACT_IDX_EXT_TASK_TENANT_ID ON ACT_RU_EXT_TASK(TENANT_ID_);
create index if not exists ACT_IDX_AUTH_GROUP_ID ON ACT_RU_AUTHORIZATION(GROUP_ID_);
create index if not exists ACT_IDX_JOB_JOB_DEF_ID on ACT_RU_JOB(JOB_DEF_ID_);

-- indexes for deadlock problems - https://app.camunda.com/jira/browse/CAM-2567 --
create index if not exists ACT_IDX_INC_CAUSEINCID on ACT_RU_INCIDENT(CAUSE_INCIDENT_ID_);
create index if not exists ACT_IDX_INC_EXID on ACT_RU_INCIDENT(EXECUTION_ID_);
create index if not exists ACT_IDX_INC_PROCDEFID on ACT_RU_INCIDENT(PROC_DEF_ID_);
create index if not exists ACT_IDX_INC_PROCINSTID on ACT_RU_INCIDENT(PROC_INST_ID_);
create index if not exists ACT_IDX_INC_ROOTCAUSEINCID on ACT_RU_INCIDENT(ROOT_CAUSE_INCIDENT_ID_);

alter table ACT_GE_BYTEARRAY
  add constraint foreign key (DEPLOYMENT_ID_)
  references ACT_RE_DEPLOYMENT (ID_)
  constraint ACT_FK_BYTEARR_DEPL;

alter table ACT_RU_EXECUTION
  add constraint foreign key (PROC_INST_ID_)
  references ACT_RU_EXECUTION (ID_)
  constraint ACT_FK_EXE_PROCINST;

alter table ACT_RU_EXECUTION
  add constraint foreign key (PARENT_ID_)
  references ACT_RU_EXECUTION (ID_)
  constraint ACT_FK_EXE_PARENT;
  
alter table ACT_RU_EXECUTION
  add constraint foreign key (SUPER_EXEC_)
  references ACT_RU_EXECUTION (ID_)
  constraint ACT_FK_EXE_SUPER;
  
alter table ACT_RU_EXECUTION
  add constraint foreign key (PROC_DEF_ID_)
  references ACT_RE_PROCDEF (ID_)
  constraint ACT_FK_EXE_PROCDEF;

alter table ACT_RU_IDENTITYLINK
  add constraint foreign key (TASK_ID_)
  references ACT_RU_TASK (ID_)
  constraint ACT_FK_TSKASS_TASK;

alter table ACT_RU_IDENTITYLINK
  add constraint foreign key (PROC_DEF_ID_)
  references ACT_RE_PROCDEF (ID_)
  constraint ACT_FK_ATHRZ_PROCEDEF;

alter table ACT_RU_TASK
  add constraint foreign key (EXECUTION_ID_)
  references ACT_RU_EXECUTION (ID_)
  constraint ACT_FK_TASK_EXE;
  
alter table ACT_RU_TASK
  add constraint foreign key (PROC_INST_ID_)
  references ACT_RU_EXECUTION (ID_)
  constraint ACT_FK_TASK_PROCINST;
  
alter table ACT_RU_TASK
  add constraint foreign key (PROC_DEF_ID_)
  references ACT_RE_PROCDEF (ID_)
  constraint ACT_FK_TASK_PROCDEF;
  
alter table ACT_RU_VARIABLE 
  add constraint foreign key (EXECUTION_ID_)
  references ACT_RU_EXECUTION (ID_)
  constraint ACT_FK_VAR_EXE;

alter table ACT_RU_VARIABLE
  add constraint foreign key (PROC_INST_ID_)
  references ACT_RU_EXECUTION(ID_)
  constraint ACT_FK_VAR_PROCINST;

alter table ACT_RU_VARIABLE 
  add constraint foreign key (BYTEARRAY_ID_)
  references ACT_GE_BYTEARRAY (ID_)
  constraint ACT_FK_VAR_BYTEARRAY;

alter table ACT_RU_JOB 
  add constraint foreign key (EXCEPTION_STACK_ID_)
  references ACT_GE_BYTEARRAY (ID_)
  constraint ACT_FK_JOB_EXCEPTION;
  
alter table ACT_RU_EVENT_SUBSCR
  add constraint foreign key (EXECUTION_ID_)
  references ACT_RU_EXECUTION(ID_)
  constraint ACT_FK_EVENT_EXEC;
  
alter table ACT_RU_INCIDENT
  add constraint foreign key (EXECUTION_ID_)
  references ACT_RU_EXECUTION (ID_)
  constraint ACT_FK_INC_EXE;
  
alter table ACT_RU_INCIDENT
  add constraint foreign key (PROC_INST_ID_)
  references ACT_RU_EXECUTION (ID_)
  constraint ACT_FK_INC_PROCINST;

alter table ACT_RU_INCIDENT
  add constraint foreign key (PROC_DEF_ID_)
  references ACT_RE_PROCDEF (ID_)
  constraint ACT_FK_INC_PROCDEF;
  
alter table ACT_RU_INCIDENT
  add constraint foreign key (CAUSE_INCIDENT_ID_)
  references ACT_RU_INCIDENT (ID_)
  constraint ACT_FK_INC_CAUSE;

alter table ACT_RU_INCIDENT
  add constraint foreign key (ROOT_CAUSE_INCIDENT_ID_)
  references ACT_RU_INCIDENT (ID_)
  constraint ACT_FK_INC_RCAUSE;

alter table ACT_RU_EXT_TASK
  add constraint foreign key (EXECUTION_ID_)
  references ACT_RU_EXECUTION (ID_)
  constraint ACT_FK_EXT_TASK_EXE;

alter table ACT_RU_BATCH
    add constraint foreign key (SEED_JOB_DEF_ID_)
    references ACT_RU_JOBDEF (ID_)
    constraint ACT_FK_BATCH_SEED_JOB_DEF;

alter table ACT_RU_BATCH
    add constraint foreign key (MONITOR_JOB_DEF_ID_)
    references ACT_RU_JOBDEF (ID_)
    constraint ACT_FK_BATCH_MONITOR_JOB_DEF;

alter table ACT_RU_BATCH
    add constraint foreign key (BATCH_JOB_DEF_ID_)
    references ACT_RU_JOBDEF (ID_)
    constraint ACT_FK_BATCH_JOB_DEF;

-- These functions cannot be created atomatically, because every line is terminated by a semicolon and therefore executed seperately. --
create function if not exists ACT_FCT_USER_ID_OR_ID_(USER_ID_ varchar(255),ID_ varchar(64)) returning varchar(255) with (not variant); return nvl(USER_ID_,ID_); end function;
create function if not exists ACT_FCT_GROUP_ID_OR_ID_(GROUP_ID_ varchar(255),ID_ varchar(64)) returning varchar(255) with (not variant); return nvl(GROUP_ID_,ID_); end function;
create function if not exists ACT_FCT_RESOURCE_ID_OR_ID_(RESOURCE_ID_ varchar(64),ID_ varchar(64)) returning varchar(64) with (not variant); return nvl(RESOURCE_ID_,ID_); end function;

create unique index if not exists ACT_UNIQ_AUTH_USER on ACT_RU_AUTHORIZATION(TYPE_,ACT_FCT_USER_ID_OR_ID_(USER_ID_,ID_),RESOURCE_TYPE_,ACT_FCT_RESOURCE_ID_OR_ID_(RESOURCE_ID_,ID_));
create unique index if not exists ACT_UNIQ_AUTH_GROUP on ACT_RU_AUTHORIZATION(TYPE_,ACT_FCT_GROUP_ID_OR_ID_(GROUP_ID_,ID_),RESOURCE_TYPE_,ACT_FCT_RESOURCE_ID_OR_ID_(RESOURCE_ID_,ID_));

alter table ACT_RU_VARIABLE
  add constraint unique (VAR_SCOPE_,NAME_)
  constraint ACT_UNIQ_VARIABLE;

-- index for deadlock problem - https://app.camunda.com/jira/browse/CAM-4440 --
create index if not exists ACT_IDX_AUTH_RESOURCE_ID on ACT_RU_AUTHORIZATION(RESOURCE_ID_);
-- index to prevent deadlock on fk constraint - https://app.camunda.com/jira/browse/CAM-5440 --
create index if not exists ACT_IDX_EXT_TASK_EXEC on ACT_RU_EXT_TASK(EXECUTION_ID_);

-- indexes to improve deployment
create index if not exists ACT_IDX_BYTEARRAY_NAME on ACT_GE_BYTEARRAY(NAME_);
create index if not exists ACT_IDX_DEPLOYMENT_NAME on ACT_RE_DEPLOYMENT(NAME_);
create index if not exists ACT_IDX_DEPLOYMENT_TENANT_ID on ACT_RE_DEPLOYMENT(TENANT_ID_);
create index if not exists ACT_IDX_JOBDEF_PROC_DEF_ID on ACT_RU_JOBDEF(PROC_DEF_ID_);
create index if not exists ACT_IDX_JOB_HANDLER_TYPE on ACT_RU_JOB(HANDLER_TYPE_);
create index if not exists ACT_IDX_EVENT_SUBSCR_EVT_NAME on ACT_RU_EVENT_SUBSCR(EVENT_NAME_);
create index if not exists ACT_IDX_PROCDEF_DEPLOYMENT_ID on ACT_RE_PROCDEF(DEPLOYMENT_ID_);
create index if not exists ACT_IDX_PROCDEF_TENANT_ID ON ACT_RE_PROCDEF(TENANT_ID_);

create function if not exists QUARTER(DT date) returning integer with (not variant); return 1 + trunc((month(DT)-1)/3,0); end function;
