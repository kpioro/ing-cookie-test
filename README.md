# ing.pl – test zgody na cookies analityczne

[![Playwright E2E](https://github.com/kpioro/ing-cookie-test/actions/workflows/playwright.yml/badge.svg)](https://github.com/kpioro/ing-cookie-test/actions/workflows/playwright.yml)

Test E2E w Javie i Playwright:

1. otwiera `https://www.ing.pl`,
2. w banerze cookie klika **Dostosuj**,
3. włącza **Cookies analityczne**,
4. klika **Zaakceptuj zaznaczone**,
5. sprawdza w magazynie ciasteczek przeglądarki, że:
   - zgoda analityczna została zapisana (`cookiePolicyGDPR` i `cookiePolicyGDPR__details`),
   - po przeładowaniu strony zapisały się ciasteczka analityczne (Google Analytics `_ga*`, Adobe Analytics `AMCV_*`),
     a marketingowe (`_fbp`, `_gcl_au`, `_uetsid`, `_uetvid`) nie.

Test biegnie równolegle na `chromium`, `firefox` i `webkit`. Lokalnie wystarcza jedna komenda, a w GitHub Actions
każdy silnik dostaje osobny job w macierzy.

Stos: Java 21, Maven (wrapper), Playwright for Java 1.64, JUnit 5 (`@ParameterizedTest`), AssertJ, Awaitility,
Lombok, SLF4J + Logback.

## Uruchomienie lokalne

Wymagane jest tylko JDK 21 (`JAVA_HOME`). Maven pobiera się sam przez wrapper, a przeglądarki Playwright przy pierwszym
uruchomieniu.

```bash
./mvnw clean test                       # chromium, firefox i webkit jednocześnie
./mvnw clean test -Dbrowser=firefox     # jedna przeglądarka (tak działa każdy job w CI)
./mvnw clean test -Dheadless=false      # z widocznym oknem przeglądarki
```

Na Windows zamiast `./mvnw` użyj `mvnw.cmd`.

Na Linuksie przeglądarki potrzebują bibliotek systemowych. Jednorazowo (przez `sudo`) instaluje je:

```bash
./mvnw exec:java -Dexec.args="install --with-deps"
```

Przy porażce trace zapisuje się w `target/traces/trace-<browser>-<timestamp>.zip`. Można go otworzyć tak:

```bash
./mvnw exec:java -Dexec.args="show-trace target/traces/<plik>.zip"
```

Fragment logu z równoległego przebiegu. `[%thread]` to wątek JUnit, a druga kolumna to przeglądarka:

```
23:02:31.502 INFO  [ForkJoinPool-1-worker-4] [webkit  ] p.i.q.p.PlaywrightFactory - Session started: webkit 27.2 (headless=true)
23:02:31.860 INFO  [ForkJoinPool-1-worker-2] [chromium] p.i.q.p.PlaywrightFactory - Session started: chromium 156.0.8078.4 (headless=true)
23:02:34.107 INFO  [ForkJoinPool-1-worker-3] [firefox ] p.i.q.p.PlaywrightFactory - Session started: firefox 157.0 (headless=true)
23:02:35.668 INFO  [ForkJoinPool-1-worker-2] [chromium] p.i.q.c.ConsentCookieVerifier - Consent persisted: cookiePolicyGDPR=3 (ANALYTICAL), cookiePolicyGDPR__details={"cookieCreateTimestamp":1793916153838}
23:02:35.673 INFO  [ForkJoinPool-1-worker-2] [chromium] p.ing.qa.pages.HomePage - Reloading https://www.ing.pl/
23:02:37.260 INFO  [ForkJoinPool-1-worker-2] [chromium] p.i.q.c.ConsentCookieVerifier - Analytical cookies stored: [AMCV_2887EB1C55BBFD957F000101%40AdobeOrg, _ga, _ga_6S7EZXEX42]
```

## Konfiguracja

Parametry przekazuje się jako System Properties (`-Dklucz=wartość`).

| Klucz | Domyślnie | Opis |
|---|---|---|
| `baseUrl` | `https://www.ing.pl` | adres strony |
| `browser` | `ALL` | `ALL`, `chromium`, `firefox` lub `webkit` |
| `headless` | `true` | tryb bez okna |
| `timeout` | `15000` | limit akcji, nawigacji, asercji i oczekiwania na ciasteczka (ms) |
| `ignoreHttpsErrors` | `true` tylko dla WebKit na Windows | wymuszenie lub wyłączenie walidacji TLS |

## Pipeline (GitHub Actions)

Plik [`.github/workflows/playwright.yml`](.github/workflows/playwright.yml) uruchamia się na push do `main` i ręcznie
(`workflow_dispatch`). Macierz `browser: [chromium, firefox, webkit]` z `fail-fast: false` odpala trzy joby
równolegle, a każdy z nich wykonuje:

1. instalację JDK 21 (Temurin),
2. instalację tylko swojej przeglądarki,
3. `./mvnw clean test -Dbrowser=${{ matrix.browser }}`,
4. podsumowanie wyników testów w zakładce joba i jako osobny check,
5. publikację raportów Surefire, a przy porażce także trace'ów Playwright.

```yaml
name: Playwright E2E

on:
  push:
    branches: [main, master]
  workflow_dispatch:

permissions:
  contents: read
  checks: write

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

defaults:
  run:
    shell: bash

jobs:
  e2e:
    name: e2e / ${{ matrix.browser }}
    runs-on: ${{ vars.E2E_RUNNER || 'ubuntu-24.04' }}
    timeout-minutes: 20
    strategy:
      fail-fast: false
      matrix:
        browser: [chromium, firefox, webkit]

    steps:
      - uses: actions/checkout@v7

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "21"
          cache: ${{ runner.environment == 'github-hosted' && 'maven' || '' }}

      - name: Prepare Maven wrapper
        run: chmod +x mvnw

      - name: Resolve Playwright version
        if: runner.environment == 'github-hosted'
        id: playwright
        run: echo "version=$(sed -n 's:.*<playwright.version>\(.*\)</playwright.version>.*:\1:p' pom.xml)" >> "$GITHUB_OUTPUT"

      - name: Cache Playwright browsers
        if: runner.environment == 'github-hosted'
        uses: actions/cache@v6
        with:
          path: ~/.cache/ms-playwright
          key: playwright-${{ runner.os }}-${{ steps.playwright.outputs.version }}-${{ matrix.browser }}

      - name: Install ${{ matrix.browser }}
        run: ./mvnw -B -ntp -q exec:java -Dexec.args="install ${{ runner.environment == 'github-hosted' && '--with-deps' || '' }} ${{ matrix.browser }}"

      - name: Test on ${{ matrix.browser }}
        run: ./mvnw -B -ntp clean test -Dbrowser=${{ matrix.browser }}
        env:
          PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD: "1"

      - name: Test results summary
        if: always()
        uses: mikepenz/action-junit-report@v6.7.0
        with:
          report_paths: target/surefire-reports/TEST-*.xml
          check_name: test results / ${{ matrix.browser }}
          include_passed: true
          detailed_summary: true
          require_tests: true

      - name: Upload Playwright traces
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: playwright-traces-${{ matrix.browser }}
          path: target/traces/
          if-no-files-found: ignore
          retention-days: 14

      - name: Upload Surefire reports
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: surefire-reports-${{ matrix.browser }}
          path: target/surefire-reports/
          if-no-files-found: ignore
          retention-days: 14
```

### Dlaczego self-hosted runner

ing.pl pokazuje ruchowi spoza Polski stronę „Dlaczego widzisz tę stronę? Możliwe, że jesteś za granicą…” z hCaptcha.
Runnery GitHuba mają adresy Azure z USA, więc dostają tę stronę na pierwszym żądaniu, zanim zadziała jakikolwiek
kod przeglądarki. Test rozpoznaje tę sytuację i kończy się komunikatem z numerem incydentu Imperva, zamiast
mylącego „nie znaleziono banera”.

Dlatego joby biegną na self-hosted runnerach z polskim IP. Runner wybiera zmienna repozytorium `E2E_RUNNER`
(tu: etykieta `ing-pl`). Bez tej zmiennej workflow używa `ubuntu-24.04` i dodatkowo cache'uje Mavena i przeglądarki
oraz instaluje zależności systemowe (`--with-deps`). Żeby trzy przeglądarki szły naprawdę jednocześnie, potrzebne są
trzy runnery z tą etykietą, np. na jednej maszynie z Windows:

```bash
./config.cmd --url https://github.com/kpioro/ing-cookie-test --token <registration-token> \
             --name ing-1 --labels ing-pl --unattended
./run.cmd
```

Kroki workflow używają `shell: bash`. Na Windows runner szuka `bash` w `PATH`, więc Git Bash
(`C:\Program Files\Git\usr\bin`) musi być przed `C:\Windows\System32`, gdzie leży `bash.exe` z WSL.
Można to ustawić w pliku `.env` w katalogu runnera (`PATH=...`).

Workflow celowo nie reaguje na `pull_request`. Repozytorium jest publiczne, a PR z forka mógłby wykonać dowolny kod
na maszynie z self-hosted runnerem.

## Struktura projektu

```
src/test/java/pl/ing/qa
├── config       BrowserName, ConfigReader
├── playwright   PlaywrightFactory, StealthConfig, TraceRecorder
├── junit        PlaywrightExtension, BrowserSource
├── pages        HomePage, components/CookieBannerComponent
├── consent      ConsentLevel, ConsentCookieVerifier
└── tests        BaseTest, CookieConsentTest
src/test/resources
├── junit-platform.properties   równoległość JUnit
├── logback-test.xml            [%thread] i przeglądarka w logach
└── stealth/stealth.js          init script maskujący automatyzację
```

## Decyzje architektoniczne

**Bezpieczeństwo wątkowe.** Obiekty Playwright for Java wolno używać tylko z wątku, który je utworzył.
`PlaywrightFactory` trzyma `Playwright`, `Browser`, `BrowserContext` i `Page` w `ThreadLocal`, więc każdy wątek ma
własny stos i nic nie jest współdzielone. Każdy test dostaje świeży `BrowserContext` (pusty magazyn ciasteczek).
Po teście wszystko jest zamykane, a `ThreadLocal` czyszczone, żeby wątek z puli nie przeniósł stanu dalej.

**Cykl życia sesji.** `@BeforeEach` nie widzi argumentu `@ParameterizedTest`, czyli nie wie, którą przeglądarkę
uruchomić. Dlatego `PlaywrightExtension` (podpinany przez `BaseTest`) implementuje `InvocationInterceptor`: odczytuje
`BrowserName` z argumentów, otwiera sesję na wątku wykonującym test i zamyka ją po teście. Decyduje też o trace:
przy porażce zapisuje go do pliku, przy sukcesie zatrzymuje go bez zapisu.

**Równoległość.** `BrowserSource#browsers` (`@MethodSource`) zwraca przeglądarki wybrane przez `-Dbrowser`.
`junit-platform.properties` włącza tryb `concurrent` ze stałą pulą 3 wątków. `max-pool-size` celowo nie jest
ograniczone: wątek kontenera `@ParameterizedTest` czeka na swoje wywołania, a JUnit dokłada wtedy wątek
kompensujący, więc wszystkie trzy przeglądarki startują jednocześnie. W CI ten sam kod działa z jedną przeglądarką
na job.

**Stealth.** ing.pl stoi za WAF Imperva, dlatego `StealthConfig` ukrywa typowe sygnały automatyzacji:

- flagi Chromium (`--disable-blink-features=AutomationControlled` i inne) oraz kanał `chromium`, czyli pełna
  przeglądarka w trybie headless zamiast headless shell,
- User-Agent budowany z rzeczywistej wersji silnika, bez `HeadlessChrome`,
- polskie ustawienia: `pl-PL`, `Europe/Warsaw`, geolokalizacja Warszawy, viewport 1920×1080 i nagłówki
  `Accept-Language`/`Accept`,
- `stealth.js`, który poprawia `navigator.webdriver`, `window.chrome`, `navigator.languages`, `navigator.plugins`
  i `permissions.query`.

**Lokatory.** Używam wyłącznie `getByRole` z nazwą dostępną, bez XPath i klas CSS. Role i etykiety ARIA to kontrakt
dostępności, który przetrwa redesign. Dialog banera jest rozpoznawany po zawartości (przycisk „Dostosuj”,
przełączniki), a nie po id czy pozycji. Przełącznik analityki to `role=switch`, więc `check()` i `isChecked()`
działają na stanie `aria-checked`.

**Oczekiwanie na ciasteczka bez `Thread.sleep`.** Baner zapisuje zgodę asynchronicznie. `ConsentCookieVerifier`
odpytuje `context.cookies()` przez Awaitility z `pollInSameThread()`, bo bez tego Awaitility wołałby Playwright
z obcego wątku. W jednym `untilAsserted` sprawdza, że:

- oba ciasteczka istnieją,
- `cookiePolicyGDPR` daje zgodę na analitykę, ale nie na marketing,
- ciasteczko jest trwałe,
- znacznik czasu w `__details` pochodzi z bieżącego testu.

Potem test przeładowuje stronę i tak samo czeka, aż pojawią się ciasteczka analityczne (`_ga*`, `AMCV_*`) bez
marketingowych. Nazwy są dopasowywane po prefiksie, bo sufiksy (`_ga_<id strumienia>`, `AMCV_<id organizacji>`)
to identyfikatory konfiguracji banku.

## Ustalenia z analizy ing.pl

- Przycisk w ustawieniach ma etykietę **„Zaakceptuj zaznaczone”**, a nie „Zaakceptuj wybrane”. Lokator akceptuje obie.
- Kategorie to przełączniki `role=switch`: „Cookies techniczne”, „Cookies analityczne” i „Cookies marketingowe”.
- Wartości `cookiePolicyGDPR` z konfiguracji banera banku:

  | Wartość | Znaczenie |
  |---|---|
  | `0` | decyzja odłożona |
  | `1` | tylko techniczne |
  | `2` | marketingowe |
  | `3` | analityczne |
  | `4` | wszystkie |

  Strona sama to potwierdza: `CookiePolicyManager.isCookiePolicyAnalyticalAccepted()` zwraca `true` dla `3`.
- `cookiePolicyGDPR__details` ma postać `{"cookieCreateTimestamp":<ms>}`.
- Skrypty analityczne i marketingowe ładują się dopiero przy kolejnym wczytaniu strony po zapisaniu zgody. Wynik jest
  taki sam w chromium, firefox i webkit:

  | Wybór w banerze | `cookiePolicyGDPR` | Ciasteczka `.ing.pl` po przeładowaniu |
  |---|---|---|
  | tylko techniczne | `1` | tylko techniczne (Imperva) |
  | analityczne | `3` | `_ga`, `_ga_<id>`, `AMCV_<id>`, `AMCVS_<id>`, `s_cc` |
  | analityczne i marketingowe | `4` | powyższe oraz `_fbp`, `_gcl_au`, `_uetsid`, `_uetvid` |

## Znane ograniczenia

- **Geo-blokada:** poza Polską test nie dojdzie do banera (patrz wyżej). Test nie próbuje obchodzić hCaptcha.
- **WebKit na Windows:** port WinCairo Playwrighta odrzuca poprawny łańcuch certyfikatów ing.pl. Tylko dla tej
  kombinacji walidacja TLS jest domyślnie wyłączona, z ostrzeżeniem w logu. Można to zmienić przez
  `-DignoreHttpsErrors=true|false`. Na Linuksie TLS jest walidowany normalnie.
