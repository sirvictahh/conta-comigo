const path = require("path");
const fs = require("fs");
const sqlite3 = require("sqlite3");
const { open } = require("sqlite");

async function openDb() {
  const dataDir = path.join(__dirname, "..", "data");

  if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true });
  }

  const dbPath = path.join(dataDir, "app.db");

  const db = await open({
    filename: dbPath,
    driver: sqlite3.Database
  });

  /*
   * Estrutura base da base de dados.
   */
  await db.exec(`
    PRAGMA foreign_keys = ON;

    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      email TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS occurrences (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      title TEXT NOT NULL,
      latitude REAL NOT NULL,
      longitude REAL NOT NULL,
      created_at INTEGER NOT NULL,
      FOREIGN KEY(user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS expenses (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      title TEXT NOT NULL,
      amount_cents INTEGER NOT NULL,
      category TEXT NOT NULL,

      latitude REAL,
      longitude REAL,

      created_at INTEGER NOT NULL,

      FOREIGN KEY(user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_occurrences_user_id
      ON occurrences(user_id);

    CREATE INDEX IF NOT EXISTS idx_expenses_user_id
      ON expenses(user_id);
  `);

  /*
   * Migração para bases de dados que já existiam antes
   * de adicionarmos localização às despesas.
   *
   * CREATE TABLE IF NOT EXISTS não modifica uma tabela
   * existente, portanto verificamos explicitamente as colunas.
   */
  const expenseColumns = await db.all(
    "PRAGMA table_info(expenses)"
  );

  const expenseColumnNames = expenseColumns.map(
    column => column.name
  );

  if (!expenseColumnNames.includes("latitude")) {
    await db.exec(`
      ALTER TABLE expenses
      ADD COLUMN latitude REAL
    `);

    console.log(
      "Database migration: added expenses.latitude"
    );
  }

  if (!expenseColumnNames.includes("longitude")) {
    await db.exec(`
      ALTER TABLE expenses
      ADD COLUMN longitude REAL
    `);

    console.log(
      "Database migration: added expenses.longitude"
    );
  }

  return db;
}

module.exports = {
  openDb
};