CREATE TABLE company_profile_cache (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_name VARCHAR(255) NOT NULL,
  normalized_company_name VARCHAR(255),
  credit_code VARCHAR(64),
  legal_person VARCHAR(100),
  registration_status VARCHAR(50),
  industry_name VARCHAR(1000),
  industry_code VARCHAR(50),
  entity_type VARCHAR(255),
  employment_industry VARCHAR(100),
  employment_unit_nature VARCHAR(100),
  classification_source VARCHAR(50),
  classification_confidence VARCHAR(20),
  registration_authority VARCHAR(255),
  registration_authority_code VARCHAR(12),
  province VARCHAR(50),
  city VARCHAR(50),
  district VARCHAR(50),
  province_code VARCHAR(12),
  city_code VARCHAR(12),
  district_code VARCHAR(12),
  area_code VARCHAR(12),
  registered_address_area_code VARCHAR(12),
  area_code_source VARCHAR(20),
  registered_address VARCHAR(500),
  source VARCHAR(100),
  source_updated_at DATE,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_company_profile_cache_credit_code (credit_code),
  INDEX idx_company_profile_cache_name (company_name),
  INDEX idx_company_profile_cache_area_code (area_code)
);

CREATE TABLE company_profile_alias (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  alias_name VARCHAR(255) NOT NULL,
  normalized_alias VARCHAR(255) NOT NULL,
  credit_code VARCHAR(64),
  company_name VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_company_profile_alias_normalized (normalized_alias)
);

CREATE TABLE employment_job (
  id VARCHAR(40) PRIMARY KEY,
  original_filename VARCHAR(255) NOT NULL,
  input_path VARCHAR(1000) NOT NULL,
  status VARCHAR(30) NOT NULL,
  total_records INT NOT NULL,
  unique_companies INT NOT NULL,
  validation_errors_json CLOB,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  expires_at DATETIME NOT NULL
);

CREATE TABLE employment_company_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id VARCHAR(40) NOT NULL,
  input_company_name VARCHAR(255) NOT NULL,
  normalized_name VARCHAR(255) NOT NULL,
  official_company_name VARCHAR(255),
  credit_code VARCHAR(64),
  raw_industry_name VARCHAR(100),
  raw_entity_type VARCHAR(100),
  registered_address VARCHAR(500),
  employment_industry VARCHAR(100),
  employment_unit_nature VARCHAR(100),
  registered_address_area_code VARCHAR(12),
  registered_address_area_name VARCHAR(100),
  classification_source VARCHAR(50),
  classification_confidence VARCHAR(20),
  source VARCHAR(100),
  status VARCHAR(30) NOT NULL,
  affected_rows INT NOT NULL,
  cache_hit BOOLEAN NOT NULL,
  replace_official_name BOOLEAN NOT NULL,
  conflict_fields_json CLOB,
  conflict_decisions_json CLOB,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_employment_company_job_name (job_id, normalized_name)
);

CREATE TABLE employment_job_row (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id VARCHAR(40) NOT NULL,
  company_task_id BIGINT NOT NULL,
  row_number INT NOT NULL,
  created_at DATETIME NOT NULL
);
