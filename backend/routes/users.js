const express = require('express');
const pool = require('../db/pool');

const router = express.Router();

router.get('/me', async (req, res) => {
  try {
    const result = await pool.query('SELECT id, email, created_at FROM users WHERE id = $1', [
      req.userId,
    ]);
    if (result.rows.length === 0) return res.status(404).json({ error: 'User not found' });
    res.json(result.rows[0]);
  } catch (err) {
    console.error('Failed to fetch user:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;
