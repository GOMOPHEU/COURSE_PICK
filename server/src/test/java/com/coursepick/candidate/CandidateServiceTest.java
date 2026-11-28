package com.coursepick.candidate;

import com.coursepick.config.CoursepickSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CandidateServiceTest {
    CandidatePolicy policy;
    KakaoCandidateClient client;
    CandidateService service;
    @BeforeEach void setup() throws Exception {
        policy = new CandidatePolicy(new CoursepickSettings(new ObjectMapper().readTree(Path.of("config/coursepick.json").toFile())));
        client = mock(KakaoCandidateClient.class);
        service = new CandidateService(client, policy);
    }
    CandidateRequest request(String theme, String companion) {
        return new CandidateRequest(37.5765, 126.9855, theme, companion, null, false, Set.of(), Set.of());
    }
    KakaoPlace place(String id, String name, String group, String category) {
        return new KakaoPlace(id, name, category, group, 37.5765, 126.9855, "", "");
    }
    @Test void themesRequireCorrectGroupsAndCategorySegments() {
        assertThat(policy.themeMatches("culture", place("9290049", "예전사", "CT1", "문화시설"))).isFalse();
        assertThat(policy.themeMatches("culture", place("1490573702", "서울문화홍보원", "CT1", "문화시설"))).isFalse();
        assertThat(policy.themeMatches("food", place("1", "도시락", "FD6", "음식점 > 도시락"))).isFalse();
        assertThat(policy.themeMatches("food", place("1", "간식", "FD6", "음식점 > 간식 > 떡"))).isFalse();
        assertThat(policy.themeMatches("food", place("1", "식당", "FD6", "음식점 > 한식"))).isTrue();
        assertThat(policy.themeMatches("cafe", place("1", "카페", "CE7", "음식점 > 카페 > 커피전문점"))).isTrue();
        assertThat(policy.themeMatches("cafe", place("1", "카페", "CE7", "음식점 > 카페용품"))).isFalse();
        assertThat(policy.themeMatches("walk", place("1", "길", "AT4", "여행 > 관광명소 > 도보여행"))).isFalse();
        assertThat(policy.themeMatches("history", place("1", "궁", "AT4", "여행 > 관광명소 > 고궁"))).isTrue();
        assertThat(policy.themeMatches("exhibition", place("1", "전시", "CT1", "문화시설 > 미술관"))).isTrue();
    }
    @Test void shoppingUsesAllowlistInsteadOfName() {
        for (String category : List.of("인테리어장식판매", "디자인문구", "패션잡화점", "시장", "복합쇼핑몰"))
            assertThat(policy.themeMatches("shopping", place("1", "가게", "", "가정,생활 > " + category))).isTrue();
        for (String category : List.of("문구", "액세서리", "편의점", "화장품", "보석,귀금속", "통신판매 > 인터넷쇼핑몰", "시장상담"))
            assertThat(policy.themeMatches("shopping", place("1", "소품샵 쇼핑몰", "", "가정,생활 > " + category))).isFalse();
        assertThat(policy.themeMatches("shopping", place("1", "시장식당", "FD6", "음식점 > 한식"))).isFalse();
        assertThat(policy.themeMatches("shopping", place("1", "거리", "AT4", "여행 > 관광명소 > 테마거리"))).isTrue();
    }
    @Test void familyTeensAndChildrenExcludeBarsButAdultsDoNot() {
        for (String name : List.of("이자카야", "호프", "포차", "앤바", "사카바", "주점")) {
            var bar = place("1", "테스트" + name, "FD6", "음식점 > 일식");
            assertThat(policy.companionAllows(request("food", "family"), bar)).isFalse();
            assertThat(policy.companionAllows(request("food", "friends"), bar)).isTrue();
            assertThat(policy.companionAllows(new CandidateRequest(37.5765,126.9855,"food","friends","10s",false,null,null), bar)).isFalse();
            assertThat(policy.companionAllows(new CandidateRequest(37.5765,126.9855,"food","friends",null,true,null,null), bar)).isFalse();
        }
        assertThat(policy.companionAllows(request("food", "family"), place("1", "가게", "FD6", "음식점 > 술집 > 와인바"))).isFalse();
    }
    @Test void expandsAfterFilteringAndKeepsEarlierCandidates() {
        var cafe = place("1", "카페1", "CE7", "음식점 > 카페");
        when(client.search(any(), anyDouble(), anyDouble(), eq(690), eq(1))).thenReturn(new KakaoCandidateClient.Page(List.of(cafe), true));
        when(client.search(any(), anyDouble(), anyDouble(), eq(865), eq(1))).thenReturn(new KakaoCandidateClient.Page(List.of(
                place("2", "카페2", "CE7", "음식점 > 카페"), place("3", "카페3", "CE7", "음식점 > 카페")), true));
        var result = service.find(request("cafe", "solo"));
        assertThat(result.candidates()).hasSize(3);
        assertThat(result.radiusMeters()).isEqualTo(865);
        assertThat(result.expanded()).isTrue();
        assertThat(result.insufficient()).isFalse();
        verify(client, times(2)).search(any(), anyDouble(), anyDouble(), anyInt(), anyInt());
    }
    @Test void sufficientInitialCandidatesDoNotExpand() {
        when(client.search(any(), anyDouble(), anyDouble(), anyInt(), anyInt())).thenReturn(new KakaoCandidateClient.Page(List.of(
                place("1", "1", "CE7", "음식점 > 카페"), place("2", "2", "CE7", "음식점 > 카페"), place("3", "3", "CE7", "음식점 > 카페")), true));
        assertThat(service.find(request("cafe", "solo")).expanded()).isFalse();
        verify(client, times(1)).search(any(), anyDouble(), anyDouble(), eq(690), eq(1));
    }
    @Test void pagesAndRadiusAreBoundedAndDuplicatesDoNotCount() {
        when(client.search(any(), anyDouble(), anyDouble(), anyInt(), anyInt())).thenReturn(new KakaoCandidateClient.Page(List.of(place("1", "카페", "CE7", "음식점 > 카페")), false));
        var result = service.find(request("cafe", "solo"));
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.insufficient()).isTrue();
        verify(client, times(4)).search(any(), anyDouble(), anyDouble(), anyInt(), anyInt());
        verify(client, never()).search(any(), anyDouble(), anyDouble(), anyInt(), eq(3));
    }
    @Test void palaceChildrenCollapseAndChosenGroupIsExcluded() {
        var parent = new KakaoPlace("1", "경복궁", "여행 > 관광명소 > 고궁", "AT4", 37.5796, 126.9770, "", "");
        var child = new KakaoPlace("2", "근정전", "여행 > 관광명소 > 문화유적", "AT4", 37.5796, 126.9770, "", "");
        var museum = new KakaoPlace("3", "경복궁 박물관", "문화시설 > 박물관", "CT1", 37.5796, 126.9770, "", "");
        assertThat(policy.groupKey(museum)).isEqualTo("kakao:3");
        var candidates = service.filter(request("history", "family"), List.of(child, parent), 865);
        assertThat(candidates).extracting(CandidateService.Candidate::kakaoId).containsExactly("1");
        var excluded = new CandidateRequest(37.5765,126.9855,"history","family",null,false,Set.of(),Set.of("palace:gyeongbokgung"));
        assertThat(service.filter(excluded, List.of(child, parent), 865)).isEmpty();
        assertThat(policy.groupKey(place("4", "경복궁 맛집", "FD6", "음식점 > 한식"))).isEqualTo("kakao:4");
    }
    @Test void filtersRegionRadiusAndPreviouslyChosenIds() {
        var local = place("1", "카페", "CE7", "음식점 > 카페");
        var remote = new KakaoPlace("2", "먼 카페", "음식점 > 카페", "CE7", 37.60, 126.9855, "", "");
        assertThat(service.filter(request("cafe", "solo"), List.of(local, remote), 865)).hasSize(1);
        var nearRemote = new CandidateRequest(37.60,126.9855,"cafe","solo",null,false,null,null);
        assertThat(service.filter(nearRemote, List.of(remote), 865)).isEmpty();
        var excluded = new CandidateRequest(37.5765,126.9855,"cafe","solo",null,false,Set.of("1"),null);
        assertThat(service.filter(excluded, List.of(local), 865)).isEmpty();
    }
    @Test void providerFailurePropagatesInsteadOfReturningEmptyResult() {
        when(client.search(any(), anyDouble(), anyDouble(), anyInt(), anyInt())).thenThrow(new IllegalStateException("failure"));
        assertThatThrownBy(() -> service.find(request("cafe", "solo"))).isInstanceOf(IllegalStateException.class);
    }
}
