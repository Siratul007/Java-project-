package buet.com.demo3;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class GoogleAuthService {
    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String LOGIN_PAGE_ENDPOINT = "https://accounts.google.com/signin/v2/identifier";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Gson GSON = new Gson();

    private GoogleAuthService() {
    }

    public static GoogleUser authenticate() throws IOException, InterruptedException {
        GoogleOAuthConfig config = loadConfig();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());

        String state = randomUrlSafe(32);
        String codeVerifier = randomUrlSafe(64);
        String codeChallenge = createCodeChallenge(codeVerifier);
        String redirectUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth2/callback";
        CompletableFuture<String> codeFuture = new CompletableFuture<>();

        server.createContext("/oauth2/callback", exchange -> handleCallback(exchange, state, codeFuture));
        server.start();

        try {
            Desktop.getDesktop().browse(buildAuthorizationUri(config.clientId(), redirectUri, state, codeChallenge));
            String authorizationCode = codeFuture.get(180, TimeUnit.SECONDS);
            GoogleToken token = exchangeCodeForToken(config, redirectUri, authorizationCode, codeVerifier);
            return fetchUserProfile(token.accessToken());
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new IOException("Timed out waiting for Google sign-in to complete.", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IOException("Google sign-in failed.", exception.getCause());
        } finally {
            server.stop(0);
        }
    }

    public static void openGoogleLoginPage() throws IOException {
        Desktop.getDesktop().browse(URI.create(LOGIN_PAGE_ENDPOINT));
    }

    private static URI buildAuthorizationUri(String clientId, String redirectUri, String state, String codeChallenge) {
        String query = formEncode(Map.of(
                "client_id", clientId,
                "redirect_uri", redirectUri,
                "response_type", "code",
                "scope", "openid email profile",
                "access_type", "offline",
                "prompt", "select_account",
                "state", state,
                "code_challenge", codeChallenge,
                "code_challenge_method", "S256"
        ));
        return URI.create(AUTH_ENDPOINT + "?" + query);
    }

    private static void handleCallback(HttpExchange exchange, String expectedState, CompletableFuture<String> codeFuture) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String state = query.get("state");
        String code = query.get("code");
        String error = query.get("error");

        String response;
        int statusCode;
        if (error != null) {
            response = "<html><body><h2>Google sign-in was cancelled.</h2><p>You can return to the application.</p></body></html>";
            statusCode = 400;
            codeFuture.completeExceptionally(new IllegalStateException("Google sign-in was cancelled: " + error));
        } else if (code == null || state == null || !expectedState.equals(state)) {
            response = "<html><body><h2>Invalid sign-in response.</h2><p>You can close this window.</p></body></html>";
            statusCode = 400;
            codeFuture.completeExceptionally(new IllegalStateException("Invalid OAuth callback received."));
        } else {
            response = "<html><body><h2>Sign-in complete.</h2><p>You can return to the application.</p></body></html>";
            statusCode = 200;
            codeFuture.complete(code);
        }

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static GoogleToken exchangeCodeForToken(GoogleOAuthConfig config, String redirectUri, String code, String codeVerifier)
            throws IOException, InterruptedException {
        Map<String, String> payload = new HashMap<>();
        payload.put("client_id", config.clientId());
        payload.put("code", code);
        payload.put("code_verifier", codeVerifier);
        payload.put("grant_type", "authorization_code");
        payload.put("redirect_uri", redirectUri);
        if (config.clientSecret() != null && !config.clientSecret().isBlank()) {
            payload.put("client_secret", config.clientSecret());
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_ENDPOINT))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(payload)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Google token exchange failed: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return new GoogleToken(json.get("access_token").getAsString());
    }

    private static GoogleUser fetchUserProfile(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(USERINFO_ENDPOINT))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to load Google profile: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return new GoogleUser(
                json.has("name") ? json.get("name").getAsString() : "",
                json.has("email") ? json.get("email").getAsString() : "",
                json.has("picture") ? json.get("picture").getAsString() : ""
        );
    }

    private static GoogleOAuthConfig loadConfig() throws IOException {
        String clientId = System.getenv("GOOGLE_OAUTH_CLIENT_ID");
        String clientSecret = System.getenv("GOOGLE_OAUTH_CLIENT_SECRET");

        if ((clientId == null || clientId.isBlank())) {
            Properties properties = new Properties();
            try (var inputStream = GoogleAuthService.class.getResourceAsStream("/google-oauth.properties")) {
                if (inputStream != null) {
                    properties.load(inputStream);
                    clientId = properties.getProperty("google.clientId", clientId);
                    clientSecret = properties.getProperty("google.clientSecret", clientSecret);
                }
            }
        }

        if (clientId == null || clientId.isBlank()) {
            throw new IOException("Google OAuth is not configured. Set GOOGLE_OAUTH_CLIENT_ID or create src/main/resources/google-oauth.properties.");
        }

        return new GoogleOAuthConfig(clientId, clientSecret);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> values = new HashMap<>();
        if (query == null || query.isBlank()) {
            return values;
        }

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = urlDecode(parts[0]);
            String value = parts.length > 1 ? urlDecode(parts[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    private static String formEncode(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        new SecureRandom().nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static String createCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private record GoogleOAuthConfig(String clientId, String clientSecret) {
    }

    private record GoogleToken(String accessToken) {
    }

    public record GoogleUser(String name, String email, String pictureUrl) {
    }
}
