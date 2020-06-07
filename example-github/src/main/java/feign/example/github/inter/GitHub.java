package feign.example.github.inter;

import feign.Feign;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.codec.Decoder;
import feign.example.github.decoder.GitHubErrorDecoder;
import feign.example.github.model.Contributor;
import feign.example.github.model.Repository;
import feign.gson.GsonDecoder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @auther
 */
//@Headers({"name:aa", "name:bb", "age:12"})
public interface GitHub {
    @RequestLine(value = "GET /users/{username}/repos?sort=full_name")
//    @Headers(value = "")
//    @Body("")
    List<Repository> repos(@Param("username") String owner);

    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);

    default List<String> contributors(String owner) {
        return repos(owner).stream()
                .flatMap(repo -> contributors(owner, repo.name).stream())
                .map(c -> c.login)
                .distinct()
                .collect(Collectors.toList());
    }

    static GitHub connect() {
        Decoder decoder = new GsonDecoder();
        return Feign.builder()
                .decoder(decoder)
                .errorDecoder(new GitHubErrorDecoder(decoder))
                .logger(new Logger.ErrorLogger())
                .logLevel(Logger.Level.BASIC)
                .target(GitHub.class, "https://api.github.com");
    }
}
