package com.coursepick.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoLocalClientTest {

    private static final String PAGE1 = """
            {"meta":{"is_end":false},"documents":[
              {"id":"1","place_name":"카페A","category_name":"음식점 > 카페","category_group_code":"CE7",
               "address_name":"서울 종로구 1","road_address_name":"서울 종로구 길 1",
               "x":"126.9860","y":"37.5770","distance":"120","place_url":"http://place.map.kakao.com/1"}]}
            """;
    private static final String PAGE2 = """
            {"meta":{"is_end":true},"documents":[
              {"id":"2","place_name":"카페B","category_name":"음식점 > 카페","category_group_code":"CE7",
               "address_name":"서울 종로구 2","road_address_name":"",
               "x":"126.9870","y":"37.5780","distance":"","place_url":"http://place.map.kakao.com/2"}]}
            """;

    @Test
    void categorySearchFollowsPagesAndMapsFields() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v2/local/search/category.json")))
                .andExpect(queryParam("category_group_code", "CE7"))
                .andExpect(queryParam("page", "1"))
                .andExpect(queryParam("radius", "1300"))
                .andExpect(header("Authorization", "KakaoAK test"))
                .andRespond(withSuccess(PAGE1, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v2/local/search/category.json")))
                .andExpect(queryParam("page", "2"))
                .andRespond(withSuccess(PAGE2, MediaType.APPLICATION_JSON));

        List<KakaoPlace> places = new KakaoLocalClient(builder.build())
                .searchCategory("CE7", 37.5765, 126.9855, 1300);

        server.verify();
        assertThat(places).hasSize(2);
        assertThat(places.get(0).id()).isEqualTo("1");
        assertThat(places.get(0).name()).isEqualTo("카페A");
        assertThat(places.get(0).lat()).isEqualTo(37.5770);
        assertThat(places.get(0).lng()).isEqualTo(126.9860);
        assertThat(places.get(0).distanceM()).isEqualTo(120);
        assertThat(places.get(1).distanceM()).isZero();
    }
}
