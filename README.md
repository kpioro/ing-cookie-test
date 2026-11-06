# ing.pl – analytical cookie consent

[![Playwright E2E](https://github.com/kpioro/ing-cookie-test/actions/workflows/playwright.yml/badge.svg)](https://github.com/kpioro/ing-cookie-test/actions/workflows/playwright.yml)

A Java and Playwright test opens `https://www.ing.pl`, clicks **Dostosuj**, enables **Cookies analityczne**, clicks **Zaakceptuj zaznaczone**, and checks that the browser stored the consent (`cookiePolicyGDPR=3` and `cookiePolicyGDPR__details`). After a reload the analytical cookies are present (`_ga`, `AMCV_`) and the marketing cookies are not (`_fbp`, `_gcl_au`, `_uetsid`, `_uetvid`).

Chromium, Firefox, and WebKit run at the same time. JDK 21 is required.

```bash
./mvnw clean test
./mvnw clean test -Dbrowser=firefox
./mvnw clean test -Dheadless=false
```

On Windows use `mvnw.cmd`. On Linux, once, with administrator rights:

```bash
./mvnw exec:java -Dexec.args="install --with-deps"
```

| Property | Default |
|---|---|
| `baseUrl` | `https://www.ing.pl` |
| `browser` | `ALL` (`chromium`, `firefox`, `webkit`) |
| `headless` | `true` |
| `timeout` | `15000` |
| `ignoreHttpsErrors` | `false` |

## Pipeline

[`.github/workflows/playwright.yml`](.github/workflows/playwright.yml) runs on a push to `main` and on manual dispatch. The `build` job on `ubuntu-24.04` only compiles the tests. E2E is a matrix of three browsers:

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

ing.pl serves the home page only to addresses in Poland. E2E therefore runs on a runner with a Polish IP (`E2E_RUNNER=ing-pl`). Outside Poland the bank page is replaced by an Imperva block page; the test stops at once and reports the incident id.

On Windows, WebKit can reject the ing.pl certificate until the system has that root. Open the page once in Edge, or pass `-DignoreHttpsErrors=true`.
