package com.dayan.food.service.impl;

import com.dayan.food.entity.vo.ImageExportVO;
import com.dayan.food.image.ImageDimensions;
import com.dayan.food.service.FoodImageExportService;
import com.dayan.food.service.FoodService;
import com.dayan.food.service.ImageStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Service
public class FoodImageExportServiceImpl implements FoodImageExportService {

    private static final int MAX_REDIRECTS = 3;
    private static final long MAX_PIXELS = 24_000_000L;

    private final FoodService foodService;
    private final ImageStorageService imageStorageService;
    private final HttpClient httpClient;
    private final HostResolver hostResolver;
    private final int maxImageBytes;

    @Autowired
    public FoodImageExportServiceImpl(
            FoodService foodService,
            ImageStorageService imageStorageService,
            @Value("${app.image-export.max-image-bytes:10485760}") int maxImageBytes
    ) {
        this(
                foodService,
                imageStorageService,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                InetAddress::getAllByName,
                maxImageBytes
        );
    }

    FoodImageExportServiceImpl(
            FoodService foodService,
            ImageStorageService imageStorageService,
            HttpClient httpClient,
            HostResolver hostResolver,
            int maxImageBytes
    ) {
        this.foodService = foodService;
        this.imageStorageService = imageStorageService;
        this.httpClient = httpClient;
        this.hostResolver = hostResolver;
        this.maxImageBytes = maxImageBytes;
    }

    @Override
    public ImageExportVO read(Long foodId) {
        String imageUrl = foodService.detail(foodId).imageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "菜品没有可导出的图片");
        }
        if (imageUrl.startsWith("/uploads/")) {
            return imageStorageService.readForExport(imageUrl);
        }
        return readRemote(parseRemoteUri(imageUrl), 0);
    }

    private ImageExportVO readRemote(URI uri, int redirects) {
        validateRemoteTarget(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "image/webp,image/png,image/jpeg")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                try (InputStream ignored = response.body()) {
                    if (redirects >= MAX_REDIRECTS) {
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片重定向次数过多");
                    }
                    String location = response.headers().firstValue("location")
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片重定向地址缺失"));
                    return readRemote(uri.resolve(location), redirects + 1);
                }
            }
            if (status < 200 || status >= 300) {
                try (InputStream ignored = response.body()) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "远程图片不可用");
                }
            }
            try (InputStream input = response.body()) {
                byte[] content = input.readNBytes(maxImageBytes + 1);
                if (content.length > maxImageBytes) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "远程图片过大");
                }
                return inspect(content);
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "远程图片读取中断", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "远程图片读取失败", exception);
        }
    }

    private URI parseRemoteUri(String imageUrl) {
        try {
            return URI.create(imageUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片地址无效", exception);
        }
    }

    private void validateRemoteTarget(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 HTTP(S) 图片");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片地址无效");
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片端口不受支持");
        }
        try {
            for (InetAddress address : hostResolver.resolve(uri.getHost())) {
                if (!isPublicAddress(address)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片地址不能指向内网");
                }
            }
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片域名无法解析", exception);
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first != 0
                    && first != 127
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254)
                    && !(first == 198 && (second == 18 || second == 19))
                    && first < 224;
        }
        return (bytes[0] & 0xfe) != 0xfc;
    }

    private ImageExportVO inspect(byte[] content) throws IOException {
        String contentType;
        if (content.length >= 3
                && Byte.toUnsignedInt(content[0]) == 0xff
                && Byte.toUnsignedInt(content[1]) == 0xd8
                && Byte.toUnsignedInt(content[2]) == 0xff) {
            contentType = "image/jpeg";
        } else if (content.length >= 8
                && Byte.toUnsignedInt(content[0]) == 0x89
                && content[1] == 'P'
                && content[2] == 'N'
                && content[3] == 'G') {
            contentType = "image/png";
        } else if (content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P') {
            contentType = "image/webp";
        } else {
            throw new IllegalArgumentException("远程内容不是支持的图片格式");
        }
        if (ImageDimensions.read(content).pixels() > MAX_PIXELS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "图片像素过多");
        }
        return new ImageExportVO(content, contentType);
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
