const express = require('express');
const pool = require('../db/pool');

const router = express.Router();

// List all vehicles for a user.
router.get('/vehicles', async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT * FROM vehicles WHERE user_id = $1 ORDER BY nickname ASC',
      [req.userId]
    );
    res.json(result.rows);
  } catch (err) {
    console.error('Failed to list vehicles:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Add a vehicle for a user.
router.post('/vehicles', async (req, res) => {
  const { nickname, plateNumber, ownerNotes } = req.body || {};
  if (!nickname || !plateNumber) {
    return res.status(400).json({ error: 'nickname and plateNumber are required' });
  }

  try {
    const result = await pool.query(
      `INSERT INTO vehicles (user_id, nickname, plate_number, owner_notes)
       VALUES ($1, $2, $3, $4) RETURNING *`,
      [req.userId, nickname, plateNumber, ownerNotes || null]
    );
    res.status(201).json(result.rows[0]);
  } catch (err) {
    console.error('Failed to add vehicle:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

router.delete('/vehicles/:id', async (req, res) => {
  try {
    const result = await pool.query('DELETE FROM vehicles WHERE id = $1 AND user_id = $2 RETURNING id', [
      [req.params.id, req.userId],
    ]);
    if (result.rows.length === 0) return res.status(404).json({ error: 'Vehicle not found' });
    res.status(204).send();
  } catch (err) {
    console.error('Failed to delete vehicle:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;
