-- schema

-- !Ups
create table MIXING_COVERT_REQUEST
(
    MIX_GROUP_ID        VARCHAR(255) not null
        primary key,
    CREATED_TIME        BIGINT,
    DEPOSIT_ADDRESS     VARCHAR(255),
    NUM_TOKEN           INT,
    MASTER_SECRET_GROUP DECIMAL(100),
    check (("CREATED_TIME" >= 0)),
    check (("NUM_TOKEN" >= 0))
);

create table COVERT_DEFAULTS
(
    MIX_GROUP_ID        VARCHAR(255) not null,
    TOKEN_ID            VARCHAR(255) not null,
    MIXING_TOKEN_AMOUNT BIGINT,
    DEPOSIT_DONE        BIGINT,
    LAST_ACTIVITY       BIGINT,
    primary key (MIX_GROUP_ID, TOKEN_ID),
    check (("MIXING_TOKEN_AMOUNT" >= 0)),
    check (("DEPOSIT_DONE" >= 0)),
    check (("LAST_ACTIVITY" >= 0))
);

create table COVERT_ADDRESSES
(
    MIX_GROUP_ID VARCHAR(255)   not null,
    ADDRESS      VARCHAR(10000) not null,
    primary key (MIX_GROUP_ID, ADDRESS)
);

create table MIXING_GROUP_REQUEST
(
    MIX_GROUP_ID        VARCHAR(255) not null
        primary key,
    AMOUNT              BIGINT,
    STATUS              VARCHAR(255),
    CREATED_TIME        BIGINT,
    DEPOSIT_ADDRESS     VARCHAR(255),
    DEPOSIT_DONE        BIGINT,
    DEPOSIT_DONE_TOKEN  BIGINT,
    MIXING_AMOUNT       BIGINT,
    MIXING_TOKEN_AMOUNT BIGINT,
    MIXING_TOKEN_NEEDED BIGINT,
    TOKEN_ID            VARCHAR(255),
    MASTER_SECRET_GROUP DECIMAL(100),
    check (("AMOUNT" >= 0)),
    check (("CREATED_TIME" >= 0)),
    check (("DEPOSIT_DONE" >= 0)),
    check (("DEPOSIT_DONE_TOKEN" >= 0)),
    check (("MIXING_AMOUNT" >= 0)),
    check (("MIXING_TOKEN_AMOUNT" >= 0)),
    check (("MIXING_TOKEN_NEEDED" >= 0))
);

create table DISTRIBUTE_TRANSACTIONS
(
    MIX_GROUP_ID VARCHAR(255),
    TX_ID        VARCHAR(255) not null
        primary key,
    ORDER_NUM    INT,
    CREATED_TIME BIGINT,
    TX           BLOB,
    INPUTS       VARCHAR(10000),
    check (("ORDER_NUM" >= 0)),
    check (("CREATED_TIME" >= 0))
);

create table MIXING_REQUESTS
(
    MIX_ID              VARCHAR(255) not null
        primary key,
    MIX_GROUP_ID        VARCHAR(255),
    AMOUNT              BIGINT,
    NUM_ROUNDS          INT,
    STATUS              VARCHAR(255),
    CREATED_TIME        BIGINT,
    WITHDRAW_ADDRESS    VARCHAR(10000),
    DEPOSIT_ADDRESS     VARCHAR(255),
    DEPOSIT_COMPLETED   BOOLEAN,
    NEEDED_AMOUNT       BIGINT,
    NUM_TOKEN           INT,
    WITHDRAW_STATUS     VARCHAR(255),
    MIXING_TOKEN_AMOUNT BIGINT,
    MIXING_TOKEN_NEEDED BIGINT,
    TOKEN_ID            VARCHAR(255),
    MASTER_SECRET       DECIMAL(100),
    check (("AMOUNT" >= 0)),
    check (("NUM_ROUNDS" >= 0)),
    check (("CREATED_TIME" >= 0)),
    check (("NEEDED_AMOUNT" >= 0)),
    check (("NUM_TOKEN" >= 0)),
    check (("MIXING_TOKEN_AMOUNT" >= 0)),
    check (("MIXING_TOKEN_NEEDED" >= 0))
);

create table UNSPENT_DEPOSITS
(
    ADDRESS             VARCHAR(10000),
    BOX_ID              VARCHAR(255) not null
        primary key,
    AMOUNT              BIGINT,
    CREATED_TIME        BIGINT,
    MIXING_TOKEN_AMOUNT BIGINT,
    check (("AMOUNT" >= 0)),
    check (("CREATED_TIME" >= 0)),
    check (("MIXING_TOKEN_AMOUNT" >= 0))
);

create table UNSPENT_DEPOSITS_ARCHIVED
(
    ADDRESS             VARCHAR(10000),
    BOX_ID              VARCHAR(255),
    AMOUNT              BIGINT,
    CREATED_TIME        BIGINT,
    MIXING_TOKEN_AMOUNT BIGINT,
    REASON              VARCHAR(255),
    check (("AMOUNT" >= 0)),
    check (("CREATED_TIME" >= 0)),
    check (("MIXING_TOKEN_AMOUNT" >= 0))
);

create table SPENT_DEPOSITS
(
    ADDRESS             VARCHAR(10000),
    BOX_ID              VARCHAR(255) not null
        primary key,
    AMOUNT              BIGINT,
    CREATED_TIME        BIGINT,
    MIXING_TOKEN_AMOUNT BIGINT,
    TX_ID               VARCHAR(255),
    SPENT_TIME          BIGINT,
    PURPOSE             VARCHAR(255),
    check (("AMOUNT" >= 0)),
    check (("CREATED_TIME" >= 0)),
    check (("MIXING_TOKEN_AMOUNT" >= 0)),
    check (("SPENT_TIME" >= 0))
);

create table SPENT_DEPOSITS_ARCHIVED
(
    ADDRESS             VARCHAR(10000),
    BOX_ID              VARCHAR(255),
    AMOUNT              BIGINT,
    CREATED_TIME        BIGINT,
    MIXING_TOKEN_AMOUNT BIGINT,
    TX_ID               VARCHAR(255),
    SPENT_TIME          BIGINT,
    PURPOSE             VARCHAR(255),
    REASON              VARCHAR(255),
    check (("AMOUNT" >= 0)),
    check (("CREATED_TIME" >= 0)),
    check (("MIXING_TOKEN_AMOUNT" >= 0)),
    check (("SPENT_TIME" >= 0))
);

create table MIX_STATE
(
    MIX_ID   VARCHAR(255) not null
        primary key,
    ROUND    INT,
    IS_ALICE BOOLEAN,
    check (("ROUND" >= 0))
);

create table HALF_MIX
(
    MIX_ID          VARCHAR(255) not null,
    ROUND           INT          not null,
    CREATED_TIME    BIGINT,
    HALF_MIX_BOX_ID VARCHAR(255),
    IS_SPENT        BOOLEAN,
    primary key (MIX_ID, ROUND),
    check (("ROUND" >= 0)),
    check (("CREATED_TIME" >= 0))
);

create table HALF_MIX_ARCHIVED
(
    MIX_ID          VARCHAR(255),
    ROUND           INT,
    CREATED_TIME    BIGINT,
    HALF_MIX_BOX_ID VARCHAR(255),
    IS_SPENT        BOOLEAN,
    REASON          VARCHAR(255),
    check (("ROUND" >= 0)),
    check (("CREATED_TIME" >= 0))
);

create table FULL_MIX
(
    MIX_ID          VARCHAR(255) not null,
    ROUND           INT          not null,
    CREATED_TIME    BIGINT,
    HALF_MIX_BOX_ID VARCHAR(255),
    FULL_MIX_BOX_ID VARCHAR(255),
    primary key (MIX_ID, ROUND),
    check (("ROUND" >= 0)),
    check (("CREATED_TIME" >= 0))
);

create table FULL_MIX_ARCHIVED
(
    MIX_ID          VARCHAR(255),
    ROUND           INT,
    CREATED_TIME    BIGINT,
    HALF_MIX_BOX_ID VARCHAR(255),
    FULL_MIX_BOX_ID VARCHAR(255),
    REASON          VARCHAR(255),
    check (("ROUND" >= 0)),
    check (("CREATED_TIME" >= 0))
);

create table MIX_STATE_HISTORY
(
    MIX_ID       VARCHAR(255) not null,
    ROUND        INT          not null,
    IS_ALICE     BOOLEAN,
    CREATED_TIME BIGINT,
    primary key (MIX_ID, ROUND),
    check (("ROUND" >= 0)),
    check (("CREATED_TIME" >= 0))
);

create table MIX_STATE_HISTORY_ARCHIVED
(
    MIX_ID       VARCHAR(255),
    ROUND        INT,
    IS_ALICE     BOOLEAN,
    CREATED_TIME BIGINT,
    REASON       VARCHAR(255),
    check (("ROUND" >= 0)),
    check (("CREATED_TIME" >= 0))
);

create table WITHDRAW
(
    MIX_ID       VARCHAR(255) not null
        primary key,
    TX_ID        VARCHAR(255),
    CREATED_TIME BIGINT,
    BOX_ID       VARCHAR(255),
    TX           BLOB,
    check (("CREATED_TIME" >= 0))
);

create table WITHDRAW_ARCHIVED
(
    MIX_ID          VARCHAR(255),
    TX_ID           VARCHAR(255),
    CREATED_TIME    BIGINT,
    FULL_MIX_BOX_ID VARCHAR(255),
    TX              BLOB,
    REASON          VARCHAR(255),
    check (("CREATED_TIME" >= 0))
);

create table MIX_TRANSACTIONS
(
    BOX_ID VARCHAR(255) not null
        primary key,
    TX_ID  VARCHAR(255),
    TX     BLOB
);

create table EMISSION_BOX
(
    MIX_ID VARCHAR(255),
    ROUND  INT,
    BOX_ID VARCHAR(255) not null
        primary key,
    check (("ROUND" >= 0))
);

create table TOKEN_EMISSION_BOX
(
    MIX_ID VARCHAR(255) not null
        primary key,
    BOX_ID VARCHAR(255)
);

create table RESCAN
(
    MIX_ID         VARCHAR(255) not null
        primary key,
    CREATED_TIME   BIGINT,
    ROUND          INT,
    GO_BACKWARD    BOOLEAN,
    IS_HALF_MIX_TX BOOLEAN,
    MIX_BOX_ID     VARCHAR(255),
    check (("CREATED_TIME" >= 0)),
    check (("ROUND" >= 0))
);

create table RESCAN_ARCHIVE
(
    MIX_ID         VARCHAR(255),
    CREATED_TIME   BIGINT,
    ROUND          INT,
    GO_BACKWARD    BOOLEAN,
    IS_HALF_MIX_TX BOOLEAN,
    MIX_BOX_ID     VARCHAR(255),
    REASON         VARCHAR(255),
    check (("CREATED_TIME" >= 0)),
    check (("ROUND" >= 0))
);


-- !Downs

