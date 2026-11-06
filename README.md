# ing.pl – zgoda na cookies analityczne

[![Playwright E2E](https://github.com/kpioro/ing-cookie-test/actions/workflows/playwright.yml/badge.svg)](https://github.com/kpioro/ing-cookie-test/actions/workflows/playwright.yml)

Test w Javie i Playwright otwiera `https://www.ing.pl`, klika **Dostosuj**, włącza **Cookies analityczne**, klika **Zaakceptuj zaznaczone** i sprawdza, że przeglądarka zapisała zgodę (`cookiePolicyGDPR=3` i `cookiePolicyGDPR__details`). Po przeładowaniu strony są ciasteczka analityczne (`_ga`, `AMCV_`), a nie ma marketingowych (`_fbp`, `_gcl_au`, `_uetsid`, `_uetvid`).

Chromium, Firefox i WebKit idą jednocześnie. Potrzebne jest JDK 21.

```bash
./mvnw clean test
./mvnw clean test -Dbrowser=firefox
./mvnw clean test -Dheadless=false
```

Na Windows użyj `mvnw.cmd`. Na Linuksie raz, z uprawnieniami administratora:

```bash
./mvnw exec:java -Dexec.args="install --with-deps"
```

| Parametr | Domyślnie |
|---|---|
| `baseUrl` | `https://www.ing.pl` |
| `browser` | `ALL` (`chromium`, `firefox`, `webkit`) |
| `headless` | `true` |
| `timeout` | `15000` |
| `ignoreHttpsErrors` | `false` |

## Pipeline

[`.github/workflows/playwright.yml`](.github/workflows/playwright.yml) startuje na push do `main` i ręcznie. Job `build` na `ubuntu-24.04` tylko kompiluje testy. E2E to macierz trzech przeglądarek:

```yaml
e2e:
  runs-on: ${{ vars.E2E_RUNNER || 'ubuntu-24.04' }}
  strategy:
    fail-fast: false
    matrix:
      browser: [chromium, firefox, webkit]
  steps:
    - run: ./mvnw -B -ntp clean test -Dbrowser=${{ matrix.browser }}
```

ing.pl serwuje stronę główną tylko adresom z Polski. E2E idzie więc na runner z polskim IP (`E2E_RUNNER=ing-pl`). Poza Polską zamiast strony banku jest blokada Imperva; test kończy się wtedy od razu i podaje numer incydentu.

WebKit na Windows potrafi odrzucić certyfikat ing.pl, dopóki system nie ma tego korzenia. Wystarczy raz otworzyć stronę w Edge albo podać `-DignoreHttpsErrors=true`.
