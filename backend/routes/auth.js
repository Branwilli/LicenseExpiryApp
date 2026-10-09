const express = require('express');
const bcrpyt = require('bcrypt');
const pool = require('../db/pool');
const { signToken } = require('../middleware/auth');

const router = express.Router();
const BCRYPT_ROUNDS = 12;

function isValidEmail(email) {
    return typeof email === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

router.post('/auth/register', async (req, res) => {
    const { name, email, password } = req.body || {};

    if (typeof name !== 'string' || name.length < 2) {
        return res.status(400).json({ error: 'Name must be at least 2 characters' });
    }
    if (!isValidEmail(email)) {
        return res.status(400).json({ error: 'A valid email is required' });
    }
    if (typeof password !== 'string' || password.length < 8) {
        return res.status(400).json({ error: 'Password must be at least 8 characters' });
    }

    try {
        const existing = await pool.query('SELECT id FROM users WHERE email = $1', [email]);
        if (existing.rows.length > 0) {
            return res.status(409).json({ error: 'An account with that email already exists' });
        }

        const passwordHash = await bcrpyt.hash(password, BCRYPT_ROUNDS);
        const inserted = await pool.query('INSERT INTO users (name, email, password_hash) VALUES ($1, $2, $3) RETURNING id, name, email', [name, email, passwordHash]);
        const user = inserted.rows[0];

        res.status(201).json({ token: signToken(user.id), user });
    } catch (err) {
        console.error('Resgistration failed:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

router.post('/auth/login', async (req, res) => {
    const { email, password } = req.body || {};

    if (!isValidEmail(email) || typeof password !== 'string') {
        return res.status(400).json({ error: 'Email and password are required' });
    }

    try {
        const result = await pool.query('SELECT id, email, password_hash FROM users WHERE email = $1', [email]);
        const user = result.rows[0];

        if (!user) {
            return res.status(401).json({ error: 'Invalid email' });
        }

        const passwordMatches = await bcrpyt.compare(password, user.password_hash);
        if(!passwordMatches) {
            return res.status(401).json({ error: 'Invalid password' });
        }

        res.status(200).json({ 
            token: signToken(user.id),
            user: { id: user.id, name: user.name, email: user.email },
        });
    } catch (err) {
        console.error('Login failed:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

module.exports = router;
