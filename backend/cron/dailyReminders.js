const cron = require('node-cron');
const { sendDueReminders } = require('../routes/notify');

/**
 * Runs once a day at 08:00 server time and emails everyone whose license is
 * due within the next 30 days and hasn't been notified yet. This is the
 * server-side equivalent of the Android app's ExpiryCheckWorker — either can
 * be used, or both, since email_notified is checked before sending either way.
 */
function startDailyReminderJob() {
  cron.schedule('0 8 * * *', async () => {
    console.log('Running daily expiry reminder check...');
    try {
      const sent = await sendDueReminders(30);
      console.log(`Daily reminder check complete. Sent ${sent} email(s).`);
    } catch (err) {
      console.error('Daily reminder check failed:', err);
    }
  });
  console.log('Daily reminder cron job scheduled for 08:00 server time.');
}

module.exports = { startDailyReminderJob };
