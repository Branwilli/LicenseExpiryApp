const { Pool } = require('pg');

// DATABASE_URL example: postgres://user:password@localhost:5432/license_expiry
// Most hosted Postgres providers (Render, Railway, Supabase, RDS) require SSL
// in production; PGSSL=true toggles that below.
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.PGSSL === 'true' ? { rejectUnauthorized: false } : false,
});

pool.on('error', (err) => {
  console.error('Unexpected error on idle Postgres client', err);
});

module.exports = pool;
