#!/usr/bin/env python3
"""Copy Hermes profile API keys from the server's .env into Hermes for Kompakt.

Run it yourself on a computer that can SSH to the Hermes host and reach the phone over adb.
Keys go from the server to the phone and are never printed; only profile names are.

    tools/import-profiles.py --ssh my-server --env /docker/hermes/.env \\
        --url https://hermes.my-tailnet.ts.net --serial 100.x.y.z:PORT

It reads every HERMES_<NAME>_GATEWAY_API_KEY (the default profile's HERMES_GATEWAY_API_KEY is
left alone), works out each profile's slug by trying <name> with hyphens, then underscores,
against /p/<slug>/v1/models, and pushes the ones that answer to the app as a one-time import
file. The app merges it on its next start and deletes it.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

PACKAGE = "com.mudita.mmd.samples.hermes"
IMPORT_PATH = f"/sdcard/Android/data/{PACKAGE}/files/profiles-import.json"
KEY_NAME = re.compile(r"^HERMES_([A-Z0-9_]+)_GATEWAY_API_KEY$")


def read_env(host: str, path: str) -> dict:
    out = subprocess.run(["ssh", host, "cat", path], check=True, capture_output=True, text=True).stdout
    env = {}
    for line in out.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.removeprefix("export ").split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            value = value[1:-1]
        env[name.strip()] = value
    return env


def answers(url: str, slug: str, key: str) -> int:
    req = urllib.request.Request(f"{url}/p/{slug}/v1/models", headers={"Authorization": f"Bearer {key}"})
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code
    except OSError:
        return 0


class AdbError(Exception):
    pass


def adb(serial, *args, **kw):
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.returncode != 0:
        raise AdbError(f"adb {' '.join(args[:2])} failed: {(r.stderr or r.stdout).strip()}")
    return r


def connect(serial):
    """Wireless-debugging serials (host:port) must be connected before use."""
    if serial and ":" in serial:
        out = subprocess.run(["adb", "connect", serial], capture_output=True, text=True)
        text = (out.stdout + out.stderr).strip()
        if "connected to" not in text:
            raise AdbError(f"adb connect {serial}: {text or 'failed'} (check the port in Wireless debugging)")
    state = subprocess.run(["adb"] + (["-s", serial] if serial else []) + ["get-state"], capture_output=True, text=True)
    if state.stdout.strip() != "device":
        raise AdbError(f"no adb device {serial or ''}: {(state.stderr or state.stdout).strip()}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--ssh", required=True, help="SSH host that has the Hermes .env")
    ap.add_argument("--env", required=True, help="path of the .env on that host")
    ap.add_argument("--url", required=True, help="Hermes base URL the phone uses")
    ap.add_argument("--serial", help="adb device (host:port); default: the only connected device")
    ap.add_argument("--dry-run", action="store_true", help="find and check profiles, but don't touch the phone")
    a = ap.parse_args()
    url = a.url.rstrip("/")

    env = read_env(a.ssh, a.env)
    profiles = []
    for name, key in sorted(env.items()):
        m = KEY_NAME.match(name)
        if not m or not key:
            continue
        base = m.group(1).lower()
        for slug in dict.fromkeys([base.replace("_", "-"), base]):
            status = answers(url, slug, key)
            if status == 200:
                profiles.append({"slug": slug, "apiKey": key})
                print(f"  {slug}: OK")
                break
        else:
            print(f"  {base}: no profile answered (last status {status}), skipped")

    if not profiles:
        print("Nothing to import.")
        return 1
    if a.dry_run:
        print(f"Dry run: would import {', '.join(p['slug'] for p in profiles)}")
        return 0

    try:
        connect(a.serial)
    except AdbError as e:
        print(e, file=sys.stderr)
        return 1

    fd, tmp = tempfile.mkstemp(suffix=".json")
    try:
        with os.fdopen(fd, "w") as f:
            json.dump(profiles, f)
        adb(a.serial, "shell", "mkdir", "-p", os.path.dirname(IMPORT_PATH))
        adb(a.serial, "push", tmp, IMPORT_PATH)
    except AdbError as e:
        print(e, file=sys.stderr)
        return 1
    finally:
        os.remove(tmp)
    # Restart the app so it merges the file (and deletes it) right away.
    adb(a.serial, "shell", "am", "force-stop", PACKAGE)
    adb(a.serial, "shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity")
    time.sleep(5)
    left = adb(a.serial, "shell", f"ls {IMPORT_PATH} 2>/dev/null || true").stdout.strip()
    if left:
        print(f"Warning: {IMPORT_PATH} is still on the phone; open Hermes once to import and delete it.")
    print(f"Imported {', '.join(p['slug'] for p in profiles)}. Switch profiles with the person icon in Hermes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
