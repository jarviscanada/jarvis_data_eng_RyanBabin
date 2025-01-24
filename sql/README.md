# Introduction

# SQL Quries

###### Table Setup (DDL)
CREATE TABLE cd.members
    (
        memid integer NOT NULL,
        surname character varying(200) NOT NULL,
        firstname character varying(200) NOT NULL,
        address character varying(300) NOT NULL,
        zipcode integer NOT NULL,
        telephone character varying(20) NOT NULL,
        recommendedby integer,
        joindate timestamp NOT NULL,
        CONSTRAINT members_pk PRIMARY KEY (memid),
        CONSTRAINT fk_members_recommendedby FOREIGN KEY (recommendedby)
        REFERENCES cd.members(memid) ON DELETE SET NULL
);

###### Question 1: Show all members 



###### Questions 2: Lorem ipsum...



