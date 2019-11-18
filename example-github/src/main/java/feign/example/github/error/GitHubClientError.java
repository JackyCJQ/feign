package feign.example.github.error;

public class GitHubClientError extends RuntimeException {
    private String message; // parsed from json

    @Override
    public String getMessage() {
        return message;
    }
}