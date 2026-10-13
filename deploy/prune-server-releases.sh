#!/usr/bin/env bash
set -euo pipefail

test "$(id -u)" = 0
release_root=/opt/fittrace/releases
backup_root=/var/backups/fittrace
current=$(readlink -f /opt/fittrace/current)
previous=$(readlink -f /opt/fittrace/previous)
for keep in "$current" "$previous"; do
  test -d "$keep"
  test "$(dirname "$keep")" = "$release_root"
done
backup=${1:?需要指定本次发布前创建的备份目录}
backup=$(realpath "$backup")
test "$(dirname "$backup")" = "$backup_root"
test -s "$backup/fit_trace.dump"
test -s "$backup/fittrace.apk"
test -s "$backup/api.env"

# 删除前确认保留版本没有依赖待删除目录，也没有失效链接。
python3 - "$release_root" "$current" "$previous" <<'PYCHECK'
import os
import sys
from pathlib import Path
root = Path(sys.argv[1]).resolve()
kept = [Path(value).resolve() for value in sys.argv[2:]]
for release in kept:
    for directory, dirs, files in os.walk(release, followlinks=False):
        for name in dirs + files:
            link = Path(directory) / name
            if not link.is_symlink():
                continue
            target = link.resolve()
            if not target.exists():
                raise SystemExit(f'拒绝清理：失效链接 {link} -> {target}')
            if target.is_relative_to(root) and not any(target.is_relative_to(path) for path in kept):
                raise SystemExit(f'拒绝清理：保留版本依赖旧目录 {link} -> {target}')
PYCHECK

for candidate in "$release_root"/*; do
  test -d "$candidate" || continue
  test ! -L "$candidate" || continue
  if test "$candidate" != "$current" && test "$candidate" != "$previous"; then
    rm -rf -- "$candidate"
    printf 'Removed release: %s\n' "$candidate"
  fi
done
for candidate in "$backup_root"/*; do
  test ! -L "$candidate" || continue
  if test -d "$candidate" && test "$candidate" != "$backup"; then
    rm -rf -- "$candidate"
    printf 'Removed backup: %s\n' "$candidate"
  fi
done
for candidate in "$backup_root"/api.env-before-*; do
  if test -f "$candidate" && test ! -L "$candidate"; then
    rm -- "$candidate"
    printf 'Removed older config backup: %s\n' "$candidate"
  fi
done
printf 'Kept current: %s\nKept previous: %s\nKept backup: %s\n' "$current" "$previous" "$backup"
