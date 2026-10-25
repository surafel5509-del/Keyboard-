# Third-party data and code

NOVA KEYBOARD bundles **no runtime libraries**. The only compiled dependency is
the Kotlin standard library, which is redistributed inside the APK under the
Apache License 2.0.

The following data files were used to generate the assets in
`app/src/main/assets/`. They are not shipped verbatim; the generator
(`tools/generate_assets.py`) filters and reformats them.

| Asset | Upstream | Licence | What was taken |
| --- | --- | --- | --- |
| `dictionaries/en_words.txt` | [hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords) `content/2018/en/en_50k.txt` | MIT | 40,000 `word<TAB>count` lines (a–z plus apostrophes only) |
| `dictionaries/en_bigrams.txt` | [NLTK data](https://github.com/nltk/nltk_data) — Brown corpus (`brown.zip`) | Public domain (Brown Corpus, Kučera & Francis) | 20,000 `previous<TAB>next<TAB>count` bigrams |
| `emoji/emoji.tsv` | [iamcal/emoji-data](https://github.com/iamcal/emoji-data) `emoji.json` | MIT | 1,914 rows of `emoji, short name, name, category, keywords` |
| `res/raw/key_click.wav` | generated | Public domain | Short sine burst with a decay envelope, 16-bit mono |

Regenerate everything with:

```bash
python3 tools/generate_assets.py     # needs network access
```

## Fonts

No fonts are bundled. The keyboard draws with the platform's default typeface
(`sans-serif` / `sans-serif-medium`), so Ethiopic rendering uses the device's
Noto Serif/Sans Ethiopic and emoji use the device's emoji font.

## Apache License 2.0 (Kotlin standard library)

The Kotlin standard library is included in the APK as `classes.dex` content.
Its licence text is available at <https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt>.
