# McSkill Launcher attach-agent

Агент на JVM Attach API (`agentmain`) + Javassist 3.33, который патчит загруженные
классы лаунчера в рантайме:

| # | Класс (обфусц.) | Метод | Патч |
|---|------------------|-------|------|
| 1 | `net.mcsgroup.launcher.core.i.d` (SignatureVerifier) | `a(ClientProfile)` | всегда `true` |
| 2 | `net.mcsgroup.launcher.core.e.b` (IntegrityWatcher) | `register(Path,List,List,Set)`, `scheduleKill(Function0)` | пустые тела |
| 3 | `net.mcsgroup.launcher.core.e.e$d` (корутина LaunchViewModel) | `invokeSuspend` | guard abort-условия (декомпилированная строка 836 «pre-launch FAILED — aborting launch») переписан так, что abort-ветка недостижима |

Тело методов #1/#2 формируется по типу возврата из JVM-дескриптора
(`m.getMethodInfo().getDescriptor()`): `V`→`return;`, `Z`→`return true;`,
`I/J/S/B/C`→`return 0;`, остальное→`return null;`.
#3 — точечная замена 15 байт guard-блока НАПРЯМУЮ в сыром classfile-буфере
(`iconst_0; ifeq T2; nop×11`) — КАТЕГОРИЧЕСКИ без перекодирования через javassist.

### Почему патч #3 нельзя делать через javassist

`invokeSuspend` — suspend-lambda: JVM верифицирует такой метод по Kotlin-атрибуту
**StackMapTable** (73 фрейма). Javassist-3.33 при `toBytecode()` молча ВЫБРАСЫВАЕТ
этот атрибут, и при первом исполнении метода JVM падает:

```
java.lang.VerifyError: Expecting a stackmap frame at branch target 2585
  Location: net/mcsgroup/launcher/core/e/e$d.invokeSuspend @9: tableswitch
```

Raw-патч правит ровно те же 15 байт в том же Code attribute: длины и все смещения
не меняются, StackMapTable остаётся нетронутой, стек-эффект в точке входа `T2`
(продолжение запуска) совпадает с исходным (одно int до `ifeq`, пустой стек после)
— фреймы остаются валидными, VerifyError не возникает (проверено в живом лаунчере
и офлайн-тестом SmokeVerify). Если форма guard'а не совпала (другая версия
лаунчера) — агент НЕ трогает класс и пишет `!!guard shape mismatch, skipping`.

## Структура

```
agent/
  pom.xml                         Maven-сборка (uber-jar через shade)
  build.cmd                       сборка без Maven (javac + jar)
  src/com/mcskill/bypass/
    LauncherAgent.java            агент: agentmain/premain + transformer
    AttachTool.java               attach-утилита (VirtualMachine.attach/loadAgent/detach)
    PatchTest.java                офлайн-проверка на реальных .class из Launcher.jar
  e2e/                            smoke-тест attach на подставном JVM (HostStub)
  lib/javassist-3.33.0-GA.jar     зависимость (кладётся вручную перед сборкой)
```

## Сборка

Без Maven (требуются `javac`/`jar` в PATH):

```cmd
cd agent
build.cmd
```

Результат:
- `out\launcher-agent.jar` — агент (манifest: `Agent-Class`, `Can-Retransform-Classes: true`; javassist уже внутри);
- `out\attach\com\mcskill\bypass\AttachTool.class` — утилита attach.

С Maven: `mvn package` — тоже `target/launcher-agent-1.0.jar` (uber-jar).

## Запуск

### 1) Лаунчер (JVM с разрешённой динамической загрузкой агентов)

```cmd
java -XX:+EnableDynamicAgentLoading -jar Launcher.jar
```

> Чтобы Java-лаунчер (Kotlin + androidx.compose/skiko) поднял GUI, ему нужна
> нативная библиотека `skiko-windows-x64.dll`. В этом дистрибутиве её нет —
> качается из Maven и указывается через системное свойство:
> ```cmd
> :: один раз: скачать и распаковать нативный рантайм skiko (версия из org/jetbrains/skiko/Version = 0.144.6)
> curl -o skiko-runtime.jar https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-windows-x64/0.144.6/skiko-awt-runtime-windows-x64-0.144.6.jar
> mkdir skiko && cd skiko && jar xf ..\skiko-runtime.jar
> :: и запускать так:
> java -XX:+EnableDynamicAgentLoading -Dskiko.library.path=C:\путь\skiko -jar Launcher.jar
> ```
> Без этого лаунчер падает: `skiko-windows-x64.dll.sha256, proper native dependency missing`.

> JDK 21+: без `-XX:+EnableDynamicAgentLoading` загрузка агента через attach
> запрещена (`AgentLoadException`). Если запускаешь через Qt-стартер в debugMode —
> задай флаг переменной окружения: `set JDK_JAVA_OPTIONS=-XX:+EnableDynamicAgentLoading` перед стартом.
> debugMode при этом делает attach возможным (DisableAttachMechanism не выставляется).

### 2) Attach

```cmd
jps -l                       :: найти PID JVM лаунчера (процесс с -jar Launcher.jar)
java --add-modules jdk.attach -cp out\attach com.mcskill.bypass.AttachTool <PID> C:\путь\к\launcher-agent.jar
```

Ожидаемый вывод: `agent loaded into pid <PID>` / `detached`.
В логах лаунчера появятся строки `[LauncherAgent] ...`:
`retransformed N loaded class(es)` — если классы уже были загружены, или
патчи применятся при загрузке классов (e$d грузится в момент запуска игры).

### Альтернатива: патч на старте JVM

```cmd
java -javaagent:out\launcher-agent.jar -XX:+EnableDynamicAgentLoading -jar Launcher.jar
```
(работает даже если attach недоступен; вызывает `premain` → ту же логику).

## Проверка на реальных классах (без attach)

```cmd
:: распаковать классы из Launcher.jar, затем:
javac -cp lib\javassist-3.33.0-GA.jar -d out\test src\com\mcskill\bypass\PatchTest.java
java -cp lib\javassist-3.33.0-GA.jar;out\test;<LJ>\Launcher.jar com.mcskill.bypass.PatchTest <LJ>\net\mcsgroup\launcher\core
```
где `<LJ>` — каталог, куда распакованы `i\d.class`, `e\b.class`, `e\e$d.class`.
PatchTest прогоняет те же преобразования и грузит результат через `ClassLoader.defineClass`
(JVM верифицирует байткод): вывод `OK` по каждому классу.

## Примечания

- Имя класса в `transform` при retransformation приходит со слешами
  (`net/mcsgroup/launcher/core/i/d`) — агент нормализует его в точки (`replace('/', '.')`).
- `maxLocals` для нового тела = слоты параметров + слот `this` (instance-методы).
- Guard`а abort-ветки в `e$d` найден по маркерной строке «pre-launch FAILED»
  (pc 1324..1338, `iload 10; ifne 1339; iload 12; ifeq 1339; iload 14; ifeq 1660`).
  Если форма не совпала (другая версия лаунчера) — агент пишет
  `!!guard shape mismatch, skipping` и НЕ трогает класс.
- Всё проверено на Java 21 (Temurin), Launcher.jar v21 (classfile 65):
  3 патча офлайн (defineClass OK) + e2e attach на живом JVM (`res=true`).