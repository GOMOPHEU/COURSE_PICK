package com.coursepick.candidate;

import com.coursepick.config.CoursepickProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KakaoCandidateClientTest {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com").defaultHeader("Authorization", "KakaoAK test");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    KakaoCandidateClient client(String key) {
        return new KakaoCandidateClient(builder.build(), new CoursepickProperties("", new CoursepickProperties.Keys(key,"",""),null));
    }
    @Test void sendsCoordinatesPaginationAndAuthAndParsesResponse() {
        server.expect(requestTo("https://dapi.kakao.com/v2/local/search/category.json?x=126.9855&y=37.5765&radius=690&sort=distance&size=15&page=2&category_group_code=CE7"))
                .andExpect(header("Authorization", "KakaoAK test"))
                .andRespond(withSuccess("""
                    {"meta":{"is_end":true},"documents":[{"id":"1","place_name":"카페","category_name":"음식점 > 카페","category_group_code":"CE7","x":"126.9855","y":"37.5765"}]}
                    """, MediaType.APPLICATION_JSON));
        var result = client("test").search(new CandidatePolicy.Search("CE7",null),37.5765,126.9855,690,2);
        assertThat(result.end()).isTrue();
        assertThat(result.places()).hasSize(1);
        assertThat(result.places().get(0).lat()).isEqualTo(37.5765);
        server.verify();
    }
    @Test void keywordUsesKeywordEndpointAndEncodedQuery() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/keyword.json?")))
                .andExpect(queryParam("query", "%EC%86%8C%ED%92%88%EC%83%B5"))
                .andRespond(withSuccess("{\"meta\":{\"is_end\":true},\"documents\":[]}", MediaType.APPLICATION_JSON));
        client("test").search(new CandidatePolicy.Search(null,"소품샵"),37.5765,126.9855,690,1);
        server.verify();
    }
    @Test void missingKeyDoesNotCallProvider() {
        assertThatThrownBy(() -> client("").search(new CandidatePolicy.Search("CE7",null),37.5,127,690,1))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }
    @Test void providerErrorDoesNotLeakBody() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("private provider details"));
        assertThatThrownBy(() -> client("test").search(new CandidatePolicy.Search("CE7",null),37.5,127,690,1))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getMessage()).doesNotContain("private");
                });
    }
    @Test void malformedResponseIsNotAnEmptyResult() {
        server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client("test").search(new CandidatePolicy.Search("CE7",null),37.5,127,690,1))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
