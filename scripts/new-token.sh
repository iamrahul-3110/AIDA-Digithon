#!/usr/bin/env sh
# Creates an AIDA bearer token.
#
#   scripts/new-token.sh <name> <projects> <scopes>
#   scripts/new-token.sh lana-claude SHOP,OPS mcp
#
# Prints the token ONCE (store it in a password manager or an env var such as AIDA_TOKEN), and the entry to append
# to tokens.yml. Only the SHA-256 hash goes into tokens.yml; AIDA never stores or logs the token itself.
set -eu

if [ "$#" -ne 3 ]; then
  echo "usage: $0 <name> <projects, e.g. SHOP,OPS> <scopes, e.g. mcp or mcp,admin>" >&2
  exit 2
fi
name=$1
projects=$2
scopes=$3

case "$name" in
  *[!A-Za-z0-9._-]*|"") echo "name may contain only letters, digits, '.', '_' and '-'" >&2; exit 2 ;;
esac

# 32 random bytes, hex encoded.
if command -v openssl >/dev/null 2>&1; then
  token=$(openssl rand -hex 32)
else
  token=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
fi

if command -v sha256sum >/dev/null 2>&1; then
  hash=$(printf '%s' "$token" | sha256sum | cut -d' ' -f1)
else
  hash=$(printf '%s' "$token" | shasum -a 256 | cut -d' ' -f1)
fi

list() { printf '%s' "$1" | tr -d ' ' | sed 's/,/, /g'; }

echo "Token for '$name' (shown once, store it now):"
echo
echo "  $token"
echo
echo "Append to tokens.yml (path in AIDA_TOKENS_FILE, default ./tokens.yml):"
echo
echo "- name: $name"
echo "  sha256: $hash"
echo "  projects: [$(list "$projects" | tr '[:lower:]' '[:upper:]')]"
echo "  scopes: [$(list "$scopes" | tr '[:upper:]' '[:lower:]')]"
