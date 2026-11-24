#!/usr/bin/env python3
"""Collects Israeli price-transparency files for stores in Ashdod and writes
a small static JSON API into ./site (served by GitHub Pages).

site/meta.json        - build date, per-chain status
site/stores.json      - [{id, chain, name, address}]
site/p/<NNN>.json     - products sharded by the last 3 digits of the barcode:
                        {"<barcode>": {"n": name, "p": {"<storeId>": agorot}}}
"""
import gzip, html, http.cookiejar, json, os, re, sys, time, urllib.parse, urllib.request
from datetime import datetime, timezone

OUT = sys.argv[1] if len(sys.argv) > 1 else "site"
ASHDOD_CODE = "70"
UA = {"User-Agent": "Mozilla/5.0"}
CERBERUS = "https://url.publishedprices.co.il"
CERBERUS_CHAINS = {  # username -> display name
    "RamiLevi": "רמי לוי", "yohananof": "יוחננוף", "osherad": "אושר עד",
    "TivTaam": "טיב טעם", "freshmarket": "מחסני השוק", "HaziHinam": "חצי חינם",
    "Victory": "ויקטורי", "Stop_Market": "סטופ מרקט",
    "politzer": "פוליצר", "SalachD": "סלאח דבאח", "doralon": "דור אלון",
}

def log(*a): print(*a, flush=True)

def retry(fn, n=5, wait=6):
    last = None
    for i in range(n):
        try:
            return fn()
        except Exception as e:
            last = e
            time.sleep(wait * (i + 1))
    log("   failed:", repr(last)[:120])
    return None

def decode(raw):
    try: raw = gzip.decompress(raw)
    except Exception: pass
    if raw[:2] in (b"\xff\xfe", b"\xfe\xff"): return raw.decode("utf-16")
    if len(raw) > 4 and raw[1:2] == b"\x00": return raw.decode("utf-16-le", "ignore")
    return raw.decode("utf-8-sig", "ignore")

def tag(b, k):
    m = re.search(rf"<{k}>(.*?)</{k}>", b, re.S | re.I)
    return html.unescape(m.group(1).strip()) if m else ""

def items(t):
    for b in re.findall(r"<Item>(.*?)</Item>", t, re.S | re.I):
        code = tag(b, "ItemCode").lstrip("0")
        try: price = float(tag(b, "ItemPrice"))
        except ValueError: continue
        if code.isdigit() and len(code) >= 8 and price > 0:
            yield code, tag(b, "ItemName"), round(price * 100)

def fresh(fname, days=4):
    m = re.findall(r"-(20\d{6})", fname)
    if not m: return False
    age = datetime.now(timezone.utc) - datetime.strptime(m[-1], "%Y%m%d").replace(tzinfo=timezone.utc)
    return age.days <= days

def ashdod_stores(xml):
    for b in re.findall(r"<Store>(.*?)</Store>", xml, re.S | re.I):
        city, addr, name = tag(b, "City"), tag(b, "Address"), tag(b, "StoreName")
        if city == ASHDOD_CODE or "אשדוד" in city + addr + name:
            yield (tag(b, "StoreID").lstrip("0") or "0"), name, addr

# ---------------- Shufersal (public site) ----------------
def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=90).read()

def shufersal_list(cat, store=0):
    h = get(f"https://prices.shufersal.co.il/FileObject/UpdateCategory?catID={cat}&storeId={store}&sort=Time&order=Desc&page=1").decode()
    return [html.unescape(x) for x in re.findall(r'href="(https://[^"]*\.gz[^"]*)"', h)]

def collect_shufersal():
    res = []
    links = retry(lambda: shufersal_list(5))
    if not links: return res
    xml = decode(retry(lambda: get(links[0])))
    for sid, name, addr in ashdod_stores(xml):
        l = retry(lambda: shufersal_list(2, sid))
        if not l: continue
        raw = retry(lambda: get(l[0]))
        if raw: res.append(("shufersal", "שופרסל", sid, name, addr, list(items(decode(raw)))))
    return res

# ---------------- Cerberus (publishedprices.co.il) ----------------
class Cerb:
    def __init__(s, user):
        s.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        s.op.addheaders = list(UA.items())
        h = s.op.open(CERBERUS + "/login", timeout=40).read().decode()
        tok = re.search(r'name="csrftoken" content="([^"]+)"', h).group(1)
        d = urllib.parse.urlencode({"username": user, "password": "", "Submit": "Sign in", "csrftoken": tok}).encode()
        r = s.op.open(CERBERUS + "/login/user", d, timeout=40)
        s.ok = "/file" in r.geturl()
        m = re.search(r'name="csrftoken" content="([^"]+)"', r.read().decode())
        s.tok = m.group(1) if m else tok
    def ls(s):
        d = urllib.parse.urlencode({"sEcho": 1, "iColumns": 5, "sColumns": ",,,,", "iDisplayStart": 0,
            "iDisplayLength": 100000, "mDataProp_0": "fname", "mDataProp_1": "typeLabel", "mDataProp_2": "size",
            "mDataProp_3": "ftime", "sSearch": "", "csrftoken": s.tok}).encode()
        return [x["fname"] for x in json.loads(s.op.open(CERBERUS + "/file/json/dir", d, timeout=90).read())["aaData"]]
    def get(s, name): return s.op.open(CERBERUS + "/file/d/" + urllib.parse.quote(name), timeout=120).read()

def collect_cerberus(user, chain):
    res = []
    c = retry(lambda: Cerb(user))
    if not c or not c.ok: return res
    files = retry(c.ls)
    if not files: return res
    stores = sorted(f for f in files if f.lower().startswith("stores"))
    if not stores: return res
    xml = decode(retry(lambda: c.get(stores[-1])))
    for sid, name, addr in ashdod_stores(xml):
        cand = sorted(f for f in files if f.lower().startswith("pricefull") and re.search(rf"-0*{sid}-\d{{8}}", f))
        if not cand or not fresh(cand[-1]): continue
        raw = retry(lambda: c.get(cand[-1]))
        if raw: res.append((user, chain, sid, name, addr, list(items(decode(raw)))))
    return res

def main():
    os.makedirs(f"{OUT}/p", exist_ok=True)
    results, status = [], {}
    r = collect_shufersal(); results += r; status["shufersal"] = len(r)
    for user, chain in CERBERUS_CHAINS.items():
        log("chain", user)
        r = collect_cerberus(user, chain); results += r; status[user] = len(r)
    stores, shards = [], {}
    for chain_id, chain, sid, name, addr, its in results:
        if "ליקוט" in name: continue  # online-picking branch, not a shop
        key = f"{chain_id}-{sid}"
        stores.append({"id": key, "chain": chain, "name": re.sub(r"\s+", " ", name), "address": re.sub(r"\s+", " ", addr)})
        for code, nm, price in its:
            e = shards.setdefault(code[-3:], {}).setdefault(code, {"n": nm, "p": {}})
            if nm and not e["n"]: e["n"] = nm
            e["p"][key] = price
    total = sum(len(v) for v in shards.values())
    log("stores:", len(stores), "products:", total, "status:", status)
    if len(stores) < 5 or total < 5000:  # safety: never publish a broken build
        sys.exit("too little data - refusing to publish")
    for k, v in shards.items():
        json.dump(v, open(f"{OUT}/p/{k}.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    # names index: [[barcode, name, number_of_stores_selling], ...] for search and list matching
    names = [[c, e["n"], len(e["p"])] for sh in shards.values() for c, e in sh.items() if e["n"]]
    names.sort(key=lambda x: -x[2])
    json.dump(names, open(f"{OUT}/names.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    json.dump(stores, open(f"{OUT}/stores.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.dump({"built": datetime.now(timezone.utc).isoformat(timespec="seconds"), "chains": status,
               "stores": len(stores), "products": total}, open(f"{OUT}/meta.json", "w"), indent=1)

if __name__ == "__main__":
    main()
