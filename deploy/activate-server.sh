#!/usr/bin/env bash
set -euo pipefail

release_dir=${1:?需要指定 /opt/fittrace/releases 下的发布目录}
release_dir=$(realpath "$release_dir")
case "$release_dir" in
  /opt/fittrace/releases/*) ;;
  *) echo '发布目录不正确' >&2; exit 1 ;;
esac
test "$(id -u)" = 0
test -f "$release_dir/apps/api/dist/main.js"
test -f "$release_dir/apps/h5/dist/index.html"
test -f /etc/fittrace/api.env

backup_dir="/var/backups/fittrace/$(date +%Y%m%d-%H%M%S)"
install -d -m 700 "$backup_dir"
runuser -u postgres -- pg_dump -Fc fit_trace > "$backup_dir/fit_trace.dump"
cp -a /usr/local/nginx/conf/nginx.conf "$backup_dir/nginx.conf"
install -m 600 /etc/fittrace/api.env "$backup_dir/api.env"
if test -f /usr/local/nginx/conf/fittrace.conf; then
  cp -a /usr/local/nginx/conf/fittrace.conf "$backup_dir/fittrace.conf"
fi

chown -R root:root "$release_dir"
ln -sfn /etc/fittrace/api.env "$release_dir/.env"
if test -L /opt/fittrace/current; then
  ln -sfn "$(readlink -f /opt/fittrace/current)" /opt/fittrace/previous
fi
ln -sfn "$release_dir" /opt/fittrace/current.next
mv -Tf /opt/fittrace/current.next /opt/fittrace/current
install -m 644 "$release_dir/deploy/fittrace-api.service" /etc/systemd/system/fittrace-api.service
systemd-analyze verify /etc/systemd/system/fittrace-api.service
systemctl daemon-reload
systemctl enable fittrace-api
systemctl restart fittrace-api

ready=false
for attempt in $(seq 1 20); do
  if curl -fsS --max-time 2 http://127.0.0.1:3100/api/v1/health; then
    ready=true
    break
  fi
  sleep 1
done
if test "$ready" != true; then
  journalctl -u fittrace-api -n 35 --no-pager
  echo 'API 未通过健康检查，未切换 Nginx。' >&2
  exit 1
fi

install -m 644 "$release_dir/deploy/nginx-fittrace.conf" /usr/local/nginx/conf/fittrace.conf
python3 <<'PY'
from pathlib import Path
config = Path('/usr/local/nginx/conf/nginx.conf')
content = config.read_text()
include = '    include /usr/local/nginx/conf/fittrace.conf;'
if include not in content:
    assert content.count('http {') == 1
    config.write_text(content.replace('http {', 'http {\n' + include, 1))
PY
if ! /usr/local/nginx/sbin/nginx -t; then
  cp -a "$backup_dir/nginx.conf" /usr/local/nginx/conf/nginx.conf
  if test -f "$backup_dir/fittrace.conf"; then
    cp -a "$backup_dir/fittrace.conf" /usr/local/nginx/conf/fittrace.conf
  else
    rm -f /usr/local/nginx/conf/fittrace.conf
  fi
  exit 1
fi
/usr/local/nginx/sbin/nginx -s reload
install -m 644 "$release_dir/deploy/logrotate-fittrace" /etc/logrotate.d/fittrace-nginx
runuser -u fittrace -- /opt/fittrace/runtime/bin/node "$release_dir/deploy/smoke.mjs"
systemctl show fittrace-api -p ActiveState -p MemoryCurrent -p MemoryPeak -p MemoryMax -p NRestarts
bash "$release_dir/deploy/prune-server-releases.sh" "$backup_dir"
printf '\n数据库与配置备份：%s\n' "$backup_dir"
