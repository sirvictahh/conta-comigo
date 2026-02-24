require("dotenv").config();

const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const morgan = require("morgan");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const { v4: uuidv4, validate: uuidValidate } = require("uuid");

const { openDb } = require("./db");
const { requireAuth } = require("./auth");

const app = express();

/**
 * Config
 */
const PORT = Number(process.env.PORT || 3000);
const JWT_SECRET = process.env.JWT_SECRET;

if (!JWT_SECRET) {
  console.error("Missing JWT_SECRET in .env");
  process.exit(1);
}

/**
 * Middlewares
 */
app.use(helmet());
app.use(cors());
app.use(express.json({ limit: "256kb" })); // evita payloads enormes
app.use(morgan("dev"));

/**
 * Helpers
 */
function isValidEmail(email) {
  return typeof email === "string" && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function isValidLatLng(lat, lng) {
  return (
    typeof lat === "number" &&
    typeof lng === "number" &&
    lat >= -90 && lat <= 90 &&
    lng >= -180 && lng <= 180
  );
}

function sanitizeString(value, maxLen) {
  if (typeof value !== "string") return "";
  return value.trim().slice(0, maxLen);
}

/**
 * Async handler (evita try/catch repetido)
 */
function asyncHandler(fn) {
  return (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
}

let db;

/**
 * Health check (inclui DB)
 */
app.get("/health", asyncHandler(async (req, res) => {
  // Verificação leve da DB
  await db.get("SELECT 1 AS ok");
  res.json({ ok: true });
}));

/**
 * AUTH
 * - Register
 * - Login
 * - /me (perfil)
 */
app.post("/auth/register", asyncHandler(async (req, res) => {
  const name = sanitizeString(req.body?.name, 80);
  const email = sanitizeString(req.body?.email, 120).toLowerCase();
  const password = req.body?.password;

  if (name.length < 2) {
    return res.status(400).json({ error: "Name must have at least 2 characters." });
  }
  if (!isValidEmail(email)) {
    return res.status(400).json({ error: "Invalid email." });
  }
  if (typeof password !== "string" || password.length < 8) {
    return res.status(400).json({ error: "Password must have at least 8 characters." });
  }

  const userId = uuidv4();
  const passwordHash = await bcrypt.hash(password, 10);

  try {
    await db.run(
      "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)",
      [userId, name, email, passwordHash, Date.now()]
    );
  } catch (e) {
    if (String(e).includes("UNIQUE")) {
      return res.status(409).json({ error: "Email already exists." });
    }
    return res.status(500).json({ error: "Internal error creating user." });
  }

  return res.status(201).json({ ok: true });
}));

app.post("/auth/login", asyncHandler(async (req, res) => {
  const email = sanitizeString(req.body?.email, 120).toLowerCase();
  const password = req.body?.password;

  if (!isValidEmail(email) || typeof password !== "string") {
    return res.status(400).json({ error: "Invalid credentials." });
  }

  const user = await db.get(
    "SELECT id, password_hash FROM users WHERE email = ?",
    [email]
  );

  if (!user) return res.status(401).json({ error: "Invalid credentials." });

  const ok = await bcrypt.compare(password, user.password_hash);
  if (!ok) return res.status(401).json({ error: "Invalid credentials." });

  const token = jwt.sign({}, JWT_SECRET, {
    subject: user.id,
    expiresIn: "7d"
  });

  return res.json({ token });
}));

app.get("/me", requireAuth, asyncHandler(async (req, res) => {
  const user = await db.get(
    "SELECT id, name, email, created_at AS createdAtEpochMillis FROM users WHERE id = ?",
    [req.user.id]
  );

  if (!user) return res.status(404).json({ error: "User not found." });
  return res.json(user);
}));

/**
 * OCCURRENCES (protegido por utilizador)
 * Modelo resposta:
 * { id, title, latitude, longitude, createdAtEpochMillis }
 */
app.get("/occurrences", requireAuth, asyncHandler(async (req, res) => {
  const rows = await db.all(
    `
    SELECT
      id,
      title,
      latitude,
      longitude,
      created_at AS createdAtEpochMillis
    FROM occurrences
    WHERE user_id = ?
    ORDER BY created_at DESC
    `,
    [req.user.id]
  );

  return res.json(rows);
}));

app.post("/occurrences", requireAuth, asyncHandler(async (req, res) => {
  const id = sanitizeString(req.body?.id, 80);
  const title = sanitizeString(req.body?.title, 60);
  const latitude = req.body?.latitude;
  const longitude = req.body?.longitude;

  if (title.length < 1) {
    return res.status(400).json({ error: "Title must be between 1 and 60 characters." });
  }
  if (!isValidLatLng(latitude, longitude)) {
    return res.status(400).json({ error: "Invalid latitude/longitude." });
  }

  let finalId = uuidv4();
  if (id.length > 0) {
    if (!uuidValidate(id)) {
      return res.status(400).json({ error: "Invalid id (must be UUID)." });
    }
    finalId = id;
  }

  const createdAt = Date.now();

  try {
    await db.run(
      "INSERT INTO occurrences (id, user_id, title, latitude, longitude, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      [finalId, req.user.id, title, latitude, longitude, createdAt]
    );
  } catch (e) {
    if (String(e).includes("UNIQUE") || String(e).includes("PRIMARY")) {
      return res.status(409).json({ error: "Occurrence id already exists." });
    }
    return res.status(500).json({ error: "Internal error creating occurrence." });
  }

  return res.status(201).json({
    id: finalId,
    title,
    latitude,
    longitude,
    createdAtEpochMillis: createdAt
  });
}));

app.put("/occurrences/:id", requireAuth, asyncHandler(async (req, res) => {
  const id = req.params.id;
  const title = sanitizeString(req.body?.title, 60);

  if (!uuidValidate(id)) {
    return res.status(400).json({ error: "Invalid id (must be UUID)." });
  }
  if (title.length < 1) {
    return res.status(400).json({ error: "Title must be between 1 and 60 characters." });
  }

  const exists = await db.get(
    "SELECT id FROM occurrences WHERE id = ? AND user_id = ?",
    [id, req.user.id]
  );
  if (!exists) return res.status(404).json({ error: "Occurrence not found." });

  await db.run(
    "UPDATE occurrences SET title = ? WHERE id = ? AND user_id = ?",
    [title, id, req.user.id]
  );

  const updated = await db.get(
    `
    SELECT
      id,
      title,
      latitude,
      longitude,
      created_at AS createdAtEpochMillis
    FROM occurrences
    WHERE id = ? AND user_id = ?
    `,
    [id, req.user.id]
  );

  return res.json(updated);
}));

app.delete("/occurrences/:id", requireAuth, asyncHandler(async (req, res) => {
  const id = req.params.id;

  if (!uuidValidate(id)) {
    return res.status(400).json({ error: "Invalid id (must be UUID)." });
  }

  const exists = await db.get(
    "SELECT id FROM occurrences WHERE id = ? AND user_id = ?",
    [id, req.user.id]
  );
  if (!exists) return res.status(404).json({ error: "Occurrence not found." });

  await db.run("DELETE FROM occurrences WHERE id = ? AND user_id = ?", [id, req.user.id]);
  return res.json({ ok: true });
}));

/**
 * 404 handler
 */
app.use((req, res) => {
  res.status(404).json({ error: "Not found." });
});

/**
 * Error handler (único ponto de resposta para erros inesperados)
 */
app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({ error: "Internal server error." });
});

/**
 * Bootstrap
 */
async function start() {
  db = await openDb();

  app.listen(PORT, () => {
    console.log(`API listening on http://localhost:${PORT}`);
  });
}

start();