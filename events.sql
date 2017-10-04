--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.5
-- Dumped by pg_dump version 9.5.5

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


SET search_path = public, pg_catalog;

--
-- Name: event_type; Type: TYPE; Schema: public; Owner: de
--

CREATE TYPE event_type AS ENUM (
    'CREATE',
    'DELETE',
    'READ',
    'REPLICATE',
    'REPLICATION_FAILED',
    'SYNCHRONIZATION_FAILED',
    'UPDATE'
);


ALTER TYPE event_type OWNER TO de;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: event_log; Type: TABLE; Schema: public; Owner: de
--

CREATE TABLE event_log (
    id uuid DEFAULT uuid_generate_v1() NOT NULL,
    permanent_id character varying(50),
    irods_path text NOT NULL,
    ip_address character varying(15),
    user_agent text,
    subject text,
    event event_type NOT NULL,
    date_logged timestamp without time zone DEFAULT now() NOT NULL,
    node_identifier text NOT NULL
);


ALTER TABLE event_log OWNER TO de;

--
-- Name: hibernate_sequence; Type: SEQUENCE; Schema: public; Owner: de
--

CREATE SEQUENCE hibernate_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE hibernate_sequence OWNER TO de;

--
-- Name: event_log_pkey; Type: CONSTRAINT; Schema: public; Owner: de
--

ALTER TABLE ONLY event_log
    ADD CONSTRAINT event_log_pkey PRIMARY KEY (id);


--
-- Name: event_log_date_logged_index; Type: INDEX; Schema: public; Owner: de
--

CREATE INDEX event_log_date_logged_index ON event_log USING btree (date_logged);


--
-- Name: event_log_event_index; Type: INDEX; Schema: public; Owner: de
--

CREATE INDEX event_log_event_index ON event_log USING btree (event);


--
-- Name: event_log_permanent_id_index; Type: INDEX; Schema: public; Owner: de
--

CREATE INDEX event_log_permanent_id_index ON event_log USING btree (permanent_id);


--
-- PostgreSQL database dump complete
--

