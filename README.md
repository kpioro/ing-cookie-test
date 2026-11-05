# ing.pl – test zgody na cookies analityczne

[![Playwright E2E](https://github.com/kpioro/ing-cookie-test/actions/workflows/playwright.yml/badge.svg)](https://github.com/kpioro/ing-cookie-test/actions/workflows/playwright.yml)

Test E2E w Javie i Playwright:

1. otwiera `https://www.ing.pl`,
2. w banerze cookie klika **Dostosuj**,
3. włącza **Cookies analityczne**,
4. klika **Zaakceptuj zaznaczone**,
5. sprawdza w magazynie ciasteczek przeglądarki, że zgoda analityczna została zapisana (`cookiePolicyGDPR`
   i `cookiePolicyGDPR__details`).

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

Przy porażce trace zapisuje się w `target/traces/trace-<browser>-<timestamp>.zip`. Można go otworzyć tak:

```bash
./mvnw exec:java -Dexec.args="show-trace target/traces/<plik>.zip"
```

Fragment logu z równoległego przebiegu. `[%thread]` to wątek JUnit, a druga kolumna to przeglądarka:

```
22:30:49.462 INFO  [ForkJoinPool-1-worker-2] [chromium] p.i.q.c.ConsentCookieVerifier - Consent persisted: cookiePolicyGDPR=3 (ANALYTICAL), ...
22:30:50.072 INFO  [ForkJoinPool-1-worker-4] [webkit  ] p.i.q.c.ConsentCookieVerifier - Consent persisted: cookiePolicyGDPR=3 (ANALYTICAL), ...
22:30:50.959 INFO  [ForkJoinPool-1-worker-3] [firefox ] p.i.q.c.ConsentCookieVerifier - Consent persisted: cookiePolicyGDPR=3 (ANALYTICAL), ...
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

Plik [`.github/workflows/playwright.yml`](.github/workflows/playwright.yml) uruchamia się na push, na pull request
i ręcznie. Macierz `browser: [chromium, firefox, webkit]` z `fail-fast: false` odpala trzy joby równolegle, a każdy
z nich wykonuje:

1. instalację JDK 21 (Temurin) z cache Mavena,
2. instalację tylko swojej przeglądarki razem z zależnościami systemowymi (z cache kluczowanym wersją Playwright),
3. `./mvnw clean test -Dbrowser=${{ matrix.browser }}`,
4. podsumowanie wyników testów w zakładce joba i jako osobny check,
5. publikację raportów Surefire, a przy porażce także trace'ów Playwright.

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

## Znane ograniczenia

- **WAF w chmurze:** runnery GitHuba mają adresy IP spoza Polski. Jeśli Imperva zacznie blokować ten ruch, potrzebny
  jest self-hosted runner w Polsce albo proxy.
- **WebKit na Windows:** port WinCairo Playwrighta odrzuca poprawny łańcuch certyfikatów ing.pl. Tylko dla tej
  kombinacji walidacja TLS jest domyślnie wyłączona, z ostrzeżeniem w logu. Można to zmienić przez
  `-DignoreHttpsErrors=true|false`. Na Linuksie, czyli w CI, TLS jest walidowany normalnie.
