# Legion Control

Новый самостоятельный проект для Lenovo Legion Y70. Первая стадия — **реальный backend**, интерфейс развиваем позже.

## Стек
- **Kotlin/Android:** получение данных через публичные Android API, разрешения и жизненный цикл.
- **Rust:** проверка показаний и расчёт изменений батареи в ограниченном 64-сэмпловом буфере.
- **JNI:** связь между Kotlin и Rust для Android ARM64.

Текущий backend не отправляет данные на сервер, не меняет параметры телефона и не требует Shizuku или Root. Это отдельный проект: исходный `Lenovo-Y70` оставлен без изменений.

См. `rust-core/`, `android-core/`, `docs/BACKEND.md`. Следующие шаги: Rust unit tests в CI, Android JNI сборка, затем легковесный Kotlin-интерфейс. Никаких заявлений об улучшении FPS, сенсорного отклика или Bypass.

Версия backend: `0.1.0-alpha.1`.

## Project Workflow Standard v1.0

Для работы над проектом действует адаптированный стандарт из предоставленного владельцем архива:
[правила разработчика](AGENTS.md), [карточка проекта](PROJECT.md), [уровни L1–L5](TESTING-LEVELS.md).
Оригинал комплекта (шаблоны планов, аудита и релиза) сохранён в
[workflow-standard/](workflow-standard/README.md). Интеграция и источники:
[docs/WORKFLOW-ADOPTION.md](docs/WORKFLOW-ADOPTION.md).

Приоритет — **проверяемый Kotlin + Rust backend**, затем UI.
Документация не отменяет прежние Rust unit tests, Android/JNI CI gates и запрет на неподтверждённые системные переключатели.

Быстрая проверка внедрения:

```bash
python3 workflow-standard/tools/check_standard.py workflow-standard
python3 scripts/check_workflow_integration.py
```
