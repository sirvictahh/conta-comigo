const jwt = require("jsonwebtoken");

// Segredo simples para contexto académico (podes alterar se quiseres)
const JWT_SECRET = "conta-comigo-secret";

function requireAuth(req, res, next) {
  const header = req.headers.authorization || "";
  const [type, token] = header.split(" ");

  if (type !== "Bearer" || !token) {
    return res.status(401).json({ error: "Token em falta." });
  }

  try {
    const payload = jwt.verify(token, JWT_SECRET);
    // payload esperado: { userId, username, role }
    req.user = payload;
    return next();
  } catch (err) {
    return res.status(401).json({ error: "Token inválido." });
  }
}

function requireAdmin(req, res, next) {
  if (req.user?.role !== "admin") {
    return res.status(403).json({ error: "Sem permissões (admin)." });
  }
  return next();
}

module.exports = { JWT_SECRET, requireAuth, requireAdmin };