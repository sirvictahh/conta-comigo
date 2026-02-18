const sqlite3 = require("sqlite3").verbose();
const path = require("path");
const bcrypt = require("bcryptjs");

const dbPath = path.join(__dirname, "database.sqlite");
const db = new sqlite3.Database(dbPath);

function run(sql, params = []) {
  return new Promise((resolve, reject) => {
    db.run(sql, params, function (err) {
      if (err) return reject(err);
      resolve(this);
    });
  });
}

function get(sql, params = []) {
  return new Promise((resolve, reject) => {
    db.get(sql, params, (err, row) => {
      if (err) return reject(err);
      resolve(row);
    });
  });
}

function all(sql, params = []) {
  return new Promise((resolve, reject) => {
    db.all(sql, params, (err, rows) => {
      if (err) return reject(err);
      resolve(rows);
    });
  });
}

async function init() {
  // Tabela de utilizadores
  await run(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT UNIQUE NOT NULL,
      passwordHash TEXT NOT NULL,
      role TEXT NOT NULL CHECK(role IN ('admin','user'))
    );
  `);

  // Tabela de ocorrências
  await run(`
    CREATE TABLE IF NOT EXISTS occurrences (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      latitude REAL NOT NULL,
      longitude REAL NOT NULL,
      createdAtEpochMillis INTEGER NOT NULL,
      ownerId INTEGER NOT NULL,
      FOREIGN KEY(ownerId) REFERENCES users(id)
    );
  `);

  // Seed mínimo (para testes/avaliação)
  await seedUserIfMissing("admin", "admin123", "admin");
  await seedUserIfMissing("joao", "joao123", "user");
}

async function seedUserIfMissing(username, password, role) {
  const existing = await get("SELECT id FROM users WHERE username = ?", [username]);
  if (existing) return;

  const passwordHash = bcrypt.hashSync(password, 10);
  await run(
    "INSERT INTO users(username, passwordHash, role) VALUES (?,?,?)",
    [username, passwordHash, role]
  );
}

module.exports = { db, run, get, all, init };