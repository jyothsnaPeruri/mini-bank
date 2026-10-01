# Interview notes — Mini Bank MCP server

Read aloud before interviews. Each answer is short on purpose; the detail is there if they dig.

## The one-liner

"I turned my Spring Boot banking app into an MCP server, so an AI assistant can check balances and send
money for a signed-in customer — and I designed it so a model can never move money in one step."

## Q: What is MCP?

A standard protocol for connecting AI assistants to tools and data. Like a REST API, but a standard
format every MCP-capable assistant already understands — so one server works with Claude, Cursor, VS
Code and others without custom integration. Mine is at `/mcp` on the same Spring Boot app as the REST API.

## Q: What can the assistant do?

Six tools: list accounts, get an account, recent transactions, look up a payee, prepare a transfer,
confirm a transfer. Four read, one previews, one moves money.

## Q: How do you stop an AI from sending money it shouldn't? (the main question)

Five layers:

1. **Two steps, always.** `prepare_transfer` runs every check the transfer will run — ownership, the
   $10,000 limit, balance, frozen accounts, same-account — and returns a preview plus a confirmation
   code. Nothing moves. Only `confirm_transfer` with that code sends money. That creates a natural
   point for the assistant to show the customer exactly what will happen and wait for a yes. A model
   that misreads "check if I can afford $500 to Sam" can at worst produce a preview.
2. **Identity from the token, never from arguments.** No tool takes a user id. Spring Security checks
   the JWT before the request reaches MCP; a transport *context extractor* copies the verified user id
   into the MCP request context; every tool reads it from there. A client can't ask to act as someone else.
3. **Codes are useless to anyone else.** Bound to the customer, five-minute expiry, max five open.
   Tested: Bob confirming Alice's code gets "invalid or has expired" and nothing moves.
4. **Confirming twice pays once.** The code is reused as the transfer's idempotency key, so a model
   that retries, or a customer who says "yes" twice, replays the original transfer.
5. **Same rules as the app.** Tools call the existing `TransferService` — row locks, ledger, limits —
   not a copy. And the tools are annotated `readOnlyHint` / `destructiveHint`, which clients use to put
   their own approval prompt in front of the money-moving one.

## Q: How does a tool know who the user is? (the technical question)

Stateless Streamable HTTP, so every request carries its own bearer token. The endpoint sits behind the
existing security filter chain. I replaced Spring AI's auto-configured transport with one that adds a
context extractor: it runs on the servlet thread after authentication, reads the `JwtAuthenticationToken`
from the request principal, and puts the user id into the `McpTransportContext`. Tools declare that
context as a parameter. I chose this over reading `SecurityContextHolder` inside the tool because the
MCP SDK may run tool handlers off the request thread, where a thread-local security context isn't there.

## Q: What went wrong while building it?

- **Spring Boot 4 is very new.** First job was confirming which MCP library supported it: Spring AI
  2.0.1 (built on the MCP Java SDK 2.0, Jackson 3). Added it on a branch and ran the existing 19 tests
  before writing a line of tool code — all green.
- **A client sent the account number as a number.** The MCP Inspector parsed `1685260138` as an integer
  and the server rejected it against the schema. I kept the schema as *string*: account numbers are
  identifiers, and real Australian BSBs start with zero — a number would silently drop it.
- **A 401 isn't proof of deployment.** After deploying, `/mcp` returned 401 — but the security config
  returns 401 for *any* path without a token, so the old version would too. I only called it live once
  a signed-in `tools/list` returned the six tools.

## Q: What's missing for production?

The MCP authorization spec's OAuth 2.1 flow — protected-resource metadata and dynamic client
registration — so an assistant obtains a token itself instead of being handed one. Pending transfers
live in memory, fine for one instance; several instances would need them in the database or Redis.
And rate limiting per customer on `confirm_transfer`, on top of the existing per-transfer limit.

## Q: How did you test it?

Nine new tests on top of the existing 19 (28 total, CI green): six integration tests sending real
JSON-RPC to `/mcp` against PostgreSQL — sign-in required, customers isolated, prepare moves nothing,
confirm moves money once, a stolen code fails, prepare refuses what the transfer would refuse — and
three unit tests on code expiry, normalisation and the per-customer cap. Then end to end with the
official MCP Inspector: prepare left the balance at $2,040.45, confirm took it to $2,015.45, a second
confirm left it there.
