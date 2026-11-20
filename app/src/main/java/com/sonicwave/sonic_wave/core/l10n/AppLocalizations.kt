package com.sonicwave.sonic_wave.core.l10n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

val LocalAppStrings = staticCompositionLocalOf { AppLocalizations.forLanguage("en") }

object AppLocalizations {
    val supportedCodes = listOf("en", "ru", "uk", "de", "es", "fr", "pl")

    val languageDisplayNames = mapOf(
        "system" to "Auto (System)",
        "en" to "English",
        "ru" to "Русский",
        "uk" to "Українська",
        "de" to "Deutsch",
        "es" to "Español",
        "fr" to "Français",
        "pl" to "Polski"
    )

    private val _localeModeFlow = MutableStateFlow("system")
    val localeModeFlow: StateFlow<String> = _localeModeFlow

    fun setLocaleMode(mode: String) {
        _localeModeFlow.value = mode
    }

    fun resolveActiveLanguageCode(mode: String = _localeModeFlow.value): String {
        if (mode != "system" && mode in supportedCodes) return mode
        val sys = Locale.getDefault().language.lowercase()
        return if (sys in supportedCodes) sys else "en"
    }

    fun forLanguage(code: String): AppStrings {
        val resolved = if (code == "system") resolveActiveLanguageCode("system") else code
        return when (resolved) {
            "ru" -> AppStrings(ruMap, enMap)
            "uk" -> AppStrings(ukMap, ruMap, enMap)
            else -> AppStrings(enMap)
        }
    }

    @Composable
    @ReadOnlyComposable
    fun current(): AppStrings = LocalAppStrings.current

    private val enMap = mapOf(
        // Nav
        "nav_studio" to "Studio",
        "nav_library" to "Library",
        "nav_search" to "Search",
        "nav_settings" to "Settings",

        // Welcome
        "welcome_studio_tag" to "OBSIDIAN ACOUSTIC STUDIO",
        "welcome_title" to "SonicWave Studio",
        "welcome_subtitle" to "Hi-Res FLAC/ALAC streaming, offline vault, ReplayGain & 10-band studio DSP for Navidrome, OpenSubsonic, and Jellyfin.",
        "welcome_badge_hires" to "FLAC / Hi-Res",
        "welcome_badge_local" to "Local Folders",
        "welcome_badge_dsp" to "DSP & ReplayGain",
        "welcome_connect_server" to "Connect Media Server",
        "welcome_pick_local" to "Pick Local Audio Folder",
        "welcome_import_settings" to "Import Configuration",
        "welcome_back" to "Back",
        "welcome_setup_title" to "Connect Server Node",
        "welcome_qr_import" to "Import via QR / Link",
        "welcome_protocol_label" to "SERVER PROTOCOL",
        "welcome_name_label" to "SERVER NAME",
        "welcome_name_hint" to "Home Navidrome",
        "welcome_url_label" to "PRIMARY URL (WAN / DOMAIN)",
        "welcome_url_hint" to "https://music.example.com",
        "welcome_lan_label" to "LOCAL LAN URL (OPTIONAL)",
        "welcome_lan_hint" to "http://192.168.1.50:4533",
        "welcome_user_label" to "USERNAME",
        "welcome_pass_label" to "PASSWORD OR API TOKEN",
        "welcome_ssl_title" to "Allow Self-Signed SSL (TOFU Pinning)",
        "welcome_ssl_sub" to "Trusts home server certificates using SHA-256 fingerprint pinning",
        "welcome_test_btn" to "Test Connection",
        "welcome_enter_btn" to "Connect & Enter",
        "welcome_ping_ok" to "Connection verified. Server is online.",
        "welcome_ping_fail" to "Connection failed. Check URL, credentials, or SSL settings.",
        "welcome_qr_paste_title" to "Paste QR Payload or Link",
        "welcome_qr_paste_hint" to "sonicwave://server?data=...",
        "welcome_qr_apply" to "Decode & Apply",
        "welcome_qr_invalid" to "Invalid SonicWave QR payload",

        // Home
        "home_title" to "SonicWave Studio",
        "home_subtitle" to "ACOUSTIC CONSOLE",
        "home_active_node" to "ACTIVE NODE",
        "home_instant_mix" to "Instant Analog Mix",
        "home_local_library" to "LOCAL DEVICE LIBRARY",
        "home_vinyl_showcase" to "VINYL SHOWCASE // NEWEST",
        "home_heavy_rotation" to "HEAVY ROTATION // FREQUENT",
        "home_quick_mix" to "DIRECT STREAM // QUICK MIX",
        "home_no_server" to "Local Mode Only",
        "home_retry" to "Retry Sync",
        "home_empty_albums" to "No albums loaded yet. Pull to refresh or check server connection.",
        "home_qr_share_title" to "Quick Server QR Share",
        "home_include_password" to "Include Password in QR",
        "home_copy_link" to "Copy Link",
        "home_tracks_count" to "{count} TRK",

        // Library
        "library_title" to "Media Vault",
        "library_subtitle" to "COLLECTION & OFFLINE",
        "sec_playlists" to "Playlists",
        "sec_albums" to "Albums",
        "sec_tracks" to "Tracks",
        "sec_artists" to "Artists",
        "sec_genres" to "Genres",
        "sec_folders" to "Folders",
        "sec_podcasts" to "Podcasts",
        "sec_audiobooks" to "Audiobooks",
        "sec_local" to "Local Audio",
        "lib_empty" to "Nothing here yet",
        "lib_up_level" to "Up One Level",
        "lib_root_dir" to "Root",
        "lib_add_folder" to "Pick Folder",
        "lib_add_path" to "Add by Path",
        "lib_rescan" to "Rescan",
        "lib_play_all" to "Play All",
        "lib_shuffle_all" to "Shuffle",
        "lib_downloading_banner" to "Downloading {count} item(s)...",
        "lib_lru_clean" to "Clean Cache",
        "lib_download_album" to "Download Album",
        "lib_play_album" to "Play Album",

        // Search
        "search_title" to "Acoustic Locator",
        "search_subtitle" to "GLOBAL INDEX SEARCH",
        "search_hint" to "Search tracks, albums, artists...",
        "search_filter_all" to "All",
        "search_filter_music" to "Music",
        "search_filter_podcasts" to "Podcasts",
        "search_filter_audiobooks" to "Audiobooks",
        "search_filter_2020" to "2020+",
        "search_matched_albums" to "MATCHED ALBUMS",
        "search_matched_tracks" to "MATCHED TRACKS",
        "search_empty_prompt" to "Type to search your media server and local library.",
        "search_no_results" to "No matching tracks or albums found.",

        // Player
        "player_lyrics" to "Lyrics",
        "player_artwork" to "Artwork",
        "player_queue" to "Queue",
        "player_equalizer" to "Equalizer",
        "player_sleep_timer" to "Sleep Timer",
        "player_offline_cache" to "Offline Cache",
        "player_no_lyrics" to "No synchronized lyrics available for this track.",
        "player_repeat_off" to "Repeat Off",
        "player_repeat_all" to "Repeat All",
        "player_repeat_one" to "Repeat One",
        "player_sleep_off" to "Off",
        "player_sleep_15" to "15 min",
        "player_sleep_30" to "30 min",
        "player_sleep_45" to "45 min",
        "player_sleep_60" to "60 min",
        "player_stream_pcm" to "PCM STREAM // STUDIO",
        "player_stream_offline" to "OFFLINE CACHE // DIRECT",
        "player_eq_enable" to "Enable 10-Band Equalizer",
        "player_bass_boost" to "Bass Boost",
        "player_queue_empty" to "Queue is empty",
        "player_now_playing" to "NOW PLAYING",

        // Settings / Servers
        "settings_title" to "Studio Rack",
        "settings_subtitle" to "SOURCES, DSP & STORAGE",
        "cat_all" to "All",
        "cat_sources" to "Sources",
        "cat_audio" to "Audio",
        "cat_downloads" to "Downloads",
        "cat_network" to "Network",
        "cat_interface" to "Interface",
        "cat_backup" to "Backup",
        "cat_about" to "About",
        "srv_sec_sources" to "01 // MEDIA SOURCES",
        "srv_sec_audio" to "02 // AUDIO ENGINE & DSP",
        "srv_sec_downloads" to "03 // OFFLINE DOWNLOADS & CACHE",
        "srv_sec_network" to "04 // STREAMING & CODECS",
        "srv_sec_interface" to "05 // INTERFACE & PLAYBACK",
        "srv_sec_backup" to "06 // CONFIGURATION BACKUP",
        "srv_sec_about" to "07 // ABOUT & LICENSES",
        "srv_vinyl_waves" to "Vinyl Rhythm Waves",
        "srv_vinyl_waves_sub" to "Soft ambient pulse rings around the vinyl disc",
        "about_tagline" to "Hi-Res OpenSubsonic & Jellyfin Audio Console",
        "about_version" to "VERSION",
        "about_license_label" to "OPEN-SOURCE LICENSE",
        "about_license_name" to "MIT License (OSI Approved)",
        "about_privacy_note" to "Zero telemetry • No ads • Offline-first local vault",
        "about_view_license" to "View MIT License",
        "about_third_party" to "Open-Source Libraries",
        "about_third_party_sub" to "Third-party components & Apache-2.0 notices",
        "about_easter_hint" to "Signal calibration: {left} more...",
        "about_easter_title" to "SECRET LAB // ANALOG OSCILLOSCOPE",
        "about_easter_sub" to "Drag across the CRT scope to modulate frequency & harmonic phase",
        "srv_add_server" to "Add Server",
        "srv_edit_server" to "Edit Server",
        "srv_ping" to "Test",
        "srv_activate" to "Activate",
        "srv_active_badge" to "ACTIVE",
        "srv_delete_title" to "Delete Server?",
        "srv_delete_msg" to "This will remove the server profile and its offline cached tracks.",
        "srv_cert_changed_title" to "SSL Certificate Changed",
        "srv_cert_changed_msg" to "The server certificate fingerprint changed. Trust the new certificate?",
        "srv_trust_cert" to "Trust New Cert",
        "srv_auto_cache" to "Auto-Cache Upcoming Tracks",
        "srv_wifi_only" to "Wi-Fi Only Downloads",
        "srv_cache_limit" to "Auto-Cache Limit",
        "srv_clear_auto" to "Clear Auto Cache",
        "srv_clear_pinned" to "Clear All Pinned",
        "srv_clear_pinned_confirm" to "Delete all pinned offline downloads?",
        "srv_replaygain" to "ReplayGain Normalization",
        "srv_album_gain" to "Prefer Album Gain",
        "srv_preamp" to "ReplayGain Preamp",
        "srv_crossfade" to "Crossfade Duration",
        "srv_playback_speed" to "Playback Speed",
        "srv_wifi_codec" to "Wi-Fi Streaming Codec",
        "srv_mobile_codec" to "Mobile Network Codec",
        "srv_mobile_bitrate" to "Mobile Max Bitrate",
        "srv_scrobble" to "Scrobble Playback to Server",
        "srv_light_theme" to "Warm Alabaster (Light Theme)",
        "srv_dynamic_colors" to "Dynamic Artwork Colors",
        "srv_default_repeat" to "Default Repeat Mode",
        "srv_language" to "Language",
        "srv_export_btn" to "Export Settings JSON",
        "srv_import_btn" to "Import Settings JSON",
        "srv_storage_pinned" to "Pinned: {size}",
        "srv_storage_auto" to "Auto Cache: {size}",
        "srv_retry_failed" to "Retry Failed",
        "srv_filter_all" to "All",
        "srv_filter_pinned" to "Pinned",
        "srv_filter_auto" to "Auto",

        // Transfer & Common
        "transfer_export_title" to "Export Configuration",
        "transfer_include_passwords" to "Include Server Passwords",
        "transfer_save_file" to "Save to File",
        "transfer_copy_json" to "Copy JSON",
        "transfer_import_title" to "Import Configuration",
        "transfer_pick_file" to "Pick JSON File",
        "transfer_apply_json" to "Apply Configuration",
        "transfer_path_title" to "Add Local Folder by Path",
        "transfer_path_hint" to "/storage/emulated/0/Music",
        "common_cancel" to "Cancel",
        "common_confirm" to "Confirm",
        "common_save" to "Save",
        "common_delete" to "Delete",
        "common_close" to "Close",
        "common_copied" to "Copied to clipboard",
        "common_saved" to "Saved",
        "common_error" to "Error",
        "common_tracks" to "tracks"
    )

    private val ruMap = mapOf(
        // Nav
        "nav_studio" to "Студия",
        "nav_library" to "Медиатека",
        "nav_search" to "Поиск",
        "nav_settings" to "Настройки",

        // Welcome
        "welcome_studio_tag" to "OBSIDIAN ACOUSTIC STUDIO",
        "welcome_title" to "SonicWave Studio",
        "welcome_subtitle" to "Hi-Res стриминг FLAC/ALAC, офлайн-кэш, ReplayGain и 10-полосный DSP для Navidrome, OpenSubsonic и Jellyfin.",
        "welcome_badge_hires" to "FLAC / Hi-Res",
        "welcome_badge_local" to "Локальные папки",
        "welcome_badge_dsp" to "DSP и ReplayGain",
        "welcome_connect_server" to "Подключить медиасервер",
        "welcome_pick_local" to "Выбрать локальную папку",
        "welcome_import_settings" to "Импортировать настройки",
        "welcome_back" to "Назад",
        "welcome_setup_title" to "Подключение сервера",
        "welcome_qr_import" to "Импорт по QR / ссылке",
        "welcome_protocol_label" to "ПРОТОКОЛ СЕРВЕРА",
        "welcome_name_label" to "НАЗВАНИЕ СЕРВЕРА",
        "welcome_name_hint" to "Домашний Navidrome",
        "welcome_url_label" to "ОСНОВНОЙ URL (WAN / ДОМЕН)",
        "welcome_url_hint" to "https://music.example.com",
        "welcome_lan_label" to "ЛОКАЛЬНЫЙ LAN URL (ОПЦИОНАЛЬНО)",
        "welcome_lan_hint" to "http://192.168.1.50:4533",
        "welcome_user_label" to "ИМЯ ПОЛЬЗОВАТЕЛЯ",
        "welcome_pass_label" to "ПАРОЛЬ ИЛИ API-ТОКЕН",
        "welcome_ssl_title" to "Разрешить самоподписанный SSL (TOFU)",
        "welcome_ssl_sub" to "Доверие сертификатам домашнего сервера по SHA-256 отпечатку",
        "welcome_test_btn" to "Проверить связь",
        "welcome_enter_btn" to "Подключить и войти",
        "welcome_ping_ok" to "Связь установлена. Сервер доступен.",
        "welcome_ping_fail" to "Ошибка подключения. Проверьте URL, логин или SSL.",
        "welcome_qr_paste_title" to "Вставьте QR-данные или ссылку",
        "welcome_qr_paste_hint" to "sonicwave://server?data=...",
        "welcome_qr_apply" to "Расшифровать и применить",
        "welcome_qr_invalid" to "Некорректные данные QR SonicWave",

        // Home
        "home_title" to "SonicWave Studio",
        "home_subtitle" to "АКУСТИЧЕСКАЯ КОНСОЛЬ",
        "home_active_node" to "АКТИВНЫЙ УЗЕЛ",
        "home_instant_mix" to "Мгновенный аналоговый микс",
        "home_local_library" to "ЛОКАЛЬНАЯ БИБЛИОТЕКА",
        "home_vinyl_showcase" to "ВИТРИНА ВИНИЛА // НОВИНКИ",
        "home_heavy_rotation" to "ЧАСТО СЛУШАЕМОЕ // РОТАЦИЯ",
        "home_quick_mix" to "ПРЯМОЙ ПОТОК // БЫСТРЫЙ МИКС",
        "home_no_server" to "Локальный режим",
        "home_retry" to "Повторить",
        "home_empty_albums" to "Альбомы ещё не загружены. Потяните для обновления или проверьте сервер.",
        "home_qr_share_title" to "Быстрый QR-код сервера",
        "home_include_password" to "Включить пароль в QR",
        "home_copy_link" to "Скопировать ссылку",
        "home_tracks_count" to "{count} ТР",

        // Library
        "library_title" to "Медиатека",
        "library_subtitle" to "КОЛЛЕКЦИЯ И ОФЛАЙН",
        "sec_playlists" to "Плейлисты",
        "sec_albums" to "Альбомы",
        "sec_tracks" to "Треки",
        "sec_artists" to "Исполнители",
        "sec_genres" to "Жанры",
        "sec_folders" to "Папки",
        "sec_podcasts" to "Подкасты",
        "sec_audiobooks" to "Аудиокниги",
        "sec_local" to "Локальные",
        "lib_empty" to "Здесь пока пусто",
        "lib_up_level" to "На уровень выше",
        "lib_root_dir" to "Корень",
        "lib_add_folder" to "Выбрать папку",
        "lib_add_path" to "Добавить по пути",
        "lib_rescan" to "Сканировать",
        "lib_play_all" to "Играть всё",
        "lib_shuffle_all" to "Перемешать",
        "lib_downloading_banner" to "Загрузка: {count} файл(ов)...",
        "lib_lru_clean" to "Очистить кэш",
        "lib_download_album" to "Скачать альбом",
        "lib_play_album" to "Играть альбом",

        // Search
        "search_title" to "Акустический поиск",
        "search_subtitle" to "ГЛОБАЛЬНЫЙ ИНДЕКС",
        "search_hint" to "Поиск треков, альбомов, артистов...",
        "search_filter_all" to "Всё",
        "search_filter_music" to "Музыка",
        "search_filter_podcasts" to "Подкасты",
        "search_filter_audiobooks" to "Аудиокниги",
        "search_filter_2020" to "2020+",
        "search_matched_albums" to "НАЙДЕННЫЕ АЛЬБОМЫ",
        "search_matched_tracks" to "НАЙДЕННЫЕ ТРЕКИ",
        "search_empty_prompt" to "Введите запрос для поиска по медиасерверу.",
        "search_no_results" to "Ничего не найдено.",

        // Player
        "player_lyrics" to "Текст",
        "player_artwork" to "Обложка",
        "player_queue" to "Очередь",
        "player_equalizer" to "Эквалайзер",
        "player_sleep_timer" to "Таймер сна",
        "player_offline_cache" to "В офлайн",
        "player_no_lyrics" to "Синхронизированный текст для этого трека не найден.",
        "player_repeat_off" to "Без повтора",
        "player_repeat_all" to "Повтор очереди",
        "player_repeat_one" to "Повтор трека",
        "player_sleep_off" to "Выкл",
        "player_sleep_15" to "15 мин",
        "player_sleep_30" to "30 мин",
        "player_sleep_45" to "45 мин",
        "player_sleep_60" to "60 мин",
        "player_stream_pcm" to "PCM ПОТОК // СТУДИЯ",
        "player_stream_offline" to "ОФЛАЙН КЭШ // ПРЯМОЙ",
        "player_eq_enable" to "Включить 10-полосный эквалайзер",
        "player_bass_boost" to "Усиление басов (Bass Boost)",
        "player_queue_empty" to "Очередь пуста",
        "player_now_playing" to "СЕЙЧАС ИГРАЕТ",

        // Settings / Servers
        "settings_title" to "Студийная стойка",
        "settings_subtitle" to "ИСТОЧНИКИ, DSP И ПАМЯТЬ",
        "cat_all" to "Все",
        "cat_sources" to "Источники",
        "cat_audio" to "Аудио",
        "cat_downloads" to "Загрузки",
        "cat_network" to "Сеть",
        "cat_interface" to "Интерфейс",
        "cat_backup" to "Бэкап",
        "cat_about" to "О приложении",
        "srv_sec_sources" to "01 // МЕДИА-ИСТОЧНИКИ",
        "srv_sec_audio" to "02 // АУДИОДВИЖОК И DSP",
        "srv_sec_downloads" to "03 // ОФЛАЙН-ЗАГРУЗКИ И КЭШ",
        "srv_sec_network" to "04 // СТРИМИНГ И КОДЕКИ",
        "srv_sec_interface" to "05 // ИНТЕРФЕЙС И ВОСПРОИЗВЕДЕНИЕ",
        "srv_sec_backup" to "06 // РЕЗЕРВНОЕ КОПИРОВАНИЕ",
        "srv_sec_about" to "07 // О ПРИЛОЖЕНИИ И ЛИЦЕНЗИИ",
        "srv_vinyl_waves" to "Ритм-волны от винила",
        "srv_vinyl_waves_sub" to "Мягкие пульсирующие волны вокруг диска в такт треку",
        "about_tagline" to "Hi-Res аудиоконсоль для OpenSubsonic и Jellyfin",
        "about_version" to "ВЕРСИЯ",
        "about_license_label" to "ОТКРЫТАЯ ЛИЦЕНЗИЯ",
        "about_license_name" to "Лицензия MIT (Open Source)",
        "about_privacy_note" to "Без телеметрии • Без рекламы • Локальное хранение",
        "about_view_license" to "Текст лицензии MIT",
        "about_third_party" to "Открытые библиотеки",
        "about_third_party_sub" to "Сторонние компоненты и лицензии Apache-2.0",
        "about_easter_hint" to "Калибровка сигнала: осталось {left}...",
        "about_easter_title" to "СЕКРЕТНАЯ ЛАБОРАТОРИЯ // ОСЦИЛЛОГРАФ",
        "about_easter_sub" to "Водите пальцем по экрану осциллографа для модуляции волны",
        "srv_add_server" to "Добавить сервер",
        "srv_edit_server" to "Редактировать сервер",
        "srv_ping" to "Тест",
        "srv_activate" to "Выбрать",
        "srv_active_badge" to "АКТИВЕН",
        "srv_delete_title" to "Удалить сервер?",
        "srv_delete_msg" to "Профиль сервера и его кэшированные треки будут удалены.",
        "srv_cert_changed_title" to "SSL-сертификат изменился",
        "srv_cert_changed_msg" to "Отпечаток сертификата сервера изменился. Доверять новому сертификату?",
        "srv_trust_cert" to "Доверять",
        "srv_auto_cache" to "Предкэшировать следующие треки",
        "srv_wifi_only" to "Загрузки только по Wi-Fi",
        "srv_cache_limit" to "Лимит авто-кэша",
        "srv_clear_auto" to "Очистить авто-кэш",
        "srv_clear_pinned" to "Удалить все скачанные",
        "srv_clear_pinned_confirm" to "Удалить все закреплённые офлайн-треки?",
        "srv_replaygain" to "Нормализация ReplayGain",
        "srv_album_gain" to "Предпочитать Album Gain",
        "srv_preamp" to "Предусиление ReplayGain",
        "srv_crossfade" to "Плавный переход (Crossfade)",
        "srv_playback_speed" to "Скорость воспроизведения",
        "srv_wifi_codec" to "Кодек для Wi-Fi",
        "srv_mobile_codec" to "Кодек для мобильной сети",
        "srv_mobile_bitrate" to "Макс. битрейт мобильной сети",
        "srv_scrobble" to "Отправлять скробблинг на сервер",
        "srv_light_theme" to "Светлая тема (Warm Alabaster)",
        "srv_dynamic_colors" to "Динамические цвета обложки",
        "srv_default_repeat" to "Режим повтора по умолчанию",
        "srv_language" to "Язык интерфейса",
        "srv_export_btn" to "Экспорт настроек JSON",
        "srv_import_btn" to "Импорт настроек JSON",
        "srv_storage_pinned" to "Скачано: {size}",
        "srv_storage_auto" to "Авто-кэш: {size}",
        "srv_retry_failed" to "Повторить ошибки",
        "srv_filter_all" to "Все",
        "srv_filter_pinned" to "Скачанные",
        "srv_filter_auto" to "Авто-кэш",

        // Transfer & Common
        "transfer_export_title" to "Экспорт конфигурации",
        "transfer_include_passwords" to "Включить пароли серверов",
        "transfer_save_file" to "Сохранить в файл",
        "transfer_copy_json" to "Копировать JSON",
        "transfer_import_title" to "Импорт конфигурации",
        "transfer_pick_file" to "Выбрать JSON-файл",
        "transfer_apply_json" to "Применить конфигурацию",
        "transfer_path_title" to "Добавить папку по пути",
        "transfer_path_hint" to "/storage/emulated/0/Music",
        "common_cancel" to "Отмена",
        "common_confirm" to "Подтвердить",
        "common_save" to "Сохранить",
        "common_delete" to "Удалить",
        "common_close" to "Закрыть",
        "common_copied" to "Скопировано в буфер",
        "common_saved" to "Сохранено",
        "common_error" to "Ошибка",
        "common_tracks" to "треков"
    )

    private val ukMap = mapOf(
        "nav_studio" to "Студія",
        "nav_library" to "Медіатека",
        "nav_search" to "Пошук",
        "nav_settings" to "Налаштування",
        "common_cancel" to "Скасувати",
        "common_confirm" to "Підтвердити",
        "common_save" to "Зберегти",
        "common_delete" to "Видалити",
        "common_close" to "Закрити"
    )
}

class AppStrings(
    private val primary: Map<String, String>,
    private val secondary: Map<String, String> = emptyMap(),
    private val fallback: Map<String, String> = emptyMap()
) {
    fun tr(key: String, params: Map<String, String> = emptyMap()): String {
        var text = primary[key] ?: secondary[key] ?: fallback[key] ?: key
        if (params.isNotEmpty()) {
            for ((k, v) in params) {
                text = text.replace("{$k}", v)
            }
        }
        return text
    }
}
