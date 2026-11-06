# ing.pl – test zgody na cookies analityczne

[![Playwright E2E](https://github.com/kpioro/ing-cookie-test/actions/workflows/playwright.yml/badge.svg)](https://github.com/kpioro/ing-cookie-test/actions/workflows/playwright.yml)

Test E2E w Javie i Playwright:

1. otwiera `https://www.ing.pl`,
2. w banerze cookie klika **Dostosuj**,
3. włącza **Cookies analityczne**,
4. klika **Zaakceptuj zaznaczone**,
5. sprawdza w magazynie ciasteczek, że zgoda analityczna została zapisana, a potem – po przeładowaniu – że strona
   odczytuje ją jako tylko analityczną i że pojawiły się ciasteczka analityczne, bez marketingowych.

Test biegnie równolegle na `chromium`, `firefox` i `webkit`. Lokalnie wystarcza jedna komenda. W GitHub Actions
kompilacja idzie na runnerze GitHuba, a E2E – macierz trzech przeglądarek na self-hosted runnerach z polskim IP.

Stos: Java 21, Maven (wrapper), Playwright for Java 1.64, JUnit 5 (`@ParameterizedTest`), AssertJ, Awaitility,
Lombok, SLF4J + Logback.

## Jak ocenić rozwiązanie

- **Pipeline:** [Actions](https://github.com/kpioro/ing-cookie-test/actions/workflows/playwright.yml).
  Job `build` kompiluje projekt na `ubuntu-24.04` i nie wchodzi na ing.pl. Trzy joby `e2e` (chromium, firefox, webkit)
  startują jednocześnie i odpalają scenariusz.
- **Lokalnie:** `./mvnw clean test` z polskiego adresu IP.
- **We własnym forku:** Actions są domyślnie wyłączone. Po włączeniu `build` powinien być zielony.
  Joby E2E na runnerach GitHuba padną na geo-blokadzie Imperva (komunikat z numerem incydentu). To nie jest błąd
  testu. Zielone E2E w forku wymaga self-hosted runnera z polskim IP i zmiennej `E2E_RUNNER` (patrz niżej).

## Uruchomienie lokalne

Wymagane jest JDK 21 (`JAVA_HOME`). Maven pobiera się przez wrapper, przeglądarki Playwright – przy pierwszym
uruchomieniu.

```bash
./mvnw clean test                       # chromium, firefox i webkit jednocześnie
./mvnw clean test -Dbrowser=firefox     # jedna przeglądarka (tak działa każdy job E2E w CI)
./mvnw clean test -Dheadless=false      # z widocznym oknem
```

Na Windows zamiast `./mvnw` użyj `mvnw.cmd`. Na Linuksie jednorazowo (przez `sudo`):

```bash
./mvnw exec:java -Dexec.args="install --with-deps"
```

Przy porażce trace jest w `target/traces/trace-<browser>-<timestamp>.zip`:

```bash
./mvnw exec:java -Dexec.args="show-trace target/traces/<plik>.zip"
```

## Konfiguracja

Parametry to System Properties (`-Dklucz=wartość`).

| Klucz | Domyślnie | Opis |
|---|---|---|
| `baseUrl` | `https://www.ing.pl` | adres strony |
| `browser` | `ALL` | `ALL`, `chromium`, `firefox` lub `webkit` |
| `headless` | `true` | tryb bez okna |
| `timeout` | `15000` | limit akcji, asercji i oczekiwania na ciasteczka (ms) |
| `ignoreHttpsErrors` | `false` | wyłączenie walidacji TLS (ostatnia deska ratunku na WebKit/Windows) |

## Pipeline

Pełny plik: [`.github/workflows/playwright.yml`](.github/workflows/playwright.yml).
Uruchamia się na push do `main` i ręcznie (`workflow_dispatch`). Nie na `pull_request` – repo jest publiczne,
a self-hosted runner to ta maszyna.

```yaml
jobs:
  build:
    runs-on: ubuntu-24.04
    steps:
      - run: ./mvnw -B -ntp -q test-compile

  e2e:
    runs-on: ${{ vars.E2E_RUNNER || 'ubuntu-24.04' }}
    strategy:
      fail-fast: false
      matrix:
        browser: [chromium, firefox, webkit]
    steps:
      - run: ./mvnw -B -ntp clean test -Dbrowser=${{ matrix.browser }}
```

Job `e2e` instaluje tylko przeglądarkę z macierzy, publikuje raport Surefire, a przy porażce też trace Playwright.
Akcje są przypięte do SHA commita.

### Self-hosted runner (E2E)

ing.pl pokazuje ruchowi spoza Polski stronę „Dlaczego widzisz tę stronę?” z hCaptcha. Runnery GitHuba mają adresy
Azure z USA, więc test kończy się komunikatem z numerem incydentu Imperva zamiast timeoutu na banerze.
Test nie obchodzi hCaptcha i nie maskuje sygnatur automatyzacji.

Joby E2E idą na runnerach z etykietą ze zmiennej `E2E_RUNNER` (tu: `ing-pl`). Bez zmiennej – `ubuntu-24.04`.
Trzy równoległe przeglądarki = trzy runnery z tą etykietą. Kroki używają `shell: bash`; na Windows Git Bash
(`C:\Program Files\Git\usr\bin`) musi być w `PATH` przed `C:\Windows\System32`.

## Architektura

- **ThreadLocal.** Playwright for Java nie jest bezpieczny wątkowo. Każdy wątek ma własny `Playwright` / `Browser` /
  `BrowserContext` / `Page`. Świeży kontekst na test, zamknięcie i `remove()` po teście.
- **`PlaywrightExtension`.** `@BeforeEach` nie widzi argumentu `@ParameterizedTest`. `InvocationInterceptor` czyta
  `BrowserName`, otwiera sesję na wątku testu i zapisuje trace tylko przy porażce.
- **Równoległość.** `BrowserSource` + `-Dbrowser`. `junit-platform.properties`: `concurrent`, pula 3 wątków.
  W CI ten sam kod, jedna przeglądarka na job.
- **Lokatory.** Tylko `getByRole` (dialog po „Dostosuj” / przełącznikach, analityka to `role=switch`). Bez XPath
  i klas CSS.
- **Ciasteczka bez `Thread.sleep`.** Awaitility + `pollInSameThread()`:
  1. kontrakt – `cookiePolicyGDPR=3`, świeży `__details`, po reloadzie `CookiePolicyManager` zgłasza analitykę
     bez marketingu;
  2. skutek – `_ga` / `_ga_<id>` i `AMCV_<id>`, przez 3 s brak `_fbp`, `_gcl_au`, `_uetsid`, `_uetvid`.
     Jeśli padnie tylko warstwa 2, komunikat mówi, że zgoda jest zapisana, ale nie pojawiły się oczekiwane
     ciasteczka dostawców (albo pojawiły się marketingowe).

Kontekst ma `pl-PL`, `Europe/Warsaw` i viewport 1920×1080, bo strona jest po polsku. Sandbox Chromium jest włączony
poza Linuksem.

## Ustalenia z ing.pl

Przycisk w ustawieniach to **„Zaakceptuj zaznaczone”** (lokator akceptuje też „wybrane”). Kategorie to
`role=switch`. Wartości `cookiePolicyGDPR`: `0` odłożone, `1` techniczne, `2` marketingowe, `3` analityczne,
`4` wszystkie – strona potwierdza to przez `CookiePolicyManager.isCookiePolicyAnalyticalAccepted()` dla `3`.
`__details` = `{"cookieCreateTimestamp":<ms>}`. Tagi analityczne ładują się dopiero po kolejnym wczytaniu strony.

`_ga` / `AMCV_` to obserwowany skutek zgody (listopad 2026), nie publiczny kontrakt. Źródłem prawdy jest ciasteczko
zgody i API banera.

| Wybór w banerze | `cookiePolicyGDPR` | Po przeładowaniu |
|---|---|---|
| tylko techniczne | `1` | brak GA/Adobe |
| analityczne | `3` | `_ga`, `_ga_<id>`, `AMCV_<id>` |
| analityczne i marketingowe | `4` | powyższe oraz `_fbp`, `_gcl_au`, `_uetsid`, `_uetvid` |

## Znane ograniczenia

- **Geo-blokada.** Poza Polską test nie dojdzie do banera. Nie obchodzi hCaptcha.
- **WebKit na Windows.** Port WinCairo ufa tylko korzeniom już obecnym w magazynie Windows. Jeśli WebKit odrzuci
  certyfikat ing.pl, otwórz `https://www.ing.pl` raz w Edge – Windows dociągnie brakujący korzeń – i uruchom test
  ponownie. Świadome wyłączenie walidacji: `-DignoreHttpsErrors=true`. Na Linuksie TLS jest walidowany normalnie.
