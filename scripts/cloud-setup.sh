#!/usr/bin/env bash
# Cloud onboarding helper. Uses the existing isolated checkout; never creates a worktree.
set -euo pipefail
cd /workspace/tearoom_travel
mkdir -p /workspace/tools /workspace/cache/{maven,m2,npm,docker}

if [[ ! -x /workspace/tools/jdk21/bin/javac ]]; then
  docker pull maven:3.9.11-eclipse-temurin-21
  jdk_container=$(docker create maven:3.9.11-eclipse-temurin-21)
  trap 'docker rm "$jdk_container" >/dev/null' EXIT
  docker cp "$jdk_container:/opt/java/openjdk" /workspace/tools/jdk21
  docker rm "$jdk_container" >/dev/null
  trap - EXIT
fi
export JAVA_HOME=/workspace/tools/jdk21
export MAVEN_USER_HOME=/workspace/cache/maven
export MAVEN_OPTS="${MAVEN_OPTS:-} -Duser.home=/workspace/tools/maven-home -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
export npm_config_cache=/workspace/cache/npm

# Reuse the platform HTTPS proxy and system trust; never disable certificate checks.
python3 - <<'PY'
import os, pathlib, socket, urllib.parse, xml.etree.ElementTree as ET
proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy') or '')
settings = pathlib.Path('/workspace/tools/maven-home/.m2/settings.xml')
settings.parent.mkdir(parents=True, exist_ok=True)
if proxy.hostname:
    if proxy.username or proxy.password:
        raise SystemExit('Authenticated proxies must use secure existing settings, not saved credentials')
    if not settings.exists():
        root = ET.Element('settings'); proxies = ET.SubElement(root, 'proxies'); p = ET.SubElement(proxies, 'proxy')
        for key, value in [('id','cloud-egress'), ('active','true'), ('protocol','http'), ('host',proxy.hostname),
                           ('port',str(proxy.port or 80)), ('nonProxyHosts','localhost|127.0.0.1')]:
            ET.SubElement(p,key).text = value
        ET.ElementTree(root).write(settings, encoding='unicode')
    address = socket.gethostbyname(proxy.hostname)
    pathlib.Path('/workspace/tools/tripmate-compose-cloud.yml').write_text(f'''services:
  backend:
    build:
      extra_hosts: ["{proxy.hostname}:{address}"]
      secrets: [maven_settings, java_cacerts]
  frontend:
    build:
      extra_hosts: ["{proxy.hostname}:{address}"]
      secrets: [node_ca_bundle]
secrets:
  maven_settings:
    file: /workspace/tools/maven-home/.m2/settings.xml
  java_cacerts:
    file: /etc/ssl/certs/java/cacerts
  node_ca_bundle:
    file: /etc/ssl/certs/ca-certificates.crt
''')
PY
(cd backend && ./mvnw -B -ntp -Dmaven.repo.local=/workspace/cache/m2 -DskipTests package)
(cd frontend && npm ci --no-audit --no-fund && npm run build)

# This is an ignored local development credential, generated only if absent.
python3 - <<'PY'
import pathlib, secrets
p = pathlib.Path('.env')
if not p.exists():
    p.write_text('DB_PASSWORD=' + secrets.token_hex(24) + '\nDB_USER=tripmate\nDB_NAME=tripmate\nDB_PORT=5432\nFRONTEND_PORT=8081\n')
    p.chmod(0o600)
PY
compose_args=(-f docker-compose.yml)
if [[ -f /workspace/tools/tripmate-compose-cloud.yml ]]; then
  compose_args+=(-f /workspace/tools/tripmate-compose-cloud.yml)
fi
docker compose --profile app "${compose_args[@]}" config --quiet
docker compose --profile app "${compose_args[@]}" build --build-arg HTTP_PROXY --build-arg HTTPS_PROXY --build-arg NO_PROXY
