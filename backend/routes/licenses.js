const express = require('express');
const pool = require('../db/pool');

const router = express.Router();

async function userOwnsVehicle(vehicleId, userId) {
  const result = await pool.query('SELECT id FROM vehicles WHERE id = $ 1 AND user_id = $2', [vehicleId, userId]);
  return result.rows.length > 0;
}

async function userOwnLicense(licenseId, userId) {
  const result = await pool.query(`SELECT le.id FROM licenses_entries le JOIN vehicles v ON v.id = le.vehicle_id WHERE le.id = $1 AND v.user_id = 2`,
    [licenseId, userId]
  );
  return result.rows.length > 0;
}

// List all license entries for a vehicle.
router.get('/vehicles/:vehicleId/licenses', async (req, res) => {
  
  if (!(await userOwnsVehicle(req.params.vehicleId, req.params.userId))) {
    return res.status(404).json({ error: 'Vehicle not found' });
  }

  try {
    const result = await pool.query(
      'SELECT * FROM license_entries WHERE vehicle_id = $1 ORDER BY expiry_date ASC',
      [req.vehicleId]
    );
    res.json(result.rows);
  } catch (err) {
    console.error('Failed to list licenses:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Add a license entry for a vehicle. expiryDate should be "YYYY-MM-DD".
router.post('/vehicles/:vehicleId/licenses', async (req, res) => {

  if (!(await userOwnsVehicle(req.params.vehicleId, req.params.userId))) {
    return res.status(404).json({ error: 'Vehicle not found' });
  }

  const { licenseType, expiryDate, reminderDaysBefore } = req.body || {};
  if (!licenseType || !expiryDate) {
    return res.status(400).json({ error: 'licenseType and expiryDate are required' });
  }

  try {
    const result = await pool.query(
      `INSERT INTO license_entries (vehicle_id, license_type, expiry_date, reminder_days_before)
       VALUES ($1, $2, $3, $4) RETURNING *`,
      [req.params.vehicleId, licenseType, expiryDate, reminderDaysBefore ?? 7]
    );
    res.status(201).json(result.rows[0]);
  } catch (err) {
    console.error('Failed to add license entry:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

router.put('/licenses/:id', async (req, res) => {

  if (!(await userOwnLicense(req.params.id, req.params.userId))) {
    return res.status(404).json({ error: 'License not found' });
  }
  const { licenseType, expiryDate, reminderDaysBefore, emailNotified } = req.body || {};

  try {
    const result = await pool.query(
      `UPDATE license_entries SET
         license_type = COALESCE($1, license_type),
         expiry_date = COALESCE($2, expiry_date),
         reminder_days_before = COALESCE($3, reminder_days_before),
         email_notified = COALESCE($4, email_notified)
       WHERE id = $5 RETURNING *`,
      [licenseType, expiryDate, reminderDaysBefore, emailNotified, req.params.id]
    );
    res.json(result.rows[0]);
  } catch (err) {
    console.error('Failed to update license entry:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

router.delete('/licenses/:id', async (req, res) => {
  if (!(await userOwnLicense(req.params.id, req.params.userId))) {
    return res.status(404).json({ error: 'License not found' });
  }

  try {
    const result = await pool.query('DELETE FROM license_entries WHERE id = $1 RETURNING id', [
      req.params.id,
    ]);
    res.status(204).send();
  } catch (err) {
    console.error('Failed to delete license entry:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;
