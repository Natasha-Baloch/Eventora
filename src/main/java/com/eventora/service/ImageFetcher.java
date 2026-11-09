package com.eventora.service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On first start, downloads real, keyword-matched event photographs from the internet
 * (LoremFlickr -> Creative-Commons photos on Flickr) into static/images/events.
 * Files that already exist are never re-downloaded. If you prefer your own photos from
 * Unsplash/Pexels/Wikimedia, just replace the .jpg files with the same names.
 */
@Component
public class ImageFetcher implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ImageFetcher.class);
    @Value("${eventora.download-sample-images:true}") private boolean enabled;

    private static final String[][] IMAGES = {
        {"concert-1", "concert,stage", "101"}, {"dj-party-1", "dj,nightclub", "102"},
        {"tech-1", "conference,technology", "103"},
        {"sports-1", "stadium,football", "105"}, {"cultural-1", "festival,culture,dance", "106"},
        {"food-1", "street,food,market", "107"}, {"business-1", "business,meeting,conference", "108"},
    };

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        List<Path> dirs = new ArrayList<>();
        if (Files.isDirectory(Path.of("src/main/resources/static"))) dirs.add(Path.of("src/main/resources/static/images/events"));
        if (Files.isDirectory(Path.of("target/classes"))) dirs.add(Path.of("target/classes/static/images/events"));
        if (dirs.isEmpty()) return;
        Thread th = new Thread(() -> download(dirs), "image-fetcher");
        th.setDaemon(true);
        th.start();
    }

    private void download(List<Path> dirs) {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(15)).build();
        for (String[] img : IMAGES) {
            Path first = dirs.get(0).resolve(img[0] + ".jpg");
            boolean missing = dirs.stream().anyMatch(d -> !Files.exists(d.resolve(img[0] + ".jpg")));
            if (!missing) continue;
            try {
                byte[] data = null;
                if (Files.exists(first)) data = Files.readAllBytes(first);
                else {
                    HttpRequest req = HttpRequest.newBuilder(URI.create("https://loremflickr.com/1200/800/" + img[1] + "?lock=" + img[2]))
                        .header("User-Agent", "Eventora/1.0").timeout(Duration.ofSeconds(30)).GET().build();
                    HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                    data = res.body();
                    if (res.statusCode() != 200 || data.length < 15_000 || ImageIO.read(new ByteArrayInputStream(data)) == null)
                        throw new IllegalStateException("bad response " + res.statusCode());
                }
                for (Path d : dirs) {
                    Files.createDirectories(d);
                    if (!Files.exists(d.resolve(img[0] + ".jpg"))) Files.write(d.resolve(img[0] + ".jpg"), data);
                }
                log.info("Downloaded event image {}", img[0]);
            } catch (Exception ex) {
                log.warn("Could not download image {} ({}). Put your own {}.jpg into src/main/resources/static/images/events", img[0], ex.getMessage(), img[0]);
            }
        }
        try {
            Files.writeString(dirs.get(0).resolve("CREDITS.txt"),
                "Sample event photos are fetched from LoremFlickr (https://loremflickr.com), which serves Creative Commons-licensed photos from Flickr.\n"
              + "Licence and author differ per photo. Before commercial use replace them with photos you have rights to\n"
              + "(Unsplash, Pexels, Wikimedia Commons) using the same file names.\n");
        } catch (Exception ignored) { }
    }
}
