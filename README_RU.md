![banner](fastlane/metadata/android/en-US/images/featureGraphic.png)

# mpvExtended
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/marlboro-advance/mpvex.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/marlboro-advance/mpvex/releases/latest)
[![GitHub all releases](https://img.shields.io/github/downloads/marlboro-advance/mpvex/total?logo=github&cacheSeconds=3600)](https://github.com/marlboro-advance/mpvex/releases/latest)

[English](README.md) | **Русский**

**mpvExtended — это форк [mpv-android](https://github.com/mpv-android/mpv-android), построенный на библиотеке libmpv. 
Цель проекта — объединить мощные возможности mpv с простым в использовании интерфейсом и дополнительными функциями.**

- Простой и удобный интерфейс
- Выразительный дизайн Material3
- Расширенная настройка и скрипты
- Улучшенные возможности воспроизведения
- Картинка в картинке (PiP)
- Фоновое воспроизведение
- Качественная отрисовка
- Сетевое воспроизведение потоков
- Управление файлами
- Полностью бесплатно и с открытым исходным кодом, без рекламы и избыточных разрешений
- Выбор медиафайлов с режимами дерева и папок
- поддержка внешних субтитров
- Жест зумирования
- Поддержка внешних аудиофайлов
- Функция поиска
- Поддержка SMB/FTP/WebDAV
- Поддержка управления пользовательскими списками воспроизведения

**Этот проект всё ещё находится в разработке, и ожидается наличие ошибок. Пожалуйста, сообщайте о любых найденных вами ошибках в разделе [Issues](https://github.com/marlboro-advance/mpvEx/issues).**

---

## Установка

### Стабильный релиз
Скачайте последнюю стабильную версию со [страницы релизов GitHub](https://github.com/marlboro-advance/mpvEx/releases).

[![Download Release](https://img.shields.io/badge/Download-Release-blue?style=for-the-badge)](https://github.com/marlboro-advance/mpvEx/releases)

Или вы можете получить стабильные релизы здесь:

[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="50" alt="Get it at IzzyOnDroid">](https://apt.izzysoft.de/packages/app.marlboroadvance.mpvex)

### Превью-сборки
Только для тестирования

[![Download Preview Builds](https://img.shields.io/badge/Download-Preview%20Builds-red?style=for-the-badge)](https://marlboro-advance.github.io/mpvEx/)

---

## Демонстрация
<div class="image-row" align="center">
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/player.png" width="98%" />
</div>

<div class="image-row" align="center" justify-content="space-between">
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/folderscreen.png" width="23.5%"/>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/videoscreen.png" width="23.5%"/>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/about.png" width="23.5%"/>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/pip.png" width="23.5%"/>
</div>

<div class="image-row" align="center">
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/framenavigation.png" width="48.5%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/chapters.png" width="48.5%" />
</div>

---

## Сборка

### Необходимые компоненты

- JDK 17
- Android SDK с build tools 34.0.0+
- Git (для информации о версии в сборках)

### Варианты APK

Приложение генерирует несколько вариантов APK для разных архитектур процессоров:

- **universal**: Работает на всех устройствах (больший размер)
- **arm64-v8a**: Современные 64-битные ARM-устройства (рекомендуется для большинства пользователей)
- **armeabi-v7a**: Старые 32-битные ARM-устройства
- **x86**: Устройства Intel/AMD 32-бит
- **x86_64**: Устройства Intel/AMD 64-бит

---

## Релизы

### Настройка подписи релиза

Чтобы включить автоматическую подпись релизных сборок в GitHub Actions, необходимо настроить 
следующие секреты в вашем репозитории GitHub:

1. Перейдите в ваш репозиторий на GitHub
2. Откройте **Settings** → **Secrets and variables** → **Actions**
3. Добавьте следующие репозиторные секреты:

| Имя секрета                | Описание                                              |
|----------------------------|-------------------------------------------------------|
| `SIGNING_KEYSTORE`         | Keystore-файл в кодировке Base64 (`.jks` или `.keystore`) |
| `SIGNING_KEY_ALIAS`        | Алиас, использованный при создании keystore           |
| `SIGNING_STORE_PASSWORD`   | Пароль для файла keystore                             |
| `KEY_PASSWORD`             | Пароль для ключа (может совпадать с паролем keystore) |

#### Кодирование вашего keystore

Для кодирования файла keystore в Base64:

**Linux/macOS:**

```bash
base64 -i your-keystore.jks | tr -d '\n' > keystore.txt
```

**Windows (PowerShell):**

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("your-keystore.jks")) | Out-File -FilePath keystore.txt -NoNewline
```

Скопируйте содержимое `keystore.txt` и вставьте его как значение секрета `SIGNING_KEYSTORE`.

### Создание релиза

1. Обновите `versionCode` и `versionName` в `app/build.gradle.kts`
2. Сохраните изменения (commit)
3. Создайте и отправьте тег:
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```
4. GitHub Actions автоматически соберёт, подпишет и создаст черновик релиза

### Создание превью-релиза

1. Создайте и отправьте превью-тег:
   ```bash
   git tag -a v1.0.0-preview.1 -m "Preview release"
   git push origin v1.0.0-preview.1
   ```
2. GitHub Actions автоматически создаст превью-релиз (pre-release)

---

## Благодарности

- [mpv-android](https://github.com/mpv-android)
- [mpvKt](https://github.com/abdallahmehiz/mpvKt)
- [Next player](https://github.com/anilbeesetti/nextplayer)
- [Gramophone](https://github.com/FoedusProgramme/Gramophone)

---

## Поддержите проект <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Smilies/Heart%20with%20Ribbon.png" alt="Heart with Ribbon" width="25" height="25" />

Если вы находите mpvExtended полезным, рассмотрите возможность поддержки разработки:

[![UPI](https://img.shields.io/badge/UPI-aadiinarvekar@upi-blue?style=for-the-badge&logo=google-pay&logoColor=white)](upi://pay?pa=aadiinarvekar@upi)

---
## История звёзд <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Travel%20and%20places/Star.png" alt="Star" width="25" height="25" />

<a href="https://www.star-history.com/#marlboro-advance/mpvEx&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=marlboro-advance/mpvEx&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=marlboro-advance/mpvEx&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=marlboro-advance/mpvEx&type=date&legend=top-left" />
 </picture>
</a>