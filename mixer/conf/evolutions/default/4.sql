-- ADD ADDITIONAL_INFO to WITHDRAW and WITHDRAW_ARCHIVED tables

-- !Ups
ALTER TABLE WITHDRAW ADD ADDITIONAL_INFO VARCHAR(255) DEFAULT null;
ALTER TABLE WITHDRAW_ARCHIVED ADD ADDITIONAL_INFO VARCHAR(255) DEFAULT null;

-- !Downs
ALTER TABLE WITHDRAW DROP ADDITIONAL_INFO
ALTER TABLE WITHDRAW_ARCHIVED DROP ADDITIONAL_INFO


