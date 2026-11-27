import { router, useLocalSearchParams } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { startPoints, themes, transportLabels } from '@/course/contracts';
import { courseTitle } from '@/course/course-summary';
import { durationLabel, minutes, timeAt, visitDateLabel } from '@/course/schedule';
import { useCourseStore } from '@/course/store';
import { Button, Card, colors, Copy, FitBadge, Heading, Screen } from '@/course/ui';

const openStatus = { confirmed: '영업 확인', official: '공식 안내 기준', unknown: '운영정보 미확인' };

export default function CourseDetail() {
  const { courseId } = useLocalSearchParams<{ courseId: string }>();
  const courses = useCourseStore(state => state.result?.courses);
  const index = courses?.findIndex(value => value.courseId === courseId) ?? -1;
  const course = courses?.[index];
  const request = useCourseStore(state => state.request);
  const origin = startPoints.find(point => point.id === request.startPointId);
  const available = minutes(request.endTime) - minutes(request.startTime);
  if (!course) return <Screen title="코스를 찾을 수 없어요"><Button title="설문으로" onPress={() => router.dismissTo('/survey')} /></Screen>;

  return (
    <Screen title={courseTitle(course, index)}
      subtitle={[visitDateLabel(request.date), request.startTime + ' 시작', transportLabels[request.transport]].join(' · ')}>
      <Card style={styles.summary}>
        <View style={styles.row}>
          <View style={styles.place}><Heading>예상 총 {durationLabel(course.totalMinutes)}</Heading></View>
          <FitBadge fit={course.fit} />
        </View>
        <Copy>이용 {durationLabel(available)} · 남은 시간 {durationLabel(Math.max(0, available - course.totalMinutes))}</Copy>
      </Card>

      <View style={styles.timeline}>
        <Heading>{origin?.name} 출발 · {request.startTime}</Heading>
        {course.places.map(place => {
          const leg = course.legs.find(item => item.to === place.order);
          return (
            <View key={place.order} style={styles.step}>
              {leg && <Text style={styles.leg}>{transportLabels[leg.mode]} {leg.minutes}분 이동</Text>}
              <Pressable accessibilityRole="button" accessibilityHint="장소 상세 보기"
                style={({ pressed }) => [styles.row, styles.stop, pressed && styles.pressed]}
                onPress={() => router.push({
                  pathname: '/course/[courseId]/place/[order]', params: { courseId, order: String(place.order) },
                })}>
                <View style={styles.number}><Text style={styles.numberText}>{place.order}</Text></View>
                <View style={styles.place}>
                  <Heading>{place.name}</Heading>
                  <Copy>{place.arriveAt}~{timeAt(minutes(place.arriveAt) + place.stayMinutes)} · 체류 {place.stayMinutes}분</Copy>
                  <Copy>{themes[place.theme]} · {openStatus[place.openStatus]}</Copy>
                </View>
                <Text accessible={false} style={styles.chevron}>›</Text>
              </Pressable>
            </View>
          );
        })}
        <Heading>{course.endAt} 예상 종료</Heading>
      </View>

      <View style={styles.place}>
        <Copy>{course.reason}</Copy>
        {!!course.notes?.length && <>
          <Heading>빠진 테마 안내</Heading>
          {course.notes.map((note, index) => <Copy key={index}>{note}</Copy>)}
        </>}
        <Copy>총 소요시간에 첫 장소까지의 이동을 포함해요. 귀가 이동은 별도예요.</Copy>
      </View>
      <Button title="지도와 길찾기" arrow onPress={() => router.push({
        pathname: '/course/[courseId]/map', params: { courseId: course.courseId },
      })} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  summary: { backgroundColor: colors.pale },
  row: { flexDirection: 'row', gap: 10, alignItems: 'center', justifyContent: 'space-between' },
  timeline: { paddingLeft: 12, marginLeft: 4, borderLeftWidth: 2, borderColor: colors.line, gap: 10 },
  step: { gap: 4 },
  leg: { fontSize: 12, color: colors.muted, paddingLeft: 4 },
  stop: { borderRadius: 10, backgroundColor: colors.surface, padding: 9 },
  place: { flex: 1, gap: 3 },
  number: { width: 26, height: 26, borderRadius: 13, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.primary },
  numberText: { color: colors.surface, fontWeight: '700' },
  chevron: { color: colors.primary, fontSize: 22 },
  pressed: { opacity: 0.7 },
});
