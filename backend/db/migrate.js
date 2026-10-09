/**
 * Applies db/schema.sql to the database pointed at by DATABASE_URL.
 * Safe to run repeatedly — every statement uses CREATE TABLE IF NOT EXISTS
 * and CREATE INDEX IF NOT EXISTS.
 *
 * Run: npm run migrate
 */
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const pool = require('./pool');

async function migrate() {
  const schemaPath = path.join(__dirname, 'schema.sql');
  const schema = fs.readFileSync(schemaPath, 'utf8');

  console.log('Applying schema.sql...');
  await pool.query(schema);
  console.log('Migration complete.');

  await pool.end();
}

migrate().catch((err) => {
  console.error('Migration failed:', err);
  process.exit(1);
});
