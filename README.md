# issue-djinn

**A local-first issue tracker with a web UI for you and an MCP server for your AI agents.**

issue-djinn runs as a single Java process on your machine. Everything — issues,
labels, dependencies, comments — lives in one SQLite file under `~/.issue-djinn/`.
You get a SvelteKit app at `http://localhost:5444`; your agents get a full
[Model Context Protocol](https://modelcontextprotocol.io) server at `/mcp`, with
16 tools to read, claim, and finish work on their own.

No cloud, no accounts, no YAML. One `run.sh`, one Java 21 runtime — and the
djinn is up.

## Features

- **One process, one file.** A Quarkus server with the SvelteKit UI baked in,
  storing everything in a single SQLite database (`~/.issue-djinn/issue-djinn.db`).
  First run creates and migrates it automatically — the tracker starts empty,
  and you (or the first agent) creates issue #1.
- **A UI for humans.** Create and browse issues — parents, children, assignees,
  labels, comments — with changes arriving live over SSE instead of requiring
  a refresh.
- **An MCP server for agents.** The whole tracker exposed as 16 MCP tools over
  streamable HTTP, plus an `issue:///{id}` resource per issue.
- **The frontier.** `list_frontier` returns the issues worth doing *right now*:
  open, unclaimed, and blocked by nothing — oldest first. Pick the top row,
  claim it, go.
- **First-come claiming.** `claim_issue` stamps an assignee name on the issue;
  a different agent trying to grab it gets a loud error, not a mystery conflict.
- **Dependencies that can't lie.** Declare prerequisites with `add_dependency`;
  the server refuses to create cycles.
- **A paper trail by design.** Closing or reopening an issue requires a comment
  with an author. History stays honest.

## Quickstart

The release zip has everything except Java.

1. Install **Java 21 or later**.
2. Download the latest zip from
   [Releases](https://github.com/panilov7/issue-djinn/releases) and unzip it.
3. Start the app, from inside the folder the zip unpacked:

   ```bash
   ./run.sh      # Linux / macOS
   run.bat       # Windows
   ```

4. Open <http://localhost:5444>.

What's listening on that port:

| Path     | What                                     |
| -------- | ---------------------------------------- |
| `/`      | The SvelteKit UI                         |
| `/api/…` | REST API (issues, labels, live events)   |
| `/mcp`   | MCP server (streamable HTTP)             |

## Configuration

Three environment variables, all optional:

| Variable               | Default          | What it does                                                                  |
| ---------------------- | ---------------- | ----------------------------------------------------------------------------- |
| `ISSUE_DJINN_DATA_DIR` | `~/.issue-djinn` | Where the data directory (and the SQLite DB inside it) lives.                  |
| `ISSUE_DJINN_HOST`     | `127.0.0.1`      | Network interface to bind. Loopback-only by default — the tracker is *yours*. |
| `ISSUE_DJINN_PORT`     | `5444`           | HTTP port for the UI, REST API, and MCP server.                               |

The standard `quarkus.http.host` and `quarkus.http.port` properties work too
(`java -Dquarkus.http.port=9090 -jar …`).

The loopback default is deliberate: nothing outside your machine can reach the
UI, the API, or the MCP endpoint. Widen it only if you know why.

## MCP setup

Add the server to any MCP-capable client:

```json
{
  "mcpServers": {
    "issue-djinn": {
      "type": "http",
      "url": "http://localhost:5444/mcp"
    }
  }
}
```

(The repo ships this exact block in `.mcp.json`.)

A typical agent's day at the lamp:

1. `list_frontier` — see the open, unclaimed, unblocked issues.
2. `claim_issue` — claim one by name.
3. `comment` as you work, then `close_issue` when done — a closing comment is required.

The full toolset: `list_issues`, `get_issue`, `create_issue`, `update_issue`,
`close_issue`, `reopen_issue`, `unassign_issue`, `unparent_issue`,
`add_dependency`, `remove_dependency`, `list_dependencies`, `list_dependents`,
`list_children`, `list_frontier`, `claim_issue`, `comment`.

## Build from source

Prerequisites: Java 21+, Maven 3.8+, and `pnpm` for the UI build.

```bash
git clone https://github.com/panilov7/issue-djinn.git
cd issue-djinn
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

Quarkus Quinoa builds the SvelteKit UI during `mvn package` (`pnpm install` +
`pnpm build`) and embeds it as static resources — the jar that comes out is the
whole app.

For development with live reload (Quarkus + Vite HMR together):

```bash
mvn quarkus:dev
```

To reproduce the release zip exactly as it ships:

```bash
bash scripts/build-release.sh
```

The script runs the full `mvn verify` — tests included — then stages and zips
the app; the zip step needs `python3`.

## License

Apache License 2.0 — see [LICENSE](LICENSE).