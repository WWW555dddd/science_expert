#!/bin/sh

set -eu

auth="${NEO4J_AUTH:-}"
case "$auth" in
  */*) ;;
  *) exit 1 ;;
esac

username="${auth%%/*}"
password="${auth#*/}"

cypher-shell \
  --address bolt://127.0.0.1:7687 \
  --username "$username" \
  --password "$password" \
  "RETURN 1" >/dev/null 2>&1
