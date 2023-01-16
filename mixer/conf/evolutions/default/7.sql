-- !Ups
create table INCOME_STATE
(
    ORDER_NUM     INT,
    RETRY_NUM     INT,
    TX_ID         VARCHAR(255)   not null,
    check (("ORDER_NUM" >= 0)),
    primary key (TX_ID)
);

create table COMMISSION_INCOME
(
    TIMESTAMP       BIGINT       not null,
    TOKEN_ID        VARCHAR(255) not null,
    RING            BIGINT       not null,
    NUM_ENTERED     INT,
    COMMISSION      BIGINT,
    DONATION        BIGINT,
    primary key (TIMESTAMP, TOKEN_ID, RING),
    check (("TIMESTAMP" >= 0)),
    check (("RING" >= 0)),
    check (("NUM_ENTERED" >= 0)),
    check (("COMMISSION" >= 0)),
    check (("DONATION" >= 0))
);

create table TOKEN_INCOME
(
    TIMESTAMP    BIGINT not null,
    MIXING_LEVEL INT    not null,
    NUM_ENTERED   INT,
    AMOUNT       BIGINT,
    primary key (TIMESTAMP, MIXING_LEVEL),
    check (("TIMESTAMP" >= 0)),
    check (("MIXING_LEVEL" >= 0)),
    check (("NUM_ENTERED" >= 0)),
    check (("AMOUNT" >= 0))
);

-- !Downs
DROP TABLE INCOME_STATE;
DROP TABLE COMMISSION_INCOME;
DROP TABLE TOKEN_INCOME;
