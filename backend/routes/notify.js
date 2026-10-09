const express = require('express');
const nodemailer = require('nodemailer');
const pool = require('../db/pool');
const { userOwnsLicense } = require('./licenses');

const router = express.Router();

const transporter = nodemailer.createTransport({
  host: process.env.SMTP_HOST,
  port: Number(process.env.SMTP_PORT) || 587,
  secure: process.env.SMTP_SECURE === 'true', // true for port 465, false for 587
  auth: {
    user: process.env.SMTP_USER,
    pass: process.env.SMTP_PASS,
  },
});

router.post('/licenses/:id/notify', async (req, res) => {
  if (!(await userOwnsLicense(req.params.id, req.userId))) {
    return res.status(404).json({ error: 'License not found' });
  }

  try {
    const result = await pool.query(
      `SELECT le.id, le.license_type, le.expiry_date, le.email_notified,
              v.nickname AS vehicle_nickname,
              u.email AS to_email
       FROM license_entries le
       JOIN vehicles v ON v.id = le.vehicle_id
       JOIN users u ON u.id = v.user_id
       WHERE le.id = $1`,
      [req.params.id]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'License not found' });
    }

    const license = result.rows[0];
    const expiryDate = new Date(license.expiry_date).toISOString().split('T')[0];

    const subject = `${license.license_type} expiring soon for ${license.vehicle_nickname}`;
    const text =
      `Heads up — the ${license.license_type} for ${license.vehicle_nickname} expires on ${expiryDate}.\n\n` +
      `Please renew it before that date to avoid any penalties.`;
    const html = `
      <p>Heads up — the <strong>${license.license_type}</strong> for
      <strong>${license.vehicle_nickname}</strong> expires on <strong>${expiryDate}</strong>.</p>
      <p>Please renew it before that date to avoid any penalties.</p>
    `;

    await transporter.sendMail({
      from: process.env.FROM_EMAIL || process.env.SMTP_USER,
      to: license.to_email,
      subject,
      text,
      html,
    });

    await pool.query('UPDATE license_entries SET email_notified = true WHERE id = $1', [
      license.id,
    ]);

    res.status(200).json({ status: 'sent' });
  } catch (err) {
    console.error('Failed to send expiry email:', err);
    res.status(502).json({ error: 'Failed to send email' });
  }
});


async function sendDueReminders(withinDays = 30) {
  const dueResult = await pool.query(
    `SELECT le.id FROM license_entries le
     WHERE le.email_notified = false
     AND le.expiry_date <= (CURRENT_DATE + $1::int)`,
    [withinDays]
  );

  let sent = 0;
  for (const row of dueResult.rows) {
    try {
      const licenseResult = await pool.query(
        `SELECT le.id, le.license_type, le.expiry_date,
                v.nickname AS vehicle_nickname, u.email AS to_email
         FROM license_entries le
         JOIN vehicles v ON v.id = le.vehicle_id
         JOIN users u ON u.id = v.user_id
         WHERE le.id = $1`,
        [row.id]
      );
      const license = licenseResult.rows[0];
      if (!license) continue;

      const expiryDate = new Date(license.expiry_date).toISOString().split('T')[0];
      await transporter.sendMail({
        from: process.env.FROM_EMAIL || process.env.SMTP_USER,
        to: license.to_email,
        subject: `${license.license_type} expiring soon for ${license.vehicle_nickname}`,
        text: `Heads up — the ${license.license_type} for ${license.vehicle_nickname} expires on ${expiryDate}.`,
      });
      await pool.query('UPDATE license_entries SET email_notified = true WHERE id = $1', [
        license.id,
      ]);
      sent += 1;
    } catch (err) {
      console.error(`Failed to send reminder for license ${row.id}:`, err);
    }
  }
  return sent;
}

module.exports = { router, sendDueReminders };