-- schema

-- !Ups
CREATE TABLE HEADERS
(
    ID        VARCHAR(64) NOT NULL,
    PARENT_ID VARCHAR(64) NOT NULL,
    HEIGHT    INTEGER     NOT NULL,
    TIMESTAMP BIGINT      NOT NULL,
    PRIMARY KEY (ID)
);


CREATE INDEX "HEADERS__PARENT_ID" ON HEADERS (PARENT_ID);
CREATE INDEX "HEADERS__HEIGHT" ON HEADERS (HEIGHT);
CREATE INDEX "HEADERS__TIMESTAMP" ON HEADERS (TIMESTAMP);

CREATE TABLE HEADERS_FORK
(
    ID        VARCHAR(64) NOT NULL,
    PARENT_ID VARCHAR(64) NOT NULL,
    HEIGHT    INTEGER     NOT NULL,
    TIMESTAMP BIGINT      NOT NULL,

    PRIMARY KEY (ID)
);

CREATE INDEX "HEADERS_FORK_F__PARENT_ID" ON HEADERS_FORK (PARENT_ID);
CREATE INDEX "HEADERS_FORK_F__HEIGHT" ON HEADERS_FORK (HEIGHT);
CREATE INDEX "HEADERS_FORK_F__TIMESTAMP" ON HEADERS_FORK (TIMESTAMP);

CREATE TABLE OUTPUTS
(
    BOX_ID                   VARCHAR(64) NOT NULL,
    TX_ID                    VARCHAR(64) NOT NULL,
    HEADER_ID                VARCHAR(64) NOT NULL,
    VALUE                    BIGINT      NOT NULL,
    CREATION_HEIGHT          INTEGER     NOT NULL,
    INDEX                    INTEGER     NOT NULL,
    ERGO_TREE                VARCHAR     NOT NULL,
    TIMESTAMP                BIGINT      NOT NULL,
    BYTES                    BLOB        NOT NULL,
    WITHDRAW_ADDRESS         VARCHAR(255),
    STEALTH_ID               VARCHAR(255),
    WITHDRAW_TX_ID           VARCHAR(255),
    WITHDRAW_TX_CREATED_TIME BIGINT,
    WITHDRAW_FAILED_REASON   VARCHAR(255),
    SPEND_BLOCK_ID           VARCHAR(255),
    SPEND_TX_ID              VARCHAR(255),
    SPEND_BLOCK_HEIGHT       INTEGER,
    CHECK (("WITHDRAW_TX_CREATED_TIME" >= 0)),
    CONSTRAINT PK_OUTPUTS PRIMARY KEY (BOX_ID, HEADER_ID)
);

CREATE INDEX "OUTPUTS__BOX_ID" ON OUTPUTS (BOX_ID);
CREATE INDEX "OUTPUTS__TX_ID" ON OUTPUTS (TX_ID);
CREATE INDEX "OUTPUTS__HEADER_ID" ON OUTPUTS (HEADER_ID);
CREATE INDEX "OUTPUTS__ERGO_TREE" ON OUTPUTS (ERGO_TREE);
CREATE INDEX "OUTPUTS__TIMESTAMP" ON OUTPUTS (TIMESTAMP);


CREATE TABLE OUTPUTS_FORK
(
    BOX_ID                   VARCHAR(64) NOT NULL,
    TX_ID                    VARCHAR(64) NOT NULL,
    HEADER_ID                VARCHAR(64) NOT NULL,
    VALUE                    BIGINT      NOT NULL,
    CREATION_HEIGHT          INTEGER     NOT NULL,
    INDEX                    INTEGER     NOT NULL,
    ERGO_TREE                VARCHAR     NOT NULL,
    TIMESTAMP                BIGINT      NOT NULL,
    BYTES                    BLOB        NOT NULL,
    WITHDRAW_ADDRESS         VARCHAR(255),
    STEALTH_ID               VARCHAR(255),
    WITHDRAW_TX_ID           VARCHAR(255),
    WITHDRAW_TX_CREATED_TIME BIGINT,
    WITHDRAW_FAILED_REASON   VARCHAR(255),
    SPEND_BLOCK_ID           VARCHAR(255),
    SPEND_TX_ID              VARCHAR(255),
    SPEND_BLOCK_HEIGHT       INTEGER,
    CHECK (("WITHDRAW_TX_CREATED_TIME" >= 0)),
    CONSTRAINT PK_OUTPUTS_FORK PRIMARY KEY (BOX_ID, HEADER_ID)
);

CREATE INDEX "OUTPUTS_FORK_F__BOX_ID" ON OUTPUTS_FORK (BOX_ID);
CREATE INDEX "OUTPUTS_FORK_F__TX_ID" ON OUTPUTS_FORK (TX_ID);
CREATE INDEX "OUTPUTS_FORK_F__HEADER_ID" ON OUTPUTS_FORK (HEADER_ID);
CREATE INDEX "OUTPUTS_FORK_F__ERGO_TREE" ON OUTPUTS_FORK (ERGO_TREE);
CREATE INDEX "OUTPUTS_FORK_F__TIMESTAMP" ON OUTPUTS_FORK (TIMESTAMP);


CREATE TABLE STEALTH
(
    STEALTH_ID   VARCHAR(255) NOT NULL,
    SECRET       DECIMAL(100) NOT NULL,
    STEALTH_NAME VARCHAR(255) NOT NULL,
    PK           VARCHAR(255) NOT NULL,
    PRIMARY KEY (STEALTH_ID)
);

CREATE INDEX "STEALTH__STEALTH_ID" ON STEALTH (STEALTH_ID);


CREATE TABLE TOKEN_INFORMATION
(
    ID   VARCHAR(64) NOT NULL,
    NAME VARCHAR(255),
    DECIMALS    INTEGER,
    PRIMARY KEY (ID)
);

CREATE INDEX "TOKEN_INFORMATION__ID" ON TOKEN_INFORMATION (ID);

-- !Downs
DROP TABLE HEADERS;
DROP TABLE HEADERS_FORK;
DROP TABLE OUTPUTS;
DROP TABLE OUTPUTS_FORK;
DROP TABLE STEALTH;
DROP TABLE TOKEN_INFORMATION;