# Conta Comigo (DAM)

## Descrição
Aplicação Android nativa (Kotlin) para registo e partilha de despesas.
Inclui localização (GPS) e mapa para associar despesas a um local e visualizá-las geograficamente.

A aplicação comunica com uma **API REST** (sem Firebase), guarda dados localmente quando necessário,
e aplica autenticação e controlo de acessos por utilizador/perfil.

## Funcionalidades principais (planeadas)
- Autenticação (registo/login) e sessão
- Despesas com localização (GPS) e visualização em mapa (marcadores)
- CRUD de despesas (criar/editar/remover) com validações e confirmação ao apagar
- Partilha/controlo de acesso: recursos diferentes consoante o utilizador (e.g. dono vs colaborador/admin)
- Armazenamento local (cache e preferências)

## Requisitos de avaliação (checklist)
- [ ] Interface em Português (PT-PT)
- [ ] Uso de hardware com sentido (GPS/Localização)
- [ ] Interação com API REST (sem Firebase ou equivalente)
- [ ] Autenticação
- [ ] Controlo de acesso a recursos (perfis/roles e regras por utilizador)
- [ ] Validações na inserção e edição
- [ ] Confirmação/validação na remoção
- [ ] Mensagens de erro adequadas
- [ ] Armazenamento local (dados necessários)
- [ ] Ecrã "Informações" acessível a partir do ecrã inicial:
      curso + UC + ano letivo, autor com foto, bibliotecas e fontes
- [ ] Publicação na Google Play
- [ ] GitHub com evolução clara (commits frequentes e justificáveis)

## Prova de conformidade (onde será demonstrado)
| Requisito | Evidência no projeto |
|---|---|
| UI em PT-PT | `strings.xml` e screenshots (docs) |
| GPS/Mapas | Ecrã "Mapa" + permissões de localização |
| API REST | Módulo de rede (Retrofit/OkHttp) + endpoints do servidor |
| Autenticação | Ecrãs Login/Registo + token/interceptor |
| Controlo de acesso | Regras no servidor + UI (ações limitadas por utilizador/role) |
| Validações | Formulários + validações no servidor |
| Remoção com confirmação | Diálogo de confirmação antes de apagar |
| Storage local | Room/DataStore para cache/sessão/preferências |
| Ecrã Informações | Ecrã no Home com dados obrigatórios |
| Código de terceiros | `docs/TERCEIROS.md` + referências no ecrã Informações/código |

## Estrutura do repositório (planeada)
- `/app` - Aplicação Android
- `/server` - API REST
- `/docs` - Documentação (screenshots, diagramas, etc.)

## Bibliotecas e código de terceiros
As bibliotecas/frameworks e qualquer código de terceiros utilizado serão identificados no ecrã
"Informações" (a partir do ecrã inicial) e, quando aplicável, também como comentários junto do código.