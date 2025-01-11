package org.example;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class linksShortener {
    private final Map<String, UserData> users = new HashMap<>();
    private final String BASE_URL = "chertchill.ru/";

    private static final String DATA_FILE = "user_data.json";
    private static final long MAX_EXPIRY_TIME_MS = TimeUnit.DAYS.toMillis(1);   // 24ч по умолчанию
    private final Gson gson = new Gson();
    private String currentUserUuid;

    public linksShortener() {
        loadData(); // Загрузка данных из файла при запуске
        removeExpiredLinks(); // Удаление устаревших ссылок при запуске
    }

    /**
     * Загружает данные пользователей из файла.
     */
    private void loadData() {
        try (Reader reader = new FileReader(DATA_FILE)) {
            Map<String, UserData> data = gson.fromJson(reader, new TypeToken<Map<String, UserData>>() {}.getType());
            if (data != null) {
                users.putAll(data);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Данные не найдены, начата новая сессия.");
        } catch (IOException e) {
            System.err.println("Ошибка при загрузке данных: " + e.getMessage());
        }
    }

    /**
     * Сохраняет данные пользователей в файл.
     */
    private void saveData() {
        try (Writer writer = new FileWriter(DATA_FILE)) {
            gson.toJson(users, writer);
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении данных: " + e.getMessage());
        }
    }

    /**
     * Аутентифицирует пользователя или создает нового.
     */
    public void authenticate(Scanner scanner) {
        String username;

        while (true) {
            System.out.println("Введите ваш username для входа:");
            username = scanner.nextLine().trim();

            // Проверка, что имя пользователя состоит только из букв (латинские буквы)
            if (username.isEmpty() || !username.matches("[a-zA-Z]+")) {
                System.out.println("Имя пользователя должно состоять только из букв. Попробуйте снова.");
            } else {
                break; // Если имя корректно, выходим из цикла
            }
        }

        if (users.containsKey(username)) {
            currentUserUuid = users.get(username).getUuid();
            System.out.println("Добро пожаловать, " + username + "!");
            System.out.println("Ваш UUID: " + currentUserUuid);
        } else {
            UserData newUser = new UserData(UUID.randomUUID().toString(), new HashMap<>());
            users.put(username, newUser);
            currentUserUuid = newUser.getUuid();
            saveData();
            System.out.println("Новый пользователь создан.");
            System.out.println("Ваш UUID: " + currentUserUuid);
        }
    }

    /**
     * Преобразует исходный URL в короткий.
     * Для каждого запроса создается уникальная короткая ссылка.
     */
    public void shortenUrl(Scanner scanner) {
        String longUrl;
        while (true) {
            System.out.println("Введите исходный URL ('exit' - для перехода в меню):");
            longUrl = scanner.nextLine();

            if ("exit".equalsIgnoreCase(longUrl)) {
                System.out.println("Переход в меню.");
                return;
            }

            // Проверка доступности URL
            if (isUrlAccessible(longUrl)) {
                break;
            }
            System.out.println("URL недоступен или не существует. Попробуйте снова.");
        }

        String durationInput;
        long durationMs;
        do {
            System.out.println("Введите время действия ссылки (например, 5h 30m, 1h, 5m; 'exit' - для перехода в меню):");
            durationInput = scanner.nextLine();

            if ("exit".equalsIgnoreCase(durationInput)) {
                System.out.println("Переход в меню.");
                return;
            }

            durationMs = parseDuration(durationInput);  // Парсинг времени действия
        } while (durationMs <= 0);

        // Ограничение времени действия до максимального
        if (durationMs > MAX_EXPIRY_TIME_MS) {
            durationMs = MAX_EXPIRY_TIME_MS;
            System.out.println("Внимание: Максимальное время действия ссылки – 24 часа (установлено автоматически).");
        }

        // Создание короткой ссылки
        String uniqueId = UUID.randomUUID().toString().substring(0, 6);
        String shortUrl = BASE_URL + uniqueId;

        UserData currentUser = getCurrentUser();
        currentUser.getLinks().put(shortUrl, new LinkData(longUrl, System.currentTimeMillis() + durationMs));

        saveData();
        System.out.println("Короткая ссылка: " + shortUrl);
    }

    /**
     * Перенаправляет пользователя на исходный ресурс.
     */
    public void redirect(Scanner scanner) {
        removeExpiredLinks(); // Удаление устаревших ссылок

        while (true) {
            System.out.println("Введите короткую ссылку для перенаправления ('exit' - для перехода в меню):");
            String shortUrlInput = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(shortUrlInput)) {
                System.out.println("Переход в меню.");
                return;
            }

            // Локальная копия переменной для использования в лямбда-выражении
            final String shortUrl = shortUrlInput;

            // Проверка существования ссылки у любого пользователя
            LinkData link = users.values().stream()
                    .map(UserData::getLinks)
                    .map(links -> links.get(shortUrl))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            if (link != null) {
                long currentTime = System.currentTimeMillis();
                if (currentTime <= link.getExpiryTime()) {
                    try {
                        Desktop.getDesktop().browse(new URI(link.getLongUrl()));
                        System.out.println("Перенаправление на: " + link.getLongUrl());
                    } catch (Exception e) {
                        System.err.println("Ошибка при открытии URL: " + e.getMessage());
                    }
                } else {
                    System.out.println("Срок действия ссылки истёк. Исходная ссылка: " + link.getLongUrl());
                }
                return;
            }

            System.out.println("Короткая ссылка не найдена. Попробуйте снова.");
        }
    }

    /**
     * Удаляет ссылку, если запрос отправил её создатель.
     */
    public void deleteUrl(Scanner scanner) {
        removeExpiredLinks();    // Удаление устаревших ссылок
        UserData currentUser = getCurrentUser();

        while (true) {
            System.out.println("Введите короткую ссылку для удаления ('exit' - для перехода в меню):");
            String shortUrl = scanner.nextLine();

            if ("exit".equalsIgnoreCase(shortUrl)) {
                System.out.println("Переход в меню.");
                return;
            }

            if (currentUser.getLinks().remove(shortUrl) != null) {
                saveData();
                System.out.println("Ссылка удалена.");
            } else {
                System.out.println("Ссылка не существует или вы не являетесь её владельцем.");
            }
        }
    }

    /**
     * Отображает все ссылки текущего пользователя с удалением просроченных ссылок.
     */
    public void showUserLinks() {
        removeExpiredLinks();   // Удаление устаревших ссылок

        UserData currentUser = getCurrentUser();
        Map<String, LinkData> links = currentUser.getLinks();

        if (links.isEmpty()) {
            System.out.println("У вас нет созданных ссылок.");
        } else {
            System.out.println("Ваши ссылки:");
            links.entrySet()
                    .stream()
                    .sorted(Comparator.comparing(entry -> entry.getValue().getLongUrl())) // Сортировка по исходным ссылкам
                    .forEach(entry -> {
                        String shortUrl = entry.getKey();
                        LinkData linkData = entry.getValue();
                        String remainingTime = formatRemainingTime(linkData.getExpiryTime());
                        System.out.println(shortUrl + " - " + linkData.getLongUrl() + " (" + remainingTime + ")");
                    });
        }
    }

    private String formatRemainingTime(long expiryTime) {
        long remainingMs = expiryTime - System.currentTimeMillis();
        if (remainingMs <= 0) return "истекло";

        long days = TimeUnit.MILLISECONDS.toDays(remainingMs);
        remainingMs -= TimeUnit.DAYS.toMillis(days);

        long hours = TimeUnit.MILLISECONDS.toHours(remainingMs);
        remainingMs -= TimeUnit.HOURS.toMillis(hours);

        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs);
        remainingMs -= TimeUnit.MINUTES.toMillis(minutes);

        long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs);

        // Собираем строку времени в нужном формате
        StringBuilder timeRemaining = new StringBuilder();
        if (days > 0) timeRemaining.append(days).append("d ");
        if (hours > 0) timeRemaining.append(hours).append("h ");
        if (minutes > 0) timeRemaining.append(minutes).append("m ");
        if (seconds > 0) timeRemaining.append(seconds).append("s");

        return timeRemaining.toString().trim();
    }

    /**
     * Позволяет редактировать параметры короткой ссылки.
     */
    public void editLink(Scanner scanner) {
        removeExpiredLinks();   // Удаление устаревших ссылок
        UserData currentUser = getCurrentUser();

        String shortUrl;
        while (true) {
            System.out.println("Введите короткую ссылку для редактирования ('exit' - для перехода в меню):");
            shortUrl = scanner.nextLine();
            if ("exit".equalsIgnoreCase(shortUrl)) {
                System.out.println("Переход в меню.");
                return;
            }

            // Проверка существования ссылки у текущего пользователя
            if (currentUser.getLinks().containsKey(shortUrl)) {
                break;
            }
            System.out.println("Ссылка не существует или вы не являетесь её владельцем. Попробуйте снова.");
        }

        LinkData currentLink = currentUser.getLinks().get(shortUrl);
        if (currentLink == null) {
            System.out.println("Ссылка была удалена или устарела. Пожалуйста, выберите другую.");
            return;
        }

        String newLongUrl;
        while (true) {
            System.out.println("Текущая ссылка: " + currentLink.getLongUrl());
            System.out.println("Введите новый URL (нажмите Enter, чтобы оставить текущий или введите 'exit' - для перехода в меню):");
            newLongUrl = scanner.nextLine();
            if ("exit".equalsIgnoreCase(newLongUrl)) {
                System.out.println("Переход в меню.");
                return;
            }

            // Оставить текущий URL
            if (newLongUrl.isEmpty()) {
                newLongUrl = currentLink.getLongUrl();
                break;
            }

            // Проверка доступности нового URL
            if (isUrlAccessible(newLongUrl)) {
                break;
            }
            System.out.println("Новый URL недоступен или не существует. Попробуйте снова.");
        }

        String newDurationInput;
        long newExpiryTimeMs;
        while (true) {
            System.out.println("Текущее время действия ссылки: " + formatRemainingTime(currentLink.getExpiryTime()));
            System.out.println("Введите новое время действия (например, 1d 2h 30m; нажмите Enter, чтобы оставить текущее время; 'exit' - для перехода в меню):");
            newDurationInput = scanner.nextLine();
            if ("exit".equalsIgnoreCase(newDurationInput)) {
                System.out.println("Переход в меню.");
                return;
            }

            // Оставить текущее время действия
            if (newDurationInput.isEmpty()) {
                newExpiryTimeMs = currentLink.getExpiryTime();
                break;
            }

            // Парсинг времени действия
            newExpiryTimeMs = parseDuration(newDurationInput);
            if (newExpiryTimeMs > 0) {
                if (newExpiryTimeMs > MAX_EXPIRY_TIME_MS) {
                    System.out.println("Указанное время превышает максимальное значение в 24 часа и будет ограничено автоматически.");
                    newExpiryTimeMs = MAX_EXPIRY_TIME_MS;
                }
                newExpiryTimeMs += System.currentTimeMillis(); // Установка нового времени истечения
                break;
            }
        }

        // Проверяем, не истекло ли новое время действия
        if (newExpiryTimeMs <= System.currentTimeMillis()) {
            System.out.println("Указанное время действия уже истекло. Ссылка не была обновлена.");
            return;
        }

        currentUser.getLinks().put(shortUrl, new LinkData(newLongUrl, newExpiryTimeMs));

        saveData();
        System.out.println("Ссылка успешно обновлена: " + shortUrl);
    }

    /**
     * Позволяет парсить время действия ссылки в мультиформатном виде для 1h, 1d, 1m.
     */
    private long parseDuration(String input) {
        long totalMs = 0;
        String[] parts = input.split(" ");
        for (String part : parts) {
            try {
                if (part.endsWith("d")) {
                    totalMs += TimeUnit.DAYS.toMillis(Long.parseLong(part.replace("d", "")));
                } else if (part.endsWith("h")) {
                    totalMs += TimeUnit.HOURS.toMillis(Long.parseLong(part.replace("h", "")));
                } else if (part.endsWith("m")) {
                    totalMs += TimeUnit.MINUTES.toMillis(Long.parseLong(part.replace("m", "")));
                } else {
                    System.out.println("Неверный формат времени: " + part + ". Укажите значение, например, 1d 2h 30m.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Ошибка парсинга: " + part);
            }
        }
        return totalMs;
    }

    /**
     * Удаляет просроченные ссылки с указанием исходной ссылки в сообщении.
     */
    public void removeExpiredLinks() {
        long currentTime = System.currentTimeMillis();
        users.forEach((username, user) -> {
            Iterator<Map.Entry<String, LinkData>> iterator = user.getLinks().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, LinkData> entry = iterator.next();
                if (entry.getValue().getExpiryTime() <= currentTime) {
                    System.out.println("Ссылка " + entry.getKey() + " (" + entry.getValue().getLongUrl() + ") стала недоступна. Пользователь: " + username);
                    iterator.remove();
                }
            }
        });
        saveData();
    }

    /**
     * Проверяет доступность URL.
     */
    public boolean isUrlAccessible(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000); // Таймаут 5 секунд
            connection.setReadTimeout(5000);
            connection.connect();

            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 400; // Проверка на успешные коды
        } catch (Exception e) {
            return false; // Если произошла ошибка, считаем, что URL недоступен
        }
    }

    /**
     * Позволяет получить данные текущего пользователя.
     */
    private UserData getCurrentUser() {
        return users.values().stream()
                .filter(user -> user.getUuid().equals(currentUserUuid))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден!"));
    }

    public static void main(String[] args) {
        linksShortener shortener = new linksShortener();
        Scanner scanner = new Scanner(System.in);

        shortener.authenticate(scanner);

        while (true) {
            shortener.removeExpiredLinks();

            System.out.println("""
            ==========================================
            Выберите действие:
            1. Создать короткую ссылку
            2. Перейти по короткой ссылке
            3. Показать все созданные ссылки
            4. Редактировать параметры короткой ссылки
            5. Удалить короткую ссылку
            6. Выйти из программы
            ==========================================""");
            String input = scanner.nextLine();

            switch (input) {
                case "1":
                    shortener.shortenUrl(scanner);
                    break;

                case "2":
                    shortener.redirect(scanner);
                    break;

                case "3":
                    shortener.showUserLinks();
                    break;

                case "4":
                    shortener.editLink(scanner);
                    break;

                case "5":
                    shortener.deleteUrl(scanner);
                    break;

                case "6":
                    System.out.println("Выход из программы. Спасибо за использование!");
                    scanner.close();
                    return;

                default:
                    System.out.println("Некорректный выбор. Попробуйте снова.");
            }
        }
    }
}



class UserData {
    private final String uuid;
    private final Map<String, LinkData> links;

    public UserData(String uuid, Map<String, LinkData> links) {
        this.uuid = uuid;
        this.links = links;
    }

    public String getUuid() {
        return uuid;
    }

    public Map<String, LinkData> getLinks() {
        return links;
    }
}

class LinkData {
    private final String longUrl;
    private final long expiryTime;

    public LinkData(String longUrl, long expiryTime) {
        this.longUrl = longUrl;
        this.expiryTime = expiryTime;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public long getExpiryTime() {
        return expiryTime;
    }
}