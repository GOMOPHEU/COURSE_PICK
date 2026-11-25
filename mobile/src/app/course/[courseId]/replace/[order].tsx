import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { isMockMode } from '@/course/api';
import { mockReplacementCourses } from '@/course/mock';
import { durationLabel } from '@/course/schedule';
import { useCourseStore } from '@/course/store';
import { Button, Card, colors, Copy, FitBadge, Heading, Screen } from '@/course/ui';

export default function ReplacePlace() {
  const { courseId, order } = useLocalSearchParams<{ courseId: string; order: string }>();
  const course = useCourseStore(state => state.result?.courses.find(item => item.courseId === courseId));
  const request = useCourseStore(state => state.request);
  const [selectedId, setSelectedId] = useState<string>();
  const original = course?.places.find(place => place.order === Number(order));
  const back = () => router.dismissTo({ pathname: '/course/[courseId]', params: { courseId } });

  if (!course || !original) return (
    <Screen title="장소를 찾을 수 없어요"><Button title="코스 목록" onPress={() => router.dismissTo('/courses')} /></Screen>
  );

  const options = isMockMode ? mockReplacementCourses(request, course, original.order) : [];
  const selected = options.find(option => option.place.kakaoId === selectedId);

  function apply() {
    const { result, setResult } = useCourseStore.getState();
    if (!selected || !course || !result || !result.courses.includes(course)) return;
    setResult({ ...result, courses: result.courses.map(item => item === course ? selected.course : item) });
    back();
  }

  return (
    <Screen title="한 곳만 바꿔볼까요?">
      <Card style={styles.summary}>
        <Copy>변경할 곳</Copy>
        <Heading>{original.order}번 {original.name}</Heading>
        <Copy>현재 체류 {original.stayMinutes}분 · 나머지 장소 유지</Copy>
      </Card>
      {options.length > 0 ? <>
        <Heading>대체 장소를 하나 선택하세요</Heading>
        <Copy>시연용 후보예요. 이동 시간은 추정값이며 영업시간은 확인되지 않았어요.</Copy>
        <View accessibilityRole="radiogroup" style={styles.options}>
          {options.map(({ place, course: updated }) => {
            const active = place.kakaoId === selectedId;
            return (
              <Pressable key={place.kakaoId} accessibilityRole="radio" accessibilityState={{ checked: active }}
                style={[styles.option, active && styles.active]} onPress={() => setSelectedId(place.kakaoId)}>
                <View style={styles.optionText}>
                  <Heading>{place.name}</Heading>
                  <Copy>체류 {place.stayMinutes}분 · 변경 후 총 {durationLabel(updated.totalMinutes)}</Copy>
                </View>
                <Text accessible={false} style={styles.indicator}>{active ? '●' : '○'}</Text>
              </Pressable>
            );
          })}
        </View>
      </> : <Card><Copy>{isMockMode ? '남은 시간에 맞는 교체 후보가 없어요.' : '장소 변경은 시연 모드에서 할 수 있어요.'}</Copy></Card>}
      {selected && <Card style={styles.preview}>
        <Copy>변경 후 예상</Copy>
        <Heading>{durationLabel(course.totalMinutes)} → {durationLabel(selected.course.totalMinutes)}</Heading>
        <FitBadge fit={selected.course.fit} />
        <Copy>{selected.course.places.map(place => place.name).join(' → ')}</Copy>
      </Card>}
      <Button title="기존 코스로 돌아가기" secondary onPress={back} />
      {selected && <Button title="선택한 장소로 적용" arrow onPress={apply} />}
    </Screen>
  );
}

const styles = StyleSheet.create({
  summary: { backgroundColor: '#eaf1eb' },
  preview: { backgroundColor: colors.pale },
  options: { gap: 8 },
  option: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12,
    backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, borderRadius: 13, padding: 14 },
  active: { backgroundColor: colors.pale, borderColor: colors.primary },
  optionText: { flex: 1, gap: 4 },
  indicator: { fontSize: 18, color: colors.primary },
});
