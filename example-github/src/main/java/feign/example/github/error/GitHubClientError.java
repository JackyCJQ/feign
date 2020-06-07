package feign.example.github.error;

public class GitHubClientError extends RuntimeException {
    private String message;

    @Override
    public String getMessage() {
        return message;
    }
}