#!/usr/bin/env bash
# Usage: scripts/new-token.sh <name> <projects> <scopes>
# Example: scripts/new-token.sh demo-token "SHOP,OPS" "mcp,admin"
# Prints the raw token once and the tokens.yml entry to add.

set -euo pipefail

NAME="${1:?Usage: new-token.sh <name> <projects> <scopes>}"
PROJECTS="${2:?provide comma-separated projects}"
SCOPES="${3:?provide comma-separated scopes}"

RAW=$(openssl rand -hex 32)
HASH=$(echo -n "$RAW" | sha256sum | awk "{print \$1}")

echo ""
echo "=== NEW TOKEN ==="
echo "Raw token (show once, store securely):"
echo "  $RAW"
echo ""
echo "Add this to tokens.yml:"
echo "---"
echo "- name: $NAME"
echo "  sha256: $HASH"
PROJ_YAML=$(echo "$PROJECTS" | tr "," "\n" | sed "s/^/  - /")
SCOPE_YAML=$(echo "$SCOPES" | tr "," "\n" | sed "s/^/  - /")
echo "  projects:"
echo "$PROJ_YAML"
echo "  scopes:"
echo "$SCOPE_YAML"
echo ""
echo "To use with Claude Code:"
echo "  export AIDA_TOKEN=$RAW"
echo "  claude mcp add --transport http aida http://localhost:8080/mcp --header \"Authorization: Bearer \$AIDA_TOKEN\""
