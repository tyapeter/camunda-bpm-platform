create table if not exists ACT_ID_GROUP (
  ID_ varchar(64) not null,
  REV_ integer,
  NAME_ varchar(255),
  TYPE_ varchar(255),
  primary key (ID_)
);

create table if not exists ACT_ID_MEMBERSHIP (
  USER_ID_ varchar(64) not null,
  GROUP_ID_ varchar(64) not null,
  primary key (USER_ID_, GROUP_ID_)
);

create table if not exists ACT_ID_USER (
  ID_ varchar(64) not null,
  REV_ integer,
  FIRST_ varchar(255),
  LAST_ varchar(255),
  EMAIL_ varchar(255),
  PWD_ varchar(255),
  PICTURE_ID_ varchar(64),
  primary key (ID_)
);

create table if not exists ACT_ID_INFO (
  ID_ varchar(64) not null,
  REV_ integer,
  USER_ID_ varchar(64),
  TYPE_ varchar(64),
  KEY_ varchar(255),
  VALUE_ varchar(255),
  PASSWORD_ byte,
  PARENT_ID_ varchar(255),
  primary key (ID_)
);

create table if not exists ACT_ID_TENANT (
  ID_ varchar(64) not null,
  REV_ integer,
  NAME_ varchar(255),
  primary key (ID_)
);

create table if not exists ACT_ID_TENANT_MEMBER (
  ID_ varchar(64) not null,
  TENANT_ID_ varchar(64) not null,
  USER_ID_ varchar(64),
  GROUP_ID_ varchar(64),
  primary key (ID_)
);

alter table ACT_ID_MEMBERSHIP 
  add constraint foreign key (GROUP_ID_)
  references ACT_ID_GROUP (ID_)
  constraint ACT_FK_MEMB_GROUP;

alter table ACT_ID_MEMBERSHIP 
  add constraint foreign key (USER_ID_)
  references ACT_ID_USER (ID_)
  constraint ACT_FK_MEMB_USER;

alter table ACT_ID_TENANT_MEMBER
  add constraint unique (TENANT_ID_, USER_ID_)
  constraint ACT_UNIQ_TENANT_MEMB_USER;

alter table ACT_ID_TENANT_MEMBER
  add constraint unique (TENANT_ID_, GROUP_ID_)
  constraint ACT_UNIQ_TENANT_MEMB_GROUP;

alter table ACT_ID_TENANT_MEMBER
  add constraint foreign key (TENANT_ID_)
  references ACT_ID_TENANT
  constraint ACT_FK_TENANT_MEMB;

alter table ACT_ID_TENANT_MEMBER
  add constraint foreign key (USER_ID_)
  references ACT_ID_USER
  constraint ACT_FK_TENANT_MEMB_USER;

alter table ACT_ID_TENANT_MEMBER
  add constraint foreign key (GROUP_ID_)
  references ACT_ID_GROUP
  constraint ACT_FK_TENANT_MEMB_GROUP;
