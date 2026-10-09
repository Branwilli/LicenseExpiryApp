require('dotenv').config();
const express = require('express');
const { requireAuth } = require('./middleware/auth');
const authRouter = require('./routes/auth');
const usersRouter = require('./routes/users');
const vehiclesRouter = require('./routes/vehicles');
const licensesRouter = require('./routes/licenses');
const { router: notifyRouter } = require('./routes/notify');
const { startDailyReminderJob } = require('./cron/dailyReminders');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 3000;

app.get('/health', (_req, res) => res.json({ status: 'ok' }));

// Every route below requires the shared API key.
app.use(authRouter);
app.use(requireAuth);
app.use(usersRouter);
app.use(vehiclesRouter);
app.use(licensesRouter);
app.use(notifyRouter);

app.listen(PORT, () => {
  console.log(`License expiry backend listening on port ${PORT}`);
  startDailyReminderJob();
});
