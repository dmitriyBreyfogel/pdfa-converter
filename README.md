# PDF/A Converter

Конвертирует PDF, DOC, DOCX, PPTX, XLS и XLSX в PDF/A-1b, PDF/A-2b или PDF/A-3b. Готовый PDF/A проверяется через veraPDF перед отправкой пользователю.

## Запуск

Из корня проекта выполните:

```bash
docker compose up --build
```

Приложение откроется на <http://localhost:3000>. Остановить его можно командой `docker compose down`.

Первый запуск скачивает зависимости и собирает оба образа, поэтому может занять несколько минут. Для работы Docker требуется доступ в интернет при сборке.

## API

`POST /api/convert` принимает `multipart/form-data` с полями `file` и `target`. Допустимые значения `target`: `PDF_A_1b`, `PDF_A_2b`, `PDF_A_3b`. Успешный ответ содержит PDF с заголовком `Content-Disposition: attachment`; при ошибке возвращается JSON с `code`, `message` и `details`. Максимальный размер входного файла — 50 МБ.

В Compose API доступен через адрес приложения: `http://localhost:3000/api/convert`.

Для локальной разработки фронтенда: запустите backend на порту 8080, затем в `frontend` выполните `npm ci` и `npm run dev`. Vite перенаправит `/api` на backend.
