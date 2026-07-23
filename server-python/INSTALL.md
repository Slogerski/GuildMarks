# GuildMarks Python API for an OVH VPS

The service has no web administrator panel and accepts no file uploads. Edit `/etc/guildmarks/guilds.json` through SSH. Changes are detected automatically by file modification time.

## Behaviour

- Minecraft identity is verified through Mojang only when a player equips, changes or removes a cape.
- The Python protocol verifies the selected cape once per Minecraft client session, when the player enters the dedicated server. Changing or removing the cape also requires a new proof.
- The mod only downloads player assignments once every 5 minutes. It sends no periodic heartbeat.
- `/players.json` and `?action=players` return assignments verified during the previous 28 hours.
- If a client does not provide a fresh proof, it automatically disappears from the public list after 28 hours.
- The API allows 10 total requests per IP pseudonym in 10 minutes. Repeated abuse blocks for 15, 15, 15, 30, 30, 30 and then 60 minutes; later blocks remain 60 minutes.

## Debian/Ubuntu installation

Run as root only for installation:

```bash
apt update
apt install -y python3 python3-venv nginx certbot python3-certbot-nginx
useradd --system --home /var/lib/guildmarks --shell /usr/sbin/nologin guildmarks
install -d -o guildmarks -g guildmarks -m 700 /var/lib/guildmarks
install -d -o root -g guildmarks -m 750 /etc/guildmarks
install -d -o root -g root -m 755 /opt/guildmarks-api
```

Copy `guildmarks_api/` and `requirements.txt` into `/opt/guildmarks-api/`, then:

```bash
cd /opt/guildmarks-api
python3 -m venv .venv
.venv/bin/pip install --upgrade pip
.venv/bin/pip install -r requirements.txt
cp guilds.json /etc/guildmarks/guilds.json
cp rate-limits.json /etc/guildmarks/rate-limits.json
cp guildmarks.env.example /etc/guildmarks/guildmarks.env
chmod 640 /etc/guildmarks/guilds.json /etc/guildmarks/rate-limits.json /etc/guildmarks/guildmarks.env
chown root:guildmarks /etc/guildmarks/guilds.json /etc/guildmarks/rate-limits.json /etc/guildmarks/guildmarks.env
```

Generate the rate-limit secret without putting it in shell history:

```bash
python3 -c 'import secrets; print(secrets.token_hex(32))'
```

Paste it into `/etc/guildmarks/guildmarks.env`, set the real domain in `GUILDMARKS_ALLOWED_HOSTS`, and replace `capes.example.com` in the Nginx file.

Install and start the application service:

```bash
cp systemd/guildmarks-api.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now guildmarks-api
```

Obtain the certificate before enabling the final HTTPS Nginx configuration:

```bash
systemctl stop nginx
certbot certonly --standalone -d capes.example.com
```

Then install the supplied Nginx configuration:

```bash
cp nginx/guildmarks-api.conf /etc/nginx/sites-available/guildmarks-api
ln -s /etc/nginx/sites-available/guildmarks-api /etc/nginx/sites-enabled/guildmarks-api
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable --now nginx
```

## Editing guilds through SSH

```bash
sudoedit /etc/guildmarks/guilds.json
python3 -m json.tool /etc/guildmarks/guilds.json >/dev/null
```

Restarting or reloading is normally unnecessary because the API checks the file modification time. Keep `file` equal to the filename at the end of the GitHub `image` URL. Use a new filename when changing image contents so clients refresh their cache.

Replace the catalog atomically so a request never reads a partially uploaded file:

```bash
python3 -m json.tool guilds.json >/dev/null
sudo cp guilds.json /etc/guildmarks/guilds.json.new
sudo chown root:guildmarks /etc/guildmarks/guilds.json.new
sudo chmod 640 /etc/guildmarks/guilds.json.new
sudo mv /etc/guildmarks/guilds.json.new /etc/guildmarks/guilds.json
```

The next catalog, player-list or proof request sees the new modification time and reloads the file. `guilds.json` contains only available guilds and image links; active player assignments remain in SQLite and must not be edited manually.

Edit `/etc/guildmarks/rate-limits.json` to change limiter values without updating or restarting the service. The process checks its modification time at most once per `reloadCheckSeconds` (60 seconds by default), so ordinary requests do not repeatedly read the file. Invalid edits leave the last valid configuration active.

## Public checks

```bash
curl -i https://capes.example.com/healthz
curl -i https://capes.example.com/guilds.json
curl -i https://capes.example.com/players.json
curl -i 'https://capes.example.com/guildmarks_api.php?action=capes'
```

These routes must work without cookies, browser JavaScript or a bot challenge. Uvicorn listens only on `127.0.0.1:8765`; expose only ports 80/443 through the firewall.

## Privacy and operations

- Uvicorn access logging and this Nginx virtual host's access logging are disabled.
- Application rate limits store an HMAC pseudonym instead of the raw IP.
- Nginx and the VPS provider may still keep infrastructure/security logs outside this application.
- Back up `/var/lib/guildmarks/guildmarks.sqlite3` and `/etc/guildmarks/guilds.json` privately.
- Never publish `/etc/guildmarks/guildmarks.env` or the SQLite database.
