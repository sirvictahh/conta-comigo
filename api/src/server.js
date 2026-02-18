const express = require("express");
const cors = require("cors");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const crypto = require("crypto");

const { init, get, all, run } = require("./db");
const { JWT_SECRET, requireAuth, requireAdmin } = require("./auth");

const app = express();

app.use(cors());
app.use(express.json());

/**
 * Gera um ID único para ocorrências.
 * (randomUUID existe em Node >= 14.17, mas deixamos fallback por segurança)
 */
function generateId() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return crypto.randomBytes(16).toString("hex");
}

/**
 * Rota de saúde (para testar rapidamente no browser)
 */
app.get("/health", (req, res) => {
  res.json({ ok: true });
});

/**
 * LOGIN
 * POST /auth/login
 * body: { username, password }
 * resposta: { token, user: { id, username, role } }
 */
app.post("/auth/login", async (req, res) => {
  try {
    const username = String(req.body.username || "").trim();
    const password = String(req.body.password || "");

    if (!username || !password) {
      return res.status(400).json({ error: "Username/password em falta." });
    }

    const user = await get("SELECT * FROM users WHERE username = ?", [username]);
    if (!user) return res.status(401).json({ error: "Credenciais inválidas." });

    const ok = bcrypt.compareSync(password, user.passwordHash);
    if (!ok) return res.status(401).json({ error: "Credenciais inválidas." });

    const token = jwt.sign(
      { userId: user.id, username: user.username, role: user.role },
      JWT_SECRET,
      { expiresIn: "7d" }
    );

    return res.json({
      token,
      user: { id: user.id, username: user.username, role: user.role },
    });
  } catch (err) {
    return res.status(500).json({ error: "Erro interno no login." });
  }
});

/**
 * LISTAR OCORRÊNCIAS
 * GET /occurrences
 * - admin: vê todas
 * - user: vê apenas as próprias
 */
app.get("/occurrences", requireAuth, async (req, res) => {
  try {
    if (req.user.role === "admin") {
      const rows = await all(
        `
        SELECT o.*, u.username AS ownerUsername
        FROM occurrences o
        JOIN users u ON u.id = o.ownerId
        ORDER BY o.createdAtEpochMillis DESC
      `
      );
      return res.json(rows);
    }

    const rows = await all(
      `
      SELECT o.*, u.username AS ownerUsername
      FROM occurrences o
      JOIN users u ON u.id = o.ownerId
      WHERE o.ownerId = ?
      ORDER BY o.createdAtEpochMillis DESC
    `,
      [req.user.userId]
    );

    return res.json(rows);
  } catch (err) {
    return res.status(500).json({ error: "Erro ao obter ocorrências." });
  }
});

/**
 * CRIAR OCORRÊNCIA
 * POST /occurrences
 * body: { id? , title, latitude, longitude }
 * - guarda ownerId a partir do token
 */
app.post("/occurrences", requireAuth, async (req, res) => {
  try {
    const id = String(req.body.id || generateId());
    const title = String(req.body.title || "").trim();
    const latitude = Number(req.body.latitude);
    const longitude = Number(req.body.longitude);

    // Validações mínimas (úteis para avaliação)
    if (!title || title.length < 2 || title.length > 80) {
      return res.status(400).json({ error: "Título inválido (2..80 chars)." });
    }
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      return res.status(400).json({ error: "Latitude/Longitude inválidas." });
    }

    const createdAtEpochMillis = Date.now();

    await run(
      `
      INSERT INTO occurrences(id, title, latitude, longitude, createdAtEpochMillis, ownerId)
      VALUES (?,?,?,?,?,?)
    `,
      [id, title, latitude, longitude, createdAtEpochMillis, req.user.userId]
    );

    const created = await get(
      `
      SELECT o.*, u.username AS ownerUsername
      FROM occurrences o
      JOIN users u ON u.id = o.ownerId
      WHERE o.id = ?
    `,
      [id]
    );

    return res.status(201).json(created);
  } catch (err) {
    // Erro comum: ID duplicado
    return res.status(500).json({ error: "Erro ao criar ocorrência." });
  }
});

/**
 * EDITAR TÍTULO (RBAC)
 * PUT /occurrences/:id
 * - admin pode editar qualquer uma
 * - user só pode editar as próprias
 */
app.put("/occurrences/:id", requireAuth, async (req, res) => {
  try {
    const id = String(req.params.id || "");
    const title = String(req.body.title || "").trim();

    if (!id) return res.status(400).json({ error: "ID em falta." });
    if (!title || title.length < 2 || title.length > 80) {
      return res.status(400).json({ error: "Título inválido (2..80 chars)." });
    }

    const occ = await get("SELECT * FROM occurrences WHERE id = ?", [id]);
    if (!occ) return res.status(404).json({ error: "Ocorrência não encontrada." });

    const canEdit = req.user.role === "admin" || occ.ownerId === req.user.userId;
    if (!canEdit) return res.status(403).json({ error: "Sem permissões." });

    await run("UPDATE occurrences SET title = ? WHERE id = ?", [title, id]);

    const updated = await get(
      `
      SELECT o.*, u.username AS ownerUsername
      FROM occurrences o
      JOIN users u ON u.id = o.ownerId
      WHERE o.id = ?
    `,
      [id]
    );

    return res.json(updated);
  } catch (err) {
    return res.status(500).json({ error: "Erro ao editar ocorrência." });
  }
});

/**
 * APAGAR (RBAC)
 * DELETE /occurrences/:id
 * - admin pode apagar qualquer uma
 * - user só pode apagar as próprias
 */
app.delete("/occurrences/:id", requireAuth, async (req, res) => {
  try {
    const id = String(req.params.id || "");
    if (!id) return res.status(400).json({ error: "ID em falta." });

    const occ = await get("SELECT * FROM occurrences WHERE id = ?", [id]);
    if (!occ) return res.status(404).json({ error: "Ocorrência não encontrada." });

    const canDelete = req.user.role === "admin" || occ.ownerId === req.user.userId;
    if (!canDelete) return res.status(403).json({ error: "Sem permissões." });

    await run("DELETE FROM occurrences WHERE id = ?", [id]);

    return res.json({ ok: true });
  } catch (err) {
    return res.status(500).json({ error: "Erro ao apagar ocorrência." });
  }
});

/**
 * APAGAR TUDO (só admin)
 * DELETE /occurrences
 * Útil para testes e para demonstrar controlo por role.
 */
app.delete("/occurrences", requireAuth, requireAdmin, async (req, res) => {
  try {
    const result = await run("DELETE FROM occurrences");
    return res.json({ ok: true, deleted: result.changes || 0 });
  } catch (err) {
    return res.status(500).json({ error: "Erro ao limpar ocorrências." });
  }
});

const PORT = 8080;

/**
 * Inicializa a BD e arranca o servidor
 */
init()
  .then(() => {
    app.listen(PORT, "0.0.0.0", () => {
      console.log(`API a correr em http://localhost:${PORT}`);
    });
  })
  .catch((err) => {
    console.error("Erro a iniciar DB/API:", err);
    process.exit(1);
  });