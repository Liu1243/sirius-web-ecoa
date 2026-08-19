#!/bin/bash
set -e

PGDATA="${PGDATA:-/var/lib/postgresql/data}"

# Initialize PostgreSQL data directory if it's empty
if [ ! -s "$PGDATA/PG_VERSION" ]; then
    echo "=== Initializing PostgreSQL database ==="
    
    # Ensure data directory exists with correct ownership
    mkdir -p "$PGDATA"
    chown -R postgres:postgres "$PGDATA"
    chmod 700 "$PGDATA"
    
    # Run initdb as postgres user
    su - postgres -c "/usr/lib/postgresql/15/bin/initdb -D $PGDATA --encoding=UTF8 --locale=C"
    
    # Configure pg_hba.conf for local trust authentication
    echo "local   all   all   trust" > "$PGDATA/pg_hba.conf"
    echo "host    all   all   127.0.0.1/32   trust" >> "$PGDATA/pg_hba.conf"
    echo "host    all   all   ::1/128        trust" >> "$PGDATA/pg_hba.conf"
    
    # Configure postgresql.conf to listen on localhost
    echo "listen_addresses = 'localhost'" >> "$PGDATA/postgresql.conf"
    
    # Start temporary postgres to create user and database
    su - postgres -c "/usr/lib/postgresql/15/bin/pg_ctl -D $PGDATA -w start"
    
    # Create user and database
    su - postgres -c "psql -c \"CREATE USER $POSTGRES_USER WITH PASSWORD '$POSTGRES_PASSWORD' CREATEDB;\""
    su - postgres -c "createdb -O $POSTGRES_USER $POSTGRES_DB"
    
    # Stop temporary postgres
    su - postgres -c "/usr/lib/postgresql/15/bin/pg_ctl -D $PGDATA -m fast -w stop"
    
    echo "=== PostgreSQL initialized ==="
fi

# Ensure /var/run/postgresql exists
mkdir -p /var/run/postgresql
chown -R postgres:postgres /var/run/postgresql

echo "=== Starting Supervisor (PostgreSQL + Spring Boot) ==="
exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf
