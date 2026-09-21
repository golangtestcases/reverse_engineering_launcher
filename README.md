# MinerXray-src

Чистая копия исходников Xray/агента для продолжения разработки (без артефактов сборки).
Оригинал рабочей папки: `minerMinecraft\dist\autoreconnect\MinerXray\McSkillTest`.

## Структура

| Папка | Что это | Сборка |
|---|---|---|
| `agent/` | JVM attach-агент (agentmain + Javassist 3.33) — обход проверки подписи и integrity-watcher лаунчера McSkill | `build.cmd` (javac+jar) или Maven (`pom.xml`) |
| `xray/` | Xray-coremod для Galaxy_1.7.10 (FML coremod + mixins) | `build.cmd` |
| `portable-src/` | `McSkillBootstrap.java`, `TestDaemon.java` — bootstrap для portable-сборки | — |

## Как собирать

### agent
- Простым путём: `agent\build.cmd` (нужен `javac`/`jar` в PATH, `lib\javassist-3.33.0-GA.jar` уже лежит в `agent\lib\`).
- Или Maven: `mvn -f agent\pom.xml package` (uber-jar через shade).

### xray
`xray\build.cmd`. Перед запуском в нём прописаны локальные пути (JDK 21, клиент Galaxy_1.7.10,
forge.jar, RetroFuturaBootstrap) — при переезде на другую машину их надо поправить.
Скрипт ждёт `%ROOT%build\res\mcmod.info` — файл уже положен в `xray\build\res\`.
Результат: `xray\build\xray-mcskill.jar` → копируется в `mods\` клиента.

## Что НЕ входит (и почему)

- `srcMcSkill/` — декомпилированный чужой лаунчер; на GitHub светить не стоит (риск претензий).
- `xray\build\` — только артефакты (jar'ники, class), пересобираются скриптом.
- `agent\out\`, `agent\launcher-run.log` — артефакты/логи.
- `skiko\`, `forgex\`, `portable\`, `build-portable\` и прочие папки с бинарниками.

## GitHub

Готовые `.gitignore` лежат в `agent\` и `xray\` — можно делать отдельные репозитории:

```
cd agent  && git init && git add . && git commit -m "agent"
cd xray   && git init && git add . && git commit -m "xray"
cd portable-src && git init && git add . && git commit -m "portable-src"
```

и пушить каждый в свой репо (`git remote add origin <url>` + `git push -u origin main`).