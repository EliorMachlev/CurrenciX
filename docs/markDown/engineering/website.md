# Website

`docs/*.html`, served by GitHub Pages at `currencix.machlev.org`. It uses the app's mark, palette and type: paper and ink following the system's light or dark mode, Inter and Space Grotesk from [`docs/fonts/`](../../fonts), and Material Symbols Rounded as one sprite ([`docs/icons.svg`](../../icons.svg)), generated with the favicon by [`art/website/generate.py`](../../../art/website/generate.py). The pages show working pieces of the app rather than describing them ([`docs/site.js`](../../site.js), which sends nothing anywhere): the converter's receipt, a calculating keypad, the cart and providers as boards. Pictures are crops of the app's screenshot tests ([`docs/img/`](../../img)).

**Nothing loads from another site.** Each page's Content Security Policy (a `<meta>` tag, as Pages sets no headers) allows scripts, styles, fonts and images from the site only, the one inline script by its hash, inline styles only as `style` attributes, no forms, no `<base>`. [`docs/.well-known/security.txt`](../../.well-known/security.txt) points to private vulnerability reporting ([`docs/.nojekyll`](../../.nojekyll) lets Pages serve that folder).

**Every asset URL carries its content hash** (`name?v=<hash>`): Pages and the CDN let a browser keep CSS, JS and images for hours but a page for minutes, so without it a deploy could pair a new page with an old stylesheet. After changing anything under [`docs/`](../..), run:

```bash
python3 .github/scripts/site-assets.py stamp docs
```

The Site workflow (`check docs`) fails while a version is stale or an inline script isn't allowed by its page's CSP. `FontFilesTest` checks every bundled font, the app's and the site's, is a font file.

## See also

- The live site: <https://currencix.machlev.org/> ([features](https://currencix.machlev.org/features.html), [providers](https://currencix.machlev.org/providers.html), [privacy](https://currencix.machlev.org/privacy.html)); the README links to it.
- The screenshot tests the pictures are cropped from, and how to review them: [contributing.md](contributing.md#pull-request-checklist).
- The site's checks (`check docs`) and the CSP: [ci-cd.md](ci-cd.md#workflows); the CSP check itself is [`site-assets.py`](../../../.github/scripts/site-assets.py).
