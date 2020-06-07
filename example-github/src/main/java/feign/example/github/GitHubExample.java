/**
 * Copyright 2012-2019 The Feign Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.example.github;

import feign.example.github.error.GitHubClientError;
import feign.example.github.inter.GitHub;

import java.util.List;

public class GitHubExample {

    public static void main(String... args) {
        GitHub github = GitHub.connect();

        System.out.println("Let's fetch and print a list of the contributors to this org.");
        List<String> contributors = github.contributors("openfeign");
        for (String contributor : contributors) {
            System.out.println(contributor);
        }
//        System.out.println("Now, let's cause an error.");
//        try {
//            github.contributors("openfeign", "some-unknown-project");
//        } catch (GitHubClientError e) {
//            System.out.println(e.getMessage());
//        }
    }

}
