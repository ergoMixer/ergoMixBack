-- schema

-- !Ups
CREATE TABLE headers
(
    id        VARCHAR(64) NOT NULL,
    parent_id VARCHAR(64) NOT NULL,
    height    INTEGER     NOT NULL,
    timestamp BIGINT      NOT NULL,
    PRIMARY KEY (id)
);


CREATE INDEX "headers__parent_id" ON headers (parent_id);
CREATE INDEX "headers__height" ON headers (height);
CREATE INDEX "headers__ts" ON headers (timestamp);

CREATE TABLE headers_fork
(
    id        VARCHAR(64) NOT NULL,
    parent_id VARCHAR(64) NOT NULL,
    height    INTEGER     NOT NULL,
    timestamp BIGINT      NOT NULL,

    PRIMARY KEY (id)
);

CREATE INDEX "headers_fork_f__parent_id" ON headers_fork (parent_id);
CREATE INDEX "headers_fork_f__height" ON headers_fork (height);
CREATE INDEX "headers_fork_f__ts" ON headers_fork (timestamp);


CREATE TABLE transactions
(
    id               VARCHAR(64) NOT NULL,
    header_id        VARCHAR(64) REFERENCES headers (id),
    inclusion_height INTEGER     NOT NULL,
    timestamp        BIGINT      NOT NULL,
    PRIMARY KEY (id, header_id)
);

CREATE INDEX "transactions__header_id" ON transactions (header_id);
CREATE INDEX "transactions__timestamp" ON transactions (timestamp);
CREATE INDEX "transactions__inclusion_height" ON transactions (inclusion_height);


CREATE TABLE transactions_fork
(
    id               VARCHAR(64) NOT NULL,
    header_id        VARCHAR(64) REFERENCES headers_fork (id),
    inclusion_height INTEGER     NOT NULL,
    timestamp        BIGINT      NOT NULL,
    PRIMARY KEY (id, header_id)
);

CREATE INDEX "transactions_fork_f__header_id" ON transactions_fork (header_id);
CREATE INDEX "transactions_fork_f__timestamp" ON transactions_fork (timestamp);
CREATE INDEX "transactions_fork_f__inclusion_height" ON transactions_fork (inclusion_height);


CREATE TABLE inputs
(
    box_id      VARCHAR(64) NOT NULL,
    tx_id       VARCHAR(64) NOT NULL,
    header_id   VARCHAR(64) NOT NULL,
    proof_bytes BLOB,
    index       INTEGER     NOT NULL,
    PRIMARY KEY (box_id, header_id)
);

CREATE INDEX "inputs__tx_id" ON inputs (tx_id);
CREATE INDEX "inputs__box_id" ON inputs (box_id);
CREATE INDEX "inputs__header_id" ON inputs (header_id);


CREATE TABLE inputs_fork
(
    box_id      VARCHAR(64) NOT NULL,
    tx_id       VARCHAR(64) NOT NULL,
    header_id   VARCHAR(64) NOT NULL,
    proof_bytes BLOB,
    index       INTEGER     NOT NULL,

    PRIMARY KEY (box_id, header_id)
);

CREATE INDEX "inputs_fork_f__tx_id" ON inputs_fork (tx_id);
CREATE INDEX "inputs_fork_f__box_id" ON inputs_fork (box_id);
CREATE INDEX "inputs_fork_f__header_id" ON inputs_fork (header_id);


CREATE TABLE data_inputs
(
    box_id    VARCHAR(64) NOT NULL,
    tx_id     VARCHAR(64) NOT NULL,
    header_id VARCHAR(64) NOT NULL,
    index     INTEGER     NOT NULL,

    PRIMARY KEY (box_id, tx_id, header_id)
);

CREATE INDEX "data_inputs__tx_id" ON data_inputs (tx_id);
CREATE INDEX "data_inputs__box_id" ON data_inputs (box_id);
CREATE INDEX "data_inputs__header_id" ON data_inputs (header_id);


CREATE TABLE data_inputs_fork
(
    box_id    VARCHAR(64) NOT NULL,
    tx_id     VARCHAR(64) NOT NULL,
    header_id VARCHAR(64) NOT NULL,
    index     INTEGER     NOT NULL,

    PRIMARY KEY (box_id, tx_id, header_id)
);

CREATE INDEX "data_inputs_fork_f__tx_id" ON data_inputs_fork (tx_id);
CREATE INDEX "data_inputs_fork_f__box_id" ON data_inputs_fork (box_id);
CREATE INDEX "data_inputs_fork_f__header_id" ON data_inputs_fork (header_id);


CREATE TABLE outputs
(
    box_id          VARCHAR(64)  NOT NULL,
    tx_id           VARCHAR(64)  NOT NULL,
    header_id       VARCHAR(64)  NOT NULL,
    value           BIGINT       NOT NULL,
    creation_height INTEGER      NOT NULL,
    index           INTEGER      NOT NULL,
    ergo_tree       VARCHAR      NOT NULL,
    timestamp       BIGINT       NOT NULL,
    bytes           BLOB         NOT NULL,
    spent           BOOLEAN      NOT NULL,
    spend_address   VARCHAR(255) NOT NULL,
    stealth_id      VARCHAR(255) NOT NULL,
    CONSTRAINT PK_OUTPUTS PRIMARY KEY (box_id, header_id)
);

CREATE INDEX "outputs__box_id" ON outputs (box_id);
CREATE INDEX "outputs__tx_id" ON outputs (tx_id);
CREATE INDEX "outputs__header_id" ON outputs (header_id);
CREATE INDEX "outputs__ergo_tree" ON outputs (ergo_tree);
CREATE INDEX "outputs__timestamp" ON outputs (timestamp);


CREATE TABLE outputs_fork
(
    box_id          VARCHAR(64)  NOT NULL,
    tx_id           VARCHAR(64)  NOT NULL,
    header_id       VARCHAR(64)  NOT NULL,
    value           BIGINT       NOT NULL,
    creation_height INTEGER      NOT NULL,
    index           INTEGER      NOT NULL,
    ergo_tree       VARCHAR      NOT NULL,
    timestamp       BIGINT       NOT NULL,
    bytes           BLOB         NOT NULL,
    spent           BOOLEAN      NOT NULL,
    spend_address   VARCHAR(255) NOT NULL,
    stealth_id      VARCHAR(255) NOT NULL,
    CONSTRAINT PK_OUTPUTS_FORK PRIMARY KEY (box_id, header_id)
);

CREATE INDEX "outputs_fork_f__box_id" ON outputs_fork (box_id);
CREATE INDEX "outputs_fork_f__tx_id" ON outputs_fork (tx_id);
CREATE INDEX "outputs_fork_f__header_id" ON outputs_fork (header_id);
CREATE INDEX "outputs_fork_f__ergo_tree" ON outputs_fork (ergo_tree);
CREATE INDEX "outputs_fork_f__timestamp" ON outputs_fork (timestamp);


CREATE TABLE assets
(
    token_id  VARCHAR(64) NOT NULL,
    box_id    VARCHAR(64) NOT NULL,
    header_id VARCHAR(64) NOT NULL,
    index     INTEGER     NOT NULL,
    value     BIGINT      NOT NULL,
    PRIMARY KEY (index, token_id, box_id, header_id)
);

CREATE INDEX "assets__box_id" ON assets (box_id);
CREATE INDEX "assets__token_id" ON assets (token_id);
CREATE INDEX "assets__header_id" ON assets (header_id);


CREATE TABLE assets_fork
(
    token_id  VARCHAR(64) NOT NULL,
    box_id    VARCHAR(64) NOT NULL,
    header_id VARCHAR(64) NOT NULL,
    index     INTEGER     NOT NULL,
    value     BIGINT      NOT NULL,
    PRIMARY KEY (index, token_id, box_id, header_id)
);

CREATE INDEX "assets_fork_f__box_id" ON assets_fork (box_id);
CREATE INDEX "assets_fork_f__token_id" ON assets_fork (token_id);
CREATE INDEX "assets_fork_f__header_id" ON assets_fork (header_id);


CREATE TABLE box_registers
(
    id     VARCHAR(2)  NOT NULL,
    box_id VARCHAR(64) NOT NULL,
    value  BLOB        NOT NULL,
    PRIMARY KEY (id, box_id)
);

CREATE INDEX "box_registers__id" ON box_registers (id);
CREATE INDEX "box_registers__box_id" ON box_registers (box_id);


CREATE TABLE box_registers_fork
(
    id     VARCHAR(2)  NOT NULL,
    box_id VARCHAR(64) NOT NULL,
    value  BLOB        NOT NULL,
    PRIMARY KEY (id, box_id)
);

CREATE INDEX "box_registers_fork_f__id" ON box_registers_fork (id);
CREATE INDEX "box_registers_fork_f__box_id" ON box_registers_fork (box_id);

CREATE TABLE stealth
(
    stealth_id   VARCHAR(255) NOT NULL,
    secret       DECIMAL(100) NOT NULL,
    stealth_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (stealth_id)
);

CREATE INDEX "stealth__stealth_id" ON stealth (stealth_id);

-- !Downs
