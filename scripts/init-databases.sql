-- Initialize multiple databases for different services
-- This script runs when PostgreSQL container starts for the first time

-- Create databases for different services
CREATE DATABASE userdb;
CREATE DATABASE inventory_db;
CREATE DATABASE notificationdb;
CREATE DATABASE paymentdb;
CREATE DATABASE orderdb;
CREATE DATABASE cartdb;

-- Create user if not exists (for compatibility)
DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles
      WHERE  rolname = 'ecommerce_user') THEN
      CREATE ROLE ecommerce_user LOGIN PASSWORD 'ecommerce_pass';
   END IF;
END
$do$;

-- Grant permissions to the ecommerce_user for all databases
GRANT ALL PRIVILEGES ON DATABASE ecommerce TO ecommerce_user;
GRANT ALL PRIVILEGES ON DATABASE userdb TO ecommerce_user;
GRANT ALL PRIVILEGES ON DATABASE inventory_db TO ecommerce_user;
GRANT ALL PRIVILEGES ON DATABASE notificationdb TO ecommerce_user;
GRANT ALL PRIVILEGES ON DATABASE paymentdb TO ecommerce_user;
GRANT ALL PRIVILEGES ON DATABASE orderdb TO ecommerce_user;
GRANT ALL PRIVILEGES ON DATABASE cartdb TO ecommerce_user;

-- Connect to each database and create schemas if needed
\c userdb;
-- User service specific schema can be added here if needed
CREATE SCHEMA IF NOT EXISTS public;
GRANT ALL ON SCHEMA public TO ecommerce_user;

\c inventory_db;
-- Inventory service specific schema can be added here if needed
CREATE SCHEMA IF NOT EXISTS public;
GRANT ALL ON SCHEMA public TO ecommerce_user;

\c notificationdb;
-- Notification service specific schema
CREATE SCHEMA IF NOT EXISTS public;
GRANT ALL ON SCHEMA public TO ecommerce_user;

\c paymentdb;
-- Payment service specific schema
CREATE SCHEMA IF NOT EXISTS public;
GRANT ALL ON SCHEMA public TO ecommerce_user;

\c orderdb;
-- Order service specific schema
CREATE SCHEMA IF NOT EXISTS public;
GRANT ALL ON SCHEMA public TO ecommerce_user;

\c cartdb;
-- Cart service specific schema (if using PostgreSQL instead of Redis)
CREATE SCHEMA IF NOT EXISTS public;
GRANT ALL ON SCHEMA public TO ecommerce_user;

\c ecommerce;
-- Shared database for common services
CREATE SCHEMA IF NOT EXISTS public;
GRANT ALL ON SCHEMA public TO ecommerce_user;

-- Note: Individual services will handle their own table creation via JPA/Hibernate
