import { router } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { isMockMode } from '@/course/api';
import { ConditionSummary, courseTitle } from '@/course/course-summary';
import { durationLabel } from '@/course/schedule';
import { useCourseStore } from '@/course/store';
import { Button, Card, colors, Copy, FitBadge, Heading, Screen } from '@/course/ui';

export default function Courses() {
  const result = useCourseStore(state => state.result);
  const request = useCourseStore(state => state.request);
  const edit = () => router.dismissTo('/survey');
  if (!result?.courses.length) {
    return (
      <Screen title={result ? '조금 다른 조건으로\n다시 찾아볼까요?' : '추천 결과가 없어요'} centered
        subtitle={result ? '시간을 늘리거나 테마를 바꾸면 다른 코스를 찾을 수 있어요.' : '설문을 작성하면 코스를 볼 수 있어요.'}
        style={styles.empty}>
        <ConditionSummary request={request} />
        <Button title="조건 수정하기" arrow onPress={edit} />
      </Screen>
    );
  }

  return (
    <Screen title="취향에 맞는 오늘의 코스">
      <ConditionSummary request={request} onEdit={edit} />
      <Heading>조건에 맞는 코스 {result.courses.length}개</Heading>
      {result.courses.map((course, index) => (
        <Pressable key={course.courseId} accessibilityRole="button" accessibilityHint="코스 상세 보기"
          style={({ pressed }) => pressed && styles.pressed} onPress={() => router.push({
            pathname: '/course/[courseId]', params: { courseId: course.courseId },
          })}>
          <Card>
            <View style={styles.row}>
              <View style={styles.content}>
                <Heading>{courseTitle(course, index)}</Heading>
                <Copy>{course.places.map(place => place.name).join(' → ')}</Copy>
                <Copy>{course.places.length}곳 · 예상 {durationLabel(course.totalMinutes)}</Copy>
              </View>
              <Text style={styles.chevron} accessible={false}>›</Text>
            </View>
            <FitBadge fit={course.fit} />
            {!isMockMode && course.source === 'basic' && <Copy>규칙 기반 추천</Copy>}
            <Copy>{course.reason}</Copy>
          </Card>
        </Pressable>
      ))}
      <Copy>{isMockMode ? '시연용 데이터 · 이동시간은 추정값입니다.' : '이동시간은 추정값입니다.'}</Copy>
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  content: { flex: 1, gap: 4 },
  chevron: { fontSize: 24, color: colors.primary },
  pressed: { opacity: 0.8 },
  empty: { justifyContent: 'center' },
});
