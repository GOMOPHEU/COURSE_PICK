package com.coursepick.gemini;

import com.coursepick.assemble.CourseAssembler.Input;
import com.coursepick.config.CoursepickSettings;
import com.coursepick.dto.RecommendResponse.Course;
import com.coursepick.dto.RecommendResponse.Place;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class GeminiRanker {

    public record Choice(int index, String reason) {
    }

    public record Result(List<Choice> choices, List<String> notes) {
    }

    private static final int MAX_REASON_LENGTH = 120;

    private static final String SCHEMA = """
            {"type":"OBJECT","properties":{"picks":{"type":"ARRAY","items":{"type":"OBJECT","properties":{
            "courseId":{"type":"STRING"},"reason":{"type":"STRING"}},"required":["courseId","reason"]}}},
            "required":["picks"]}
            """;

    private final ObjectProvider<GeminiClient> clientProvider;
    private final CoursepickSettings settings;
    private final ObjectMapper json = new ObjectMapper();

    public GeminiRanker(ObjectProvider<GeminiClient> clientProvider, CoursepickSettings settings) {
        this.clientProvider = clientProvider;
        this.settings = settings;
    }

    public boolean available() {
        return clientProvider.getIfAvailable() != null
                && settings.root().path("gemini").path("enabled").asBoolean(false);
    }

    public int candidateCourses() {
        return settings.root().path("gemini").path("candidateCourses").asInt(5);
    }

    public Result rank(Input in, List<Course> courses, int count) {
        List<Choice> choices = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        GeminiClient client = clientProvider.getIfAvailable();
        if (client == null) {
            return new Result(choices, notes);
        }
        JsonNode response;
        try {
            response = json.readTree(client.generate(prompt(in, courses, count), json.readTree(SCHEMA)));
        } catch (Exception e) {
            notes.add("제미나이 호출 실패: " + e.getMessage());
            return new Result(choices, notes);
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode pick : response.path("picks")) {
            if (choices.size() >= count) {
                break;
            }
            String id = pick.path("courseId").asText("");
            int index = indexOf(courses, id);
            if (index < 0 || !seen.add(id)) {
                notes.add("제미나이가 없는 코스(" + id + ")를 골랐어요");
                continue;
            }
            String reason = pick.path("reason").asText("").strip();
            if (reason.length() > MAX_REASON_LENGTH) {
                reason = reason.substring(0, MAX_REASON_LENGTH);
            }
            if (claimsMissingTheme(reason, courses.get(index))) {
                notes.add("코스 " + id + "의 추천 이유가 빠진 테마를 언급해 기본 문장으로 바꿨어요");
                reason = "";
            }
            choices.add(new Choice(index, reason));
        }
        if (choices.isEmpty()) {
            notes.add("제미나이가 코스를 고르지 못해 기본 순서를 썼어요");
        } else {
            notes.add("제미나이가 코스 " + courses.size() + "개 중 " + choices.size() + "개를 골랐어요");
        }
        return new Result(choices, notes);
    }

    private boolean claimsMissingTheme(String reason, Course course) {
        String text = reason;
        for (Place p : course.places()) {
            text = text.replace(p.name(), "");
        }
        for (String note : course.notes()) {
            int colon = note.indexOf(':');
            if (colon > 0 && text.contains(note.substring(0, colon).strip())) {
                return true;
            }
        }
        return false;
    }

    private int indexOf(List<Course> courses, String id) {
        for (int i = 0; i < courses.size(); i++) {
            if (courses.get(i).courseId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private String prompt(Input in, List<Course> courses, int count) throws Exception {
        ObjectNode data = json.createObjectNode();
        data.put("출발시각", clock(in.startMin()));
        data.put("종료시각", clock(in.endMin()));
        data.put("동행", in.companion() == null ? "" : in.companion());
        data.put("연령대", in.ageGroup() == null ? "" : in.ageGroup());
        data.set("선택테마", json.valueToTree(in.themes()));
        ArrayNode list = data.putArray("코스");
        for (Course course : courses) {
            ObjectNode node = list.addObject();
            node.put("courseId", course.courseId());
            node.put("총소요분", course.totalMinutes());
            node.put("종료", course.endAt());
            ArrayNode places = node.putArray("장소");
            for (Place p : course.places()) {
                ObjectNode place = places.addObject();
                place.put("이름", p.name());
                place.put("테마", p.theme());
                place.put("도착", p.arriveAt());
                place.put("체류분", p.stayMinutes());
            }
            ArrayNode missing = node.putArray("빠진선택테마");
            for (String note : course.notes()) {
                missing.add(note);
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("서울 도심 당일 도보 여행 코스를 추천하는 도우미야. 아래 코스들은 이미 서버가 규칙을 검증한 완성본이다. ")
                .append("코스를 고치지 말고, 사용자 조건에 가장 잘 맞는 코스를 ").append(count).append("개 골라 좋은 순서대로 나열해줘.\n");
        text.append("고르는 기준: 선택테마가 잘 반영됐는지, 동행과 연령대에 어울리는지, 장소 흐름이 자연스러운지, ")
                .append("시간을 알차게 쓰는지, 고른 코스끼리 구성이 다양한지.\n");
        text.append("각 코스의 reason에는 사용자에게 보여줄 추천 이유를 한국어 한 문장으로 쓴다. 장소 이름을 포함하고, ")
                .append("데이터에 없는 사실(분위기, 가격, 메뉴 등)은 지어내지 않는다. ")
                .append("코스의 빠진선택테마에 적힌 테마는 그 코스에 없으니 reason에서 포함한다고 쓰지 않는다. ")
                .append("장소의 테마는 데이터의 테마 값만 따르고 이름에서 추측하지 않는다.\n\n데이터:\n");
        text.append(json.writeValueAsString(data));
        return text.toString();
    }

    private String clock(int minutes) {
        return String.format("%02d:%02d", (minutes / 60) % 24, minutes % 60);
    }
}
