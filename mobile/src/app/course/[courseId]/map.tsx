import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Linking, Pressable, StyleSheet, Text, View } from 'react-native';

import { startPoints, transportLabels } from '@/course/contracts';
import CourseMap from '@/course/course-map';
import { courseTitle } from '@/course/course-summary';
import { useCourseStore } from '@/course/store';
import { Button, Card, colors, Copy, ErrorText, Heading, Screen } from '@/course/ui';

export default function MapScreen() {
  const { courseId } = useLocalSearchParams<{ courseId: string }>();
  const courses = useCourseStore(state => state.result?.courses);
  const index = courses?.findIndex(value => value.courseId === courseId) ?? -1;
  const course = courses?.[index];
  const origin = useCourseStore(state => startPoints.find(point => point.id === state.request.startPointId));
  const [failedLeg, setFailedLeg] = useState<number | null>(null);
  if (!course || !origin) return <Screen title="코스를 찾을 수 없어요"><Button title="설문으로" onPress={() => router.dismissTo('/survey')} /></Screen>;

  return (
    <Screen title={courseTitle(course, index)} subtitle={origin.name + ' 출발'}>
      <CourseMap origin={origin} course={course} />
      <Copy>지도 선은 방문 순서를 표시해요. 실제 경로는 구간별 길찾기에서 확인할 수 있어요.</Copy>
      {course.legs.map(leg => {
        const from = leg.from === 'start' ? origin : course.places.find(place => place.order === leg.from);
        const to = course.places.find(place => place.order === leg.to);
        if (!from || !to) return null;
        const query = `sp=${from.lat},${from.lng}&ep=${to.lat},${to.lng}&by=${leg.mode === 'walk' ? 'foot' : 'publictransit'}`;
        return <Card key={leg.to}>
          <View style={styles.row}>
            <View style={styles.leg}>
              <Heading>{from.name} → {to.name}</Heading>
              <Copy>{transportLabels[leg.mode]} {leg.minutes}분</Copy>
            </View>
            <Pressable style={styles.route} accessibilityRole="button" accessibilityLabel={from.name + '에서 ' + to.name + '까지 카카오맵 길찾기'}
              onPress={async () => {
                setFailedLeg(null);
                try {
                  await Linking.openURL('kakaomap://route?' + query);
                } catch {
                  try { await Linking.openURL('https://m.map.kakao.com/scheme/route?' + query); }
                  catch { setFailedLeg(leg.to); }
                }
              }}>
              <Text style={styles.routeText}>길찾기 ›</Text>
            </Pressable>
          </View>
          {failedLeg === leg.to && <ErrorText message="카카오맵을 열지 못했어요. 다시 시도해 주세요." />}
        </Card>;
      })}
      <Button title="코스 상세로 돌아가기" arrow onPress={() => router.dismissTo({
        pathname: '/course/[courseId]', params: { courseId },
      })} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  leg: { flex: 1 },
  route: { minHeight: 44, justifyContent: 'center', paddingHorizontal: 4 },
  routeText: { color: colors.primary, fontWeight: '700' },
});
