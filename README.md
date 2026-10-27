# Apis_Goggles
Apis' Brave Search "Goggles"

| Goggle | File |
|---|---|
| Apis Health | `Apis_health/apis_health.goggle` |
| Apis News | `Apis_news/apis_news.goggle` |

`apis_search.goggle` is the original combined goggle, kept so existing followers keep working.

- `rater_app/` — Goggle Rater, an Android app that finds sites not yet in a goggle, lets you rate them, and commits the rules here.
- `tools/split_goggle.py` — one-off split of `apis_search.goggle` into the two goggles.
- `tools/parse_goggle.py <file.goggle>` — dumps a goggle's rules to CSV.
