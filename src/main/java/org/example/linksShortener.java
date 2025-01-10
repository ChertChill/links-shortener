package org.example;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class linksShortener {
    private final Map<String, UserData> users = new HashMap<>();
    private final String BASE_URL = "chertchill.ru/";

    private static final String DATA_FILE = "user_data.json";
    private final Gson gson = new Gson();
    private String currentUserUuid;

    public linksShortener() {
        loadData(); // Загрузка данных из файла при запуске
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
    public void authenticate(String username) {
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
    public String shortenUrl(String longUrl) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 6);
        String shortUrl = BASE_URL + uniqueId;

        UserData currentUser = getCurrentUser();
        currentUser.getLinks().put(shortUrl, longUrl);

        saveData();
        return shortUrl;
    }

    /**
     * Перенаправляет пользователя на исходный ресурс.
     */
    public void redirect(String shortUrl) {
        String longUrl = users.values().stream()
                .map(UserData::getLinks)
                .filter(links -> links.containsKey(shortUrl))
                .map(links -> links.get(shortUrl))
                .findFirst()
                .orElse(null);

        if (longUrl != null) {
            try {
                Desktop.getDesktop().browse(new URI(longUrl));
            } catch (Exception e) {
                System.err.println("Ошибка при открытии URL: " + e.getMessage());
            }
        } else {
            System.out.println("Ссылка недействительна или срок её действия истёк.");
        }
    }

    /**
     * Удаляет ссылку, если запрос отправил её создатель.
     */
    public void deleteUrl(String shortUrl) {
        UserData currentUser = getCurrentUser();

        if (currentUser.getLinks().remove(shortUrl) != null) {
            saveData();
            System.out.println("Ссылка удалена.");
        } else {
            System.out.println("Ссылка не существует или вы не являетесь её владельцем.");
        }
    }

    /**
     * Отображает все ссылки, созданные текущим пользователем.
     */
    public void showUserLinks() {
        UserData currentUser = getCurrentUser();
        Map<String, String> links = currentUser.getLinks();

        if (links.isEmpty()) {
            System.out.println("У вас нет созданных ссылок.");
        } else {
            System.out.println("Ваши ссылки:");

            // Сортируем по исходным ссылкам
            links.entrySet()
                    .stream()
                    .sorted(Comparator.comparing(Map.Entry::getValue)) // Сортировка по значению (исходному URL)
                    .forEach(entry -> {
                        System.out.println(entry.getKey() + " - " + entry.getValue());
                    });
        }
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

    private UserData getCurrentUser() {
        return users.values().stream()
                .filter(user -> user.getUuid().equals(currentUserUuid))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден!"));
    }

    public static void main(String[] args) {
        linksShortener shortener = new linksShortener();
        Scanner scanner = new Scanner(System.in);

        System.out.println("Введите ваш username для входа:");
        String username = scanner.nextLine();
        shortener.authenticate(username);

        while (true) {
            System.out.println("Выберите действие:\n1. Создать короткую ссылку\n2. Перейти по короткой ссылке\n3. Удалить короткую ссылку\n4. Показать все созданные ссылки\n5. Выйти из программы");
            String input = scanner.nextLine();

            switch (input) {
                case "1":
                    System.out.println("Введите исходный URL:");
                    String longUrl = scanner.nextLine();

                    if (shortener.isUrlAccessible(longUrl)) {
                        String shortUrl = shortener.shortenUrl(longUrl);
                        System.out.println("Короткая ссылка: " + shortUrl);
                    } else {
                        System.out.println("Введенный URL недоступен или не существует.");
                    }
                    break;

                case "2":
                    System.out.println("Введите короткую ссылку:");
                    String shortUrl = scanner.nextLine();
                    shortener.redirect(shortUrl);
                    break;

                case "3":
                    System.out.println("Введите короткую ссылку для удаления:");
                    String urlToDelete = scanner.nextLine();
                    shortener.deleteUrl(urlToDelete);
                    break;

                case "4":
                    shortener.showUserLinks();
                    break;

                case "5":
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
    private final Map<String, String> links;

    public UserData(String uuid, Map<String, String> links) {
        this.uuid = uuid;
        this.links = links;
    }

    public String getUuid() {
        return uuid;
    }

    public Map<String, String> getLinks() {
        return links;
    }
}



// Проверка – хочет ли пользователь выполнить редирект
//while (true) {
//    System.out.println("Хотите открыть короткую ссылку в браузере? (да/нет):");
//    String response = scanner.nextLine();
//
//    if (response.equalsIgnoreCase("да")) {
//        shortener.redirect(shortUrl);
//        break;
//    } else if (response.equalsIgnoreCase("нет")) {
//        break;
//    } else {
//        System.out.println("Некорректный ввод.");
//    }
//}