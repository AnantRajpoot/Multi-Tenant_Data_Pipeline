DROP TABLE IF EXISTS processed_customers;
CREATE TABLE processed_customers (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255),
    name_lower VARCHAR(255),
    name_upper VARCHAR(255),
    age INT,
    age_plus_5 INT,
    tenant_id VARCHAR(255)
);