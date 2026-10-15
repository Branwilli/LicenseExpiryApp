const express = require('express');
const pool = require('../db/pool');

const router = express.Router();

async function userOwnsVehicle(vehicleId, userId) {
  const result = await pool.query('SELECT id FROM vehicles WHERE id = $ 1 AND user_id = $2', [vehicleId, userId]);
  return result.rows.length > 0;
}

router.get('/vehicles/:vehicleId/fitness', async (req, res) => {
  if (!(await userOwnsVehicle(req.params.vehicleId, req.userId))) {
    return res.status(404).json({ error: 'Vehicle not found' });
  }

    try {
        const result = await pool.query(
        'SELECT * FROM fitness_entries WHERE vehicle_id = $1 ORDER BY expiry_date ASC',
        [req.params.vehicleId]
        );
        res.json(result.rows);
  } catch (err) {
    console.error('Failed to list fitness entries:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

router.post('/vehicles/:vehicleId/fitness', async (req, res) => {
  if (!(await userOwnsVehicle(req.params.vehicleId, req.userId))) {
    return res.status(404).json({ error: 'Vehicle not found' });
  }

    const { expiryDate, reminderDaysBefore } = req.body || {};
    if (!expiryDate) {
    return res.status(400).json({ error: 'expiryDate is required' });
  }

    try {
        const result = await pool.query(
        `INSERT INTO fitness_entries (vehicle_id, expiry_date, reminder_days_before)
        VALUES ($1, $2, $3) RETURNING *`,
        [req.params.vehicleId, expiryDate, reminderDaysBefore ?? 7]
        );
        res.status(201).json(result.rows[0]);
    } catch (err) {
        console.error('Failed to add fitness entry:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

router.put('/fitness/:id', async(req, res) => {
    const { expiryDate, reminderDaysBefore } = req.body || {};
    if (!expiryDate) {
        return res.status(400).json({ error: 'expiryDate is required' });
    }

    try {
        const result = await pool.query(
            `UPDATE fitness_entries SET expiry_date = $1, reminder_days_before = $2 WHERE id = $3 RETURNING *`,
            [expiryDate, reminderDaysBefore ?? 7, req.params.id]
        );
        res.json(result.rows[0]);
    } catch (err) {
        console.error('Failed to update fitness entry:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

router.delete('/fitness/:id', async (req, res) => {
    try {
        const result = await pool.query('DELETE FROM fitness_entries WHERE id = $1 RETURNING id', [
            req.params.id,
        ]);
        if (result.rows.length === 0) return res.status(404).json({ error: 'Fitness entry not found' });
        res.status(204).send();
    } catch (err) {
        console.error('Failed to delete fitness entry:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

module.exports = router;