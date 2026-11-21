#!/bin/sh
# 重新生成辈分水印篆体子集（新增辈分字后执行一次即可）
# 用法: sh fonts/subset-seal.sh [额外字符]
#   例: sh fonts/subset-seal.sh 祥康
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/fonts/bbyz-seal-script.ttf"
OUT="$ROOT/family-tree-web/src/main/resources/static/fonts/seal-subset.woff2"
PY="${FT_PYTHON:-python3}"

# 1. 从数据库导出全部辈分字（family-tree 专属容器；生产库名 family_tree，scratch 库名不同请覆盖 FT_DB）
#    root 口令取自仓库根 .env（MYSQL_ROOT_PASSWORD），不入库
DB="${FT_DB:-family_tree}"
FTROOT=$(grep ^MYSQL_ROOT_PASSWORD= "$ROOT/.env" | cut -d= -f2)
docker exec family-tree-mysql mysql --default-character-set=utf8mb4 -uroot -p"$FTROOT" -N \
    -e "SELECT DISTINCT name FROM ${DB}.family_generation WHERE name IS NOT NULL AND name<>'';" \
    | tr -d '\r' > /tmp/seal-gen.txt

# 2. 汇总字符集：辈分字 + 数字 + · + 命令行额外字符
"$PY" - "$@" <<'EOF'
import sys
chars = set('0123456789·')
chars.update(open('/tmp/seal-gen.txt', encoding='utf-8').read().strip())
for extra in sys.argv[1:]:
    chars.update(extra)
open('/tmp/seal-req.txt', 'w', encoding='utf-8').write(''.join(sorted(chars)))
print('subset chars:', len(chars))
EOF

# 3. 子集化为 woff2（需 pip install fonttools brotli）
"$PY" -m fontTools.subset "$SRC" \
    --text-file=/tmp/seal-req.txt \
    --flavor=woff2 --layout-features='' --no-hinting \
    --output-file="$OUT"
ls -la "$OUT"
