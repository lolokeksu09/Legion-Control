# Legion Control alpha.26 — Navigation Component + MVVM + Game Hub

## Зачем и что изменилось

Ранее главный экран `MainActivity` содержал более 900 строк кнопок/диагностики и десятки отдельных модулей. Теперь при запуске открывается **`NavigationHostActivity`**, реальный `NavHostFragment` с файлом `app/src/main/res/navigation/legion_nav_graph.xml`. Пять вкладок: **Главная / Игры / Инструменты / История / Настройки**, плюс отдельное вложенное окно карточки игры. Есть системная навигация назад, сохранение back-stack при переключении вкладок и восстановление при повороте.

**Это реальная MVVM-граница для нового Game Hub**, но старые независимые Activity пока не переписаны на MVVM: они оставлены без изменений, чтобы не сломать Kotlin/Rust и SQLite. Это не фиктивное обещание полной миграции каждого модуля.

### Слои

- **View**: `NavigationHostActivity` (навигационный хост и 5 вкладок), `HubPageFragment` (пять видов одной навигационной точки), `GameDetailFragment` (карточка игры).
- **ViewModel**: `HubViewModel` (LiveData состояний Loading/Ready/Failed, однопоточный worker, refresh, избранное, выбор игры для Toolbox).
- **Repository**: `HubRepository`, читает **уже существующие** `ApplicationCenterRepository`, `SmartProfilesRepository`, `GameSessionRepository`, `CaptureClipLibrary` и `ApplicationInventory.launchable`. Запросы SQLite/PackageManager/MediaStore выполняются **вне главного потока**.
- **Backend bridge**: прежний `MainActivity` теперь **внутренний, неэкспортируемый экран «Диагностика Kotlin + Rust»**, доступен из «Инструменты» и «История». Все прежние Activity, сервисы, Rust JNI/SQLite v12 и их пользовательские операции остались на местах. Приложение по-прежнему не требует Root или Shizuku.

### Пять разделов

1. **Главная**: реальные количества избранного, профилей и собственных MP4 (просмотр до 100), последние игровые сессии, прямые кнопки Toolbox/Capture Studio/Game Session. Снимок батареи и частоты дисплея читается из публичных Android API — это не FPS игры.
2. **Игры**: уже установленные, видимые через системный `MAIN/LAUNCHER` приложения; по умолчанию игры по `ApplicationInfo.CATEGORY_GAME` + избранное, переключение на все видимые приложения, поиск по ярлыку/пакету, отдельная карточка. Если приложение не видно системе, избранное показано с пометкой **недоступно для запуска**, а не «удалено». Пользовательская SQLite-библиотека (лимит 150) остается единственной базой избранного.
3. **Карточка игры**: запуск доступного приложения, включение/выключение избранного с сохранением исходных `alias/category/note`, явная запись пакета в ранее существующие `ToolboxPreferences`, переход к существующим Game Session (с `smart_package`), Smart Profiles (с `app_center_package`), Experiment Lab, Capture Studio и Application Center. Без самовольного изменения глобальных системных настроек.
4. **Инструменты**: Game Toolbox, Capture Studio, Game Session, Smart Profiles, Audio Lab, Haptic Lab, Gamepad Live, Gaming/Hardware/Compatibility Lab, Display Patterns, Connectivity Observatory, Experiment Lab и полная старая диагностика Rust.
5. **История**: недавние Game Sessions из SQLite (до восьми; без ложного «активна» для незавершённых), ссылки на прежнюю Rust/SQLite историю и CSV/ZIP, эксперименты, MP4 и историю версий приложений.
6. **Настройки**: Application Center, Smart Profiles/JSON импорт-экспорт, Toolbox, явные состояния спецдоступов Android, официальные Usage Access Settings. Никакого тихого запроса привилегий.

### Границы

- Нет глобального списка всех установленных пакетов: Android `MAIN/LAUNCHER` показывает только видимые пользовательские приложения; не используется `QUERY_ALL_PACKAGES`.
- «Игры» используют системную категорию; если конкретная игра не обозначена Android как игра, переключите **«Показать все приложения»** и добавьте её в избранное.
- Переключение вкладок не останавливает запущенные в другом контексте foreground-сервисы и не запускает новые. Запись экрана всё ещё требует отдельного MediaProjection-разрешения Android для каждого сеанса.
- Пока нет автоматического применения и отката игровых пресетов — это отдельный следующий функциональный патч. Навигация лишь объединяет ранее существующие экраны.
- Новая навигация основана на стабильных AndroidX Navigation Fragment **2.10.2** и Lifecycle ViewModel/LiveData **2.11.1**; без Compose compiler. Источник: Android Developers Navigation/Lifecycle release notes.
- Обновление **alpha.25 → alpha.26** сохраняет ID `dev.lolokeksu.legioncontrol`, постоянный подписывающий ключ, прежнюю SQLite v12 со всеми данными, JNI ABI и манифестный allowlist разрешений.

## Проверка

```bash
python3 scripts/check_alpha26_mvvm_navigation.py
gradle --no-daemon testDebugUnitTest
bash scripts/build-native.sh
gradle --no-daemon assembleDebug lintDebug
```

Кроме новой регрессии запускаются **все старые** статические/SQLite/Kotlin/Rust/подписные проверки GitHub Actions. В PR должны пройти обе ветки — тестовая и подписанная на main.

**Ручная проверка на Lenovo/Motorola Android 13:** установка подписанной alpha.26 поверх подписанной alpha.25 и сохранение SQLite; пять вкладок и back; возврат из старого MainActivity/Rust, Game Toolbox, Capture Studio; search и избранное с сохранением заметки; запуск доступной игры, возврат и состояние; поворот/уход домой/убийство процесса; empty/permission denied. **CI не подтверждает фактический показ интерфейса на физическом телефоне.**

План и требования: [ALPHA26-MVVM-NAVIGATION-PLAN.md](ALPHA26-MVVM-NAVIGATION-PLAN.md).
