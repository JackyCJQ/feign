package feign.example.github.decoder;

import feign.Response;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.example.github.error.GitHubClientError;

import java.io.IOException;

public class GitHubErrorDecoder implements ErrorDecoder {

    final Decoder decoder;
    final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    public GitHubErrorDecoder(Decoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            return (Exception) decoder.decode(response, GitHubClientError.class);
        } catch (IOException fallbackToDefault) {
            return defaultDecoder.decode(methodKey, response);
        }
    }
}