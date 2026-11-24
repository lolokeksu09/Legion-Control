# Alpha 48 — реестр приёмки после комплексного аудита

Источник: [FULL-AUDIT-ALPHA48.md](FULL-AUDIT-ALPHA48.md). Объект: e537c8a0ab41cf0618ea6c1277258c9743eabdee / PR #59. Этот файл является рабочим реестром, не свидетельством исполнения.

| ID | Приоритет | Действие | Приёмка | Состояние |
|---|---|---|---|---|
| A48-01 | P1 | Exact UI smoke и выпуск | кандидат + main PASS, permanent cert | В работе: CI |
| A48-02 | P1 | Покрытие Preflight presets в backup / явное ограничение | v1 compatibility, roundtrip, восстановление | Открыто |
| A48-03 | P2 | SAF export request lifetime | rotation, cancel, I/O failure, contents | Открыто |
| A48-04 | P2 | Канонический owner-tab | Games/History/Tools selected на всех входах | Открыто |
| A48-05 | P2 | Stable finder anchors и availability | 79 маршрутов, disabled/unknown, no auto action | Открыто |
| A48-06 | P2 | Settings/recovery независимы от HubSnapshot | injected repository failure, recovery | Открыто |
| A48-07 | P2 | Preflight freshness UX | 60s expiry, resume, stale share, отказ launch | Открыто |
| A48-08 | P2 | Metric quality/units | missing, gap, endpoint, charging, A/B | Открыто |
| A48-09 | P2 | CPU/RAM/SQLite/energy profiling | workload, p50/p95, plan, no fake numbers | Не выполнено |
| A48-10 | P2 | Accessibility и UI | 200%/TalkBack/landscape/focus/contrast | Не выполнено |
| A48-11 | P2 | Media/permissions/ROM | physical Y70 + Android API coverage | Не выполнено |
| A48-12 | P2 | Backup/network/rollback runtime | process kill, DNS cancellation, undo conflict | Не выполнено |
| A48-13 | P2 | Supply-chain hardening | pinned actions/deps, secrets guard, provenance | Открыто |
| A48-14 | P3 | Version/UI/export cohesion | single version contract, scoped refactor | Открыто |

## Порядок
Сначала P1 и точный CI, затем исправления P2 по одному проверяемому изменению, затем hardware/доступность/профилирование, затем инфраструктурное упрощение. SQLite v12, package ID, certificate и существующие permissions не меняются без отдельного решения владельца. Не закрывать задачи только по факту написания теста.

## Ручная приёмка устройства
Записать модель/ROM/API, APK version/code и SHA-256, сертификат; проверить обновление поверх Alpha 47 без очистки данных. Проверить Home, пять вкладок и возврат; поиск/reveal без auto execution; два сеанса одной игры с явным START/STOP и меткой; Preflight 120 Hz vs реальные Hz и power saver, смена профиля, запуск только после подтверждения; Monitor 2/5/10 с, stop/background, baseline и CSV; Compare 5/10 мин и неполное окно; Library фильтр/пагинация/архив одной записи. SAF cancel/rotate/провайдер отказ. Capture/Replay/PNG/MP4/AAC consent allow/deny/revoke, overlay/DND/write settings и возврат, BT/USB controller, аудио/вибрация, offline/VPN, thermal/датчики. 200% fontScale, TalkBack, landscape, UI screenshot evidence. Не проводить destructive restore на пользовательской базе: disposable тестовая установка. Каждое действие: expected, actual, PASS/FAIL/NOT RUN, evidence.
