#!/bin/sh
# Kiem tra nhanh: mat khau trong .env co noi duoc Neon khong.
# Chay trong container nen KHONG dung toi bat cu thu gi dang chay o may.
cd "$(dirname "$0")" || exit 1
docker rm -f dbcheck >/dev/null 2>&1
docker run -d --name dbcheck --memory=512m -p 3009:3003 --env-file .env -e PORT=3003 ewallet-api:local >/dev/null || exit 1
i=0
while [ $i -lt 25 ]; do
  curl -sf http://localhost:3009/health >/dev/null 2>&1 && break
  i=$((i+1)); sleep 2
done
echo "--- /health ---"
curl -s http://localhost:3009/health; echo
echo "--- loi tu Postgres (neu co) ---"
docker logs dbcheck 2>&1 | grep -iE "PSQLException|FATAL|password authentication" | head -3
docker rm -f dbcheck >/dev/null 2>&1
