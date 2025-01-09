package org.example;

import java.awt.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

public class linksShortener {
    private final Map<String, String> shortToLongUrl = new HashMap<>();
    private final String BASE_URL = "chertchill.ru/";

    /**
     * Преобразует длинный URL в короткий.
     * Для каждого запроса создается уникальная короткая ссылка.
     */
    public String shortenUrl(String longUrl) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 6);
        String shortUrl = BASE_URL + uniqueId;

        shortToLongUrl.put(shortUrl, longUrl);
        return shortUrl;
    }

    /**
     * Перенаправляет пользователя на исходный ресурс.
     */
    public void redirect(String shortUrl) {
        String longUrl = shortToLongUrl.get(shortUrl);

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

    public static void main(String[] args) {
        linksShortener shortener = new linksShortener();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("Введите длинный URL для сокращения или короткую ссылку для перехода (или 'exit' для выхода):");
            String input = scanner.nextLine();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Выход из программы.");
                break;
            }

            // Проверка на короткую ссылку
            if (input.startsWith(shortener.BASE_URL)) {
                shortener.redirect(input);
            } else {
                // Проверка доступности URL
                if (shortener.isUrlAccessible(input)) {
                    // Генерация короткой ссылки
                    String shortUrl = shortener.shortenUrl(input);
                    System.out.println("Короткая ссылка: " + shortUrl);

                    // Спрашиваем, хочет ли пользователь выполнить редирект
                    System.out.println("Хотите открыть короткую ссылку в браузере? (да/нет):");
                    String response = scanner.nextLine();

                    if (response.equalsIgnoreCase("да")) {
                        shortener.redirect(shortUrl);
                    }
                } else {
                    System.out.println("Введенный URL недоступен или не существует.");
                }
            }
        }

        scanner.close();
    }
}