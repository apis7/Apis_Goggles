#!/usr/bin/env python3
"""One-off: split the legacy apis_search.goggle into Apis_health and Apis_news.

Rules that fit both topics (mainstream-news discards, platform rules, alt-media
boosts) are copied into both goggles. Middle East CSV weights become news rules:
0 -> discard, 0 < w < 1 -> downrank, 1 -> reviewed neutral (no rule).
"""
import csv
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "apis_search.goggle"
CSV = ROOT / "Apis_news" / "middle_east_sources.csv"

# Discards that only belong in the health goggle (medical lobby, mainstream
# health/nutrition, vegan).
HEALTH_DISCARDS = set("""
quackwatch.org harvard.edu medicalnewstoday.com healthline.com webmd.com acs.org
mayoclinic.org nccih.nih.gov mountsinai.org clevelandclinic.org mind.org.uk
verywellhealth.com verywellmind.com kaiserpermanente.org aafp.org fda.gov bu.edu
kumc.edu nhs.uk unitypoint.org hhs.gov umms.org christianacare.org
healthmatters.nyp.org pennmedicine.org uchicagomedicine.org osfhealthcare.org
vdh.virginia.gov hopkinsmedicine.org yalemedicine.org cdph.ca.gov
houstonmethodist.org health.ucdavis.edu healthdirect.gov.au scientificamerican.com
michiganmedicine.org lung.org healthpartners.com who.int atriumhealth.org
bhf.org.uk medcenterhealth.org avera.org everydayhealth.com eatingwell.com
bbcgoodfood.com livestrong.com pritikin.com health.com verywellfit.com
medpagetoday.com skepdic.com scienceblogs.com statnews.com respectfulinsolence.com
beckershospitalreview.com projcare.com healthfeedback.org earthclinic.com
fifthseasonfinancial.com mskcc.org cvs.com msk1.ru walgreens.com msdmanuals.com
1mg.com medlineplus.gov altibbi.com halodoc.com menshealth.com babycenter.com
healthcare-digital.com kidshealth.org weightwatchers.com netdoctor.co.uk
spiritualityhealth.com youaligned.com mantramagazine.com thrivemagazine.com
fitbottomedgirls.com ohsheglows.com tinybuddha.com wellandgood.com
mindbodygreen.com cancer.gov mayoclinicproceedings.org nursingworld.org
arthritis.org diabetes.org mentalhealth.gov alz.org aidsinfo.nih.gov
sleepfoundation.org eatright.org choosemyplate.gov kidney.org eatwell101.com
cookinglight.com nutritionfacts.org precisionnutrition.com authoritynutrition.com
myplate2yours.com jillianmichaels.com veganhealth.org forksoverknives.com
theveganrd.com plantbasednews.org happyherbivore.com veganuary.com
mercyforanimals.org aace.com health.ny.gov healthyeating.org transparentlabs.com
vegi1.org ox.ac.uk *vegan* *well* babycenter.com
""".split())

# Boosts that go in both goggles; any other boost from the legacy file is
# health-only unless listed in NEWS_BOOSTS.
SHARED_BOOSTS = set("""
*blog* *substack* gab.com reddit.com telegram.org blogger.com substack.com
lifesitenews.com zerohedge.com infowars.com globalresearch.ca presstv.com
wakingtimes.com sonsoflibertymedia.com dcclothesline.com nationalfile.com
blogspot.com blog.feedspot.com
""".split())
NEWS_BOOSTS = set("""
middleeasteye.net timesofisrael.com haaretz.com jpost.com military.com
responsiblestatecraft.org thepeoplesvoice.tv iranintl.com english.almayadeen.net
chabad.org
""".split())

TYPOS = {"bhf.ork.uk": "bhf.org.uk"}


def rule_key(line):
    """(action, value, target) for a rule line; target is site or url pattern."""
    pattern, _, opts = line.partition("$")
    action, value, site = "boost", "", ""
    for p in opts.split(","):
        k, _, v = p.strip().partition("=")
        k = k.lower()
        if k in ("boost", "downrank", "discard"):
            action, value = k, v
        elif k == "site":
            site = v.lower()
    return action, value, (site or pattern.strip().lower())


def normalise(line):
    line = line.strip()
    # Legacy lines like "$discard,site=*cnn*$discard,inurl" are a pasted
    # inurl rule after a stray "$discard,site=" prefix.
    m = re.match(r"\$discard,site=(\*[^$]+\*\$.+)$", line)
    if m:
        return m.group(1)
    m = re.search(r"site=([^,\s]+)", line)
    if m:
        site = TYPOS.get(m.group(1).lower(), m.group(1).lower())
        line = line[: m.start(1)] + site + line[m.end(1):]
    return line


def main():
    health = {"discard": [], "downrank": [], "boost": []}
    news = {"discard": [], "downrank": [], "boost": []}
    seen = {"health": set(), "news": set()}

    def add(name, bucket, line, target):
        if target in seen[name]:
            return
        seen[name].add(target)
        bucket.append(line)

    for raw in SRC.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.strip().startswith("!") or "$" not in raw:
            continue
        line = normalise(raw)
        action, _, target = rule_key(line)
        if action == "boost":
            to_health = target not in NEWS_BOOSTS
            to_news = target in NEWS_BOOSTS or target in SHARED_BOOSTS
        else:
            to_health = True
            to_news = target not in HEALTH_DISCARDS
        if to_health:
            add("health", health[action], line, target)
        if to_news:
            add("news", news[action], line, target)

    neutral = []
    with open(CSV, encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            w = (row.get("Weight (1 = normal, 0=block)") or "").strip()
            dom = row["source_domain"].strip().lower()
            if not w or dom in seen["news"]:
                continue
            w = float(w)
            if w == 0:
                add("news", news["discard"], f"$discard,site={dom}", dom)
            elif w < 1:
                strength = max(1, round((1 - w) * 10))
                add("news", news["downrank"], f"$downrank={strength},site={dom}", dom)
            else:
                neutral.append(dom)

    write(ROOT / "Apis_health" / "apis_health.goggle", "Apis Health",
          "Health and nutrition search without the medical lobby, by Apis",
          "Removes medical-lobby, pharma-funded and mainstream health sites. Boosts ancestral\n"
          "! nutrition, independent practitioners and people who care about you.", health, [])
    write(ROOT / "Apis_news" / "apis_news.goggle", "Apis News",
          "News without the corporate-media filter, by Apis",
          "Removes corporate and partisan propaganda outlets. Boosts independent and primary-source\n"
          "! reporting, including regional sources on the Middle East.", news, neutral)


def write(path, name, desc, blurb, rules, neutral):
    out = [
        f"! name: {name}",
        f"! description: {desc}",
        "! public: true",
        "! author: Apis",
        "! avatar: #fcea7e",
        "",
        f"! {blurb}",
        "!",
        "! Ratings are added by the Goggle Rater app. Keep the section headers below intact.",
        "",
    ]
    for action in ("discard", "downrank", "boost"):
        out += [f"! {action.upper()} LIST", ""] + rules[action] + [""]
    out += ["! NEUTRAL LIST (reviewed, deliberately no rule)", ""]
    out += [f"! neutral: {d}" for d in neutral] + [""]
    path.parent.mkdir(exist_ok=True)
    path.write_text("\n".join(out), encoding="utf-8", newline="\n")
    print(f"{path.name}: " + ", ".join(f"{a} {len(r)}" for a, r in rules.items())
          + f", neutral {len(neutral)}")


if __name__ == "__main__":
    main()
